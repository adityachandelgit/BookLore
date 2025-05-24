package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.CbxProgress;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.request.ToggleAllLockRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.Lock;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.adityachandel.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;
import static com.adityachandel.booklore.model.enums.MetadataProvider.*;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final BookMapper bookMapper;
    private final BookMetadataMapper bookMetadataMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final BookMetadataRepository bookMetadataRepository;
    private final FileService fileService;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final CbxProcessor cbxProcessor;
    private final Map<MetadataProvider, BookParser> parserMap;

    public List<BookMetadata> getProspectiveMetadataListForBookId(long bookId, FetchMetadataRequest request) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        Book book = bookMapper.toBook(bookEntity);
        List<List<BookMetadata>> allMetadata = request.getProviders().stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> fetchMetadataListFromAProvider(provider, book, request))
                        .exceptionally(e -> {
                            log.error("Error fetching metadata from provider: {}", provider, e);
                            return List.of();
                        }))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        List<BookMetadata> interleavedMetadata = new ArrayList<>();
        int maxSize = allMetadata.stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxSize; i++) {
            for (List<BookMetadata> metadataList : allMetadata) {
                if (i < metadataList.size()) {
                    interleavedMetadata.add(metadataList.get(i));
                }
            }
        }

        return interleavedMetadata;
    }

    public List<BookMetadata> fetchMetadataListFromAProvider(MetadataProvider provider, Book book, FetchMetadataRequest request) {
        return getParser(provider).fetchMetadata(book, request);
    }

    public BookMetadata fetchTopMetadataFromAProvider(MetadataProvider provider, Book book) {
        return getParser(provider).fetchTopMetadata(book, buildFetchMetadataRequestFromBook(book));
    }

    @Transactional
    public void refreshMetadata(MetadataRefreshRequest request) {
        log.info("Refresh Metadata task started!");

        if (request.getQuick() != null && request.getQuick()) {
            AppSettings appSettings = appSettingService.getAppSettings();
            request.setRefreshOptions(appSettings.getMetadataRefreshOptions());
        }

        List<MetadataProvider> providers = prepareProviders(request);
        List<BookEntity> books = getBookEntities(request);
        for (BookEntity bookEntity : books) {
            try {
                if (bookEntity.getMetadata().areAllFieldsLocked()) {
                    log.info("Skipping metadata refresh for fully locked book: {}", bookEntity.getFileName());
                    continue;
                }
                Map<MetadataProvider, BookMetadata> metadataMap = fetchMetadataForBook(providers, bookEntity);
                if (providers.contains(GoodReads)) {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
                }
                BookMetadata fetchedBookMetadata = buildFetchMetadata(bookEntity.getId(), request, metadataMap);
                updateBookMetadata(bookEntity, fetchedBookMetadata, request.getRefreshOptions().isRefreshCovers(), request.getRefreshOptions().isMergeCategories());
            } catch (Exception e) {
                log.error("Error while updating book metadata, book: {}", bookEntity.getFileName(), e);
            }
        }
        log.info("Refresh Metadata task completed!");
    }

    @Transactional
    protected BookMetadataEntity updateBookMetadata(BookEntity bookEntity, BookMetadata metadata, boolean replaceCover, boolean mergeCategories) {
        if (metadata != null) {
            BookMetadataEntity bookMetadata = bookMetadataUpdater.setBookMetadata(bookEntity.getId(), metadata, replaceCover, mergeCategories);
            bookEntity.setMetadata(bookMetadata);
            bookRepository.save(bookEntity);
            Book book = bookMapper.toBook(bookEntity);
            notificationService.sendMessage(Topic.BOOK_METADATA_UPDATE, book);
            notificationService.sendMessage(Topic.LOG, createLogNotification("Book metadata updated: " + book.getMetadata().getTitle()));
            return bookMetadata;
        }
        return bookEntity.getMetadata();
    }

    @Transactional
    protected List<MetadataProvider> prepareProviders(MetadataRefreshRequest request) {
        Set<MetadataProvider> allProviders = new HashSet<>(getAllProvidersUsingIndividualFields(request));
        return new ArrayList<>(allProviders);
    }

    @Transactional
    protected Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, BookEntity bookEntity) {
        return providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> fetchTopMetadataFromAProvider(provider, bookMapper.toBook(bookEntity)))
                        .exceptionally(e -> {
                            log.error("Error fetching metadata from provider: {}", provider, e);
                            return null;
                        }))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    @Transactional
    protected BookMetadata buildFetchMetadata(Long bookId, MetadataRefreshRequest request, Map<MetadataProvider, BookMetadata> metadataMap) {
        BookMetadata metadata = BookMetadata.builder().bookId(bookId).build();
        MetadataRefreshOptions.FieldOptions fieldOptions = request.getRefreshOptions().getFieldOptions();

        metadata.setTitle(resolveFieldAsString(metadataMap, fieldOptions.getTitle(), BookMetadata::getTitle));
        metadata.setDescription(resolveFieldAsString(metadataMap, fieldOptions.getDescription(), BookMetadata::getDescription));
        metadata.setAuthors(resolveFieldAsList(metadataMap, fieldOptions.getAuthors(), BookMetadata::getAuthors));
        if (request.getRefreshOptions().isMergeCategories()) {
            metadata.setCategories(getAllCategories(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
        } else {
            metadata.setCategories(resolveFieldAsList(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
        }
        metadata.setThumbnailUrl(resolveFieldAsString(metadataMap, fieldOptions.getCover(), BookMetadata::getThumbnailUrl));

        if (request.getRefreshOptions().getAllP3() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP3());
        }
        if (request.getRefreshOptions().getAllP2() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP2());
        }
        if (request.getRefreshOptions().getAllP1() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP1());
        }

        return metadata;
    }

    @Transactional
    protected void setOtherUnspecifiedMetadata(Map<MetadataProvider, BookMetadata> metadataMap, BookMetadata metadataCombined, MetadataProvider provider) {
        if (metadataMap.containsKey(provider)) {
            BookMetadata metadata = metadataMap.get(provider);
            metadataCombined.setSubtitle(metadata.getSubtitle() != null ? metadata.getSubtitle() : metadata.getTitle());
            metadataCombined.setPublisher(metadata.getPublisher() != null ? metadata.getPublisher() : metadataCombined.getPublisher());
            metadataCombined.setPublishedDate(metadata.getPublishedDate() != null ? metadata.getPublishedDate() : metadataCombined.getPublishedDate());
            metadataCombined.setIsbn10(metadata.getIsbn10() != null ? metadata.getIsbn10() : metadataCombined.getIsbn10());
            metadataCombined.setIsbn13(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadataCombined.getIsbn13());
            metadataCombined.setAsin(metadata.getAsin() != null ? metadata.getAsin() : metadataCombined.getAsin());
            metadataCombined.setPageCount(metadata.getPageCount() != null ? metadata.getPageCount() : metadataCombined.getPageCount());
            metadataCombined.setLanguage(metadata.getLanguage() != null ? metadata.getLanguage() : metadataCombined.getLanguage());
            metadataCombined.setRating(metadata.getRating() != null ? metadata.getRating() : metadataCombined.getRating());
            metadataCombined.setRatingCount(metadata.getRatingCount() != null ? metadata.getRatingCount() : metadataCombined.getRatingCount());
            metadataCombined.setReviewCount(metadata.getReviewCount() != null ? metadata.getReviewCount() : metadataCombined.getReviewCount());
            metadataCombined.setAwards(metadata.getAwards() != null ? metadata.getAwards() : metadataCombined.getAwards());
            metadataCombined.setSeriesName(metadata.getSeriesName() != null ? metadata.getSeriesName() : metadataCombined.getSeriesName());
            metadataCombined.setSeriesNumber(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber() : metadataCombined.getSeriesNumber());
            metadataCombined.setSeriesTotal(metadata.getSeriesTotal() != null ? metadata.getSeriesTotal() : metadataCombined.getSeriesTotal());
        }
    }

    @Transactional
    protected String resolveFieldAsString(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractor fieldValueExtractor) {
        String value = null;
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValue != null) {
                value = newValue;
            }
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValue != null) {
                value = newValue;
            }
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValue != null) {
                value = newValue;
            }
        }
        return value;
    }

    List<String> getAllCategories(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueCategories = new HashSet<>();
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            List<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            List<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            List<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        return new ArrayList<>(uniqueCategories);
    }

    @Transactional
    protected List<String> resolveFieldAsList(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        List<String> values = new ArrayList<>();
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            List<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        if (values.isEmpty() && fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            List<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        if (values.isEmpty() && fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            List<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        return values;
    }

    @Transactional
    protected List<BookEntity> getBookEntities(MetadataRefreshRequest request) {
        MetadataRefreshRequest.RefreshType refreshType = request.getRefreshType();
        if (refreshType != MetadataRefreshRequest.RefreshType.LIBRARY && refreshType != MetadataRefreshRequest.RefreshType.BOOKS) {
            throw ApiError.INVALID_REFRESH_TYPE.createException();
        }
        List<BookEntity> books = switch (refreshType) {
            case LIBRARY -> {
                LibraryEntity libraryEntity = libraryRepository.findById(request.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(request.getLibraryId()));
                yield libraryEntity.getBookEntities();
            }
            case BOOKS -> bookRepository.findAllByIdIn(request.getBookIds());
        };
        books.sort(Comparator.comparing(BookEntity::getFileName, Comparator.nullsLast(String::compareTo)));
        return books;
    }

    @Transactional
    protected Set<MetadataProvider> getAllProvidersUsingIndividualFields(MetadataRefreshRequest request) {
        MetadataRefreshOptions.FieldOptions fieldOptions = request.getRefreshOptions().getFieldOptions();
        Set<MetadataProvider> uniqueProviders = new HashSet<>();

        if (fieldOptions != null) {
            addProviderToSet(fieldOptions.getTitle(), uniqueProviders);
            addProviderToSet(fieldOptions.getDescription(), uniqueProviders);
            addProviderToSet(fieldOptions.getAuthors(), uniqueProviders);
            addProviderToSet(fieldOptions.getCategories(), uniqueProviders);
            addProviderToSet(fieldOptions.getCover(), uniqueProviders);
        }

        return uniqueProviders;
    }

    @Transactional
    protected void addProviderToSet(MetadataRefreshOptions.FieldProvider fieldProvider, Set<MetadataProvider> providerSet) {
        if (fieldProvider != null) {
            if (fieldProvider.getP3() != null) {
                providerSet.add(fieldProvider.getP3());
            }
            if (fieldProvider.getP2() != null) {
                providerSet.add(fieldProvider.getP2());
            }
            if (fieldProvider.getP1() != null) {
                providerSet.add(fieldProvider.getP1());
            }
        }
    }

    private FetchMetadataRequest buildFetchMetadataRequestFromBook(Book book) {
        return FetchMetadataRequest.builder()
                .isbn(book.getMetadata().getIsbn10())
                .asin(book.getMetadata().getAsin())
                .author(String.join(", ", book.getMetadata().getAuthors()))
                .title(book.getMetadata().getTitle())
                .bookId(book.getId())
                .build();
    }

    private BookParser getParser(MetadataProvider provider) {
        BookParser parser = parserMap.get(provider);
        if (parser == null) {
            throw ApiError.METADATA_SOURCE_NOT_IMPLEMENT_OR_DOES_NOT_EXIST.createException();
        }
        return parser;
    }

    public BookMetadata updateFieldLockState(long bookId, String field, boolean isLocked) {
        BookMetadataEntity existingMetadata = bookMetadataRepository.findById(bookId).orElseThrow(() -> new RuntimeException("Book metadata not found"));
        switch (field) {
            case "title":
                existingMetadata.setTitleLocked(isLocked);
                break;
            case "subtitle":
                existingMetadata.setSubtitleLocked(isLocked);
                break;
            case "authors":
                existingMetadata.setAuthorsLocked(isLocked);
                break;
            case "categories":
                existingMetadata.setCategoriesLocked(isLocked);
                break;
            case "publisher":
                existingMetadata.setPublisherLocked(isLocked);
                break;
            case "publishedDate":
                existingMetadata.setPublishedDateLocked(isLocked);
                break;
            case "isbn10":
                existingMetadata.setIsbn10Locked(isLocked);
                break;
            case "isbn13":
                existingMetadata.setIsbn13Locked(isLocked);
                break;
            case "asin":
                existingMetadata.setAsinLocked(isLocked);
                break;
            case "description":
                existingMetadata.setDescriptionLocked(isLocked);
                break;
            case "pageCount":
                existingMetadata.setPageCountLocked(isLocked);
                break;
            case "language":
                existingMetadata.setLanguageLocked(isLocked);
                break;
            case "rating":
                existingMetadata.setRatingLocked(isLocked);
                break;
            case "reviewCount":
                existingMetadata.setReviewCountLocked(isLocked);
                break;
            default:
                throw new IllegalArgumentException("Invalid field name: " + field);
        }
        return bookMetadataMapper.toBookMetadata(bookMetadataRepository.save(existingMetadata), true);
    }

    public BookMetadata handleCoverUpload(Long bookId, MultipartFile file) {
        fileService.createThumbnailFromFile(bookId, file);
        BookMetadataEntity metadata = bookMetadataRepository.findById(bookId).orElseThrow(() -> new IllegalArgumentException("Book not found with ID: " + bookId));
        metadata.setCoverUpdatedOn(Instant.now());
        bookMetadataRepository.save(metadata);
        return bookMetadataMapper.toBookMetadata(metadata, true);
    }

    public void regenerateCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookEntity.getMetadata().getCoverLocked() != null && bookEntity.getMetadata().getCoverLocked()) {
            throw ApiError.METADATA_LOCKED.createException();
        } else {
            regenerateCoverForBook(bookEntity, "");
        }
    }

    public void regenerateCovers() {
        Thread.startVirtualThread(() -> {
            List<BookEntity> books = bookRepository.findAll().stream()
                    .filter(book -> book.getMetadata().getCoverLocked() == null || !book.getMetadata().getCoverLocked())
                    .toList();
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, createLogNotification("Started regenerating covers for " + total + " books"));
            int[] current = {1};
            for (BookEntity book : books) {
                try {
                    String progress = "(" + current[0] + "/" + total + ") ";
                    regenerateCoverForBook(book, progress);
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", book.getId(), e.getMessage());
                }
                current[0]++;
            }
            notificationService.sendMessage(Topic.LOG, createLogNotification("Finished regenerating covers"));
        });
    }

    private void regenerateCoverForBook(BookEntity book, String progress) {
        String title = book.getMetadata().getTitle();
        String message = progress + "Regenerating cover for: " + title;
        notificationService.sendMessage(Topic.LOG, createLogNotification(message));
        switch (book.getBookType()) {
            case PDF -> pdfProcessor.generateCover(book);
            case EPUB -> epubProcessor.generateCover(book);
            case CBX -> cbxProcessor.generateCover(book);
            default -> throw ApiError.UNSUPPORTED_BOOK_TYPE.createException(book.getBookType());
        }
        log.info("{}Successfully regenerated cover for book ID {} ({})", progress, book.getId(), title);
    }

    @Transactional
    public List<BookMetadata> toggleAllLock(ToggleAllLockRequest request) {
        boolean lock = request.getLock() == Lock.LOCK;
        List<BookEntity> books = bookRepository.findAllByIdIn(request.getBookIds())
                .stream()
                .peek(book -> book.getMetadata().applyLockToAllFields(lock))
                .toList();
        bookRepository.saveAll(books);
        return books.stream().map(b ->bookMetadataMapper.toBookMetadata(b.getMetadata(), false)).collect(Collectors.toList());
    }
}