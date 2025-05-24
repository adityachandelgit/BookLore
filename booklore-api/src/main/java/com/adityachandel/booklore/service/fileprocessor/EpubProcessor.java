package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.util.FileUtils;
import io.documentnode.epub4j.domain.Identifier;
import io.documentnode.epub4j.domain.Metadata;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class EpubProcessor implements FileProcessor {

    private final BookRepository bookRepository;
    private final BookCreatorService bookCreatorService;
    private final BookMapper bookMapper;
    private final FileProcessingUtils fileProcessingUtils;
    private final BookMetadataRepository bookMetadataRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Book processFile(LibraryFile libraryFile, boolean forceProcess) {
        File bookFile = new File(libraryFile.getFileName());
        String fileName = bookFile.getName();
        if (!forceProcess) {
            Optional<BookEntity> bookOptional = bookRepository.findBookByFileNameAndLibraryId(fileName, libraryFile.getLibraryEntity().getId());
            return bookOptional
                    .map(bookMapper::toBook)
                    .orElseGet(() -> processNewFile(libraryFile));
        } else {
            return processNewFile(libraryFile);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Book processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB);
        setBookMetadata(bookEntity);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        bookCreatorService.saveConnections(bookEntity);
        bookRepository.save(bookEntity);
        bookRepository.flush();
        return bookMapper.toBook(bookEntity);
    }

    public boolean generateCover(BookEntity bookEntity) {
        try {
            File epubFile = new File(FileUtils.getBookFullPath(bookEntity));
            io.documentnode.epub4j.domain.Book epub = new EpubReader().readEpub(new FileInputStream(epubFile));

            // Try the default method
            Resource coverImage = epub.getCoverImage();

            // Fallback: look for a manifest resource with common "cover" keywords
            if (coverImage == null) {
                for (Resource res : epub.getResources().getAll()) {
                    String id = res.getId();
                    String href = res.getHref();

                    if ((id != null && id.toLowerCase().contains("cover")) ||
                        (href != null && href.toLowerCase().contains("cover"))) {

                        if (res.getMediaType() != null && res.getMediaType().getName().startsWith("image")) {
                            coverImage = res;
                            break;
                        }
                    }
                }
            }

            boolean saved = saveCoverImage(coverImage, bookEntity.getId());
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
            bookMetadataRepository.save(bookEntity.getMetadata());
            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for epub file {}, error: {}", bookEntity.getFileName(), e.getMessage(), e);
        }
        return false;
    }

    private static Set<String> getAuthors(io.documentnode.epub4j.domain.Book book) {
        return book.getMetadata().getAuthors().stream()
                .map(author -> author.getFirstname() + " " + author.getLastname())
                .collect(Collectors.toSet());
    }

    private void setBookMetadata(BookEntity bookEntity) {
        try {
            io.documentnode.epub4j.domain.Book book = new EpubReader().readEpub(new FileInputStream(FileUtils.getBookFullPath(bookEntity)));
            BookMetadataEntity bookMetadata = bookEntity.getMetadata();
            Metadata epubMetadata = book.getMetadata();
            if (epubMetadata != null) {
                bookMetadata.setTitle(truncate(epubMetadata.getFirstTitle(), 1000));

                if (epubMetadata.getDescriptions() != null && !epubMetadata.getDescriptions().isEmpty()) {
                    bookMetadata.setDescription(truncate(epubMetadata.getDescriptions().getFirst(), 2000));
                }

                if (epubMetadata.getPublishers() != null && !epubMetadata.getPublishers().isEmpty()) {
                    bookMetadata.setPublisher(truncate(epubMetadata.getPublishers().getFirst(), 2000));
                }

                List<String> identifiers = epubMetadata.getIdentifiers().stream()
                        .map(Identifier::getValue)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .map(id -> id.contains(":") ? id.substring(id.indexOf(":") + 1) : id) // strip prefix
                        .map(id -> id.replace("-", "")) // remove dashes
                        .toList();
                        log.debug("Found {} identifier nodes.", identifiers.size());
                        log.debug("Normalized identifiers: {}", identifiers);


                if (!identifiers.isEmpty()) {
                    // Checking for ISBN 13 (strict 13-digit numeric)
                    String isbn13 = identifiers.stream()
                            .filter(id -> id.length() == 13 && id.matches("\\d{13}"))
                            .findFirst()
                            .orElse(null);

                    // Checking for ISBN 10 (strict 10-digit format or ends in X)
                    String isbn10 = identifiers.stream()
                            .filter(id -> id.length() == 10 && id.matches("\\d{9}[\\dXx]"))
                            .findFirst()
                            .orElse(null);

                    // Checking for ASIN (alphanumeric 10 chars, must not be ISBN-10)
                    String asin = identifiers.stream()
                            .filter(id -> id.length() == 10 && id.matches("^[A-Z0-9]{10}$"))
                            .filter(id -> !id.equalsIgnoreCase(isbn10))
                            .findFirst()
                            .orElse(null);

                    bookMetadata.setIsbn13(truncate(isbn13, 64));
                    bookMetadata.setIsbn10(truncate(isbn10, 64));
                    bookMetadata.setAsin(truncate(asin, 20));
                }

                bookMetadata.setLanguage(truncate(
                        epubMetadata.getLanguage() == null || epubMetadata.getLanguage().equalsIgnoreCase("UND") ? "en" : epubMetadata.getLanguage(), 1000
                ));

                if (epubMetadata.getDates() != null && !epubMetadata.getDates().isEmpty()) {
                    epubMetadata.getDates().stream()
                            .findFirst()
                            .ifPresent(publishedDate -> {
                                String dateString = publishedDate.getValue();
                                if (isValidLocalDate(dateString)) {
                                    LocalDate parsedDate = LocalDate.parse(dateString);
                                    bookMetadata.setPublishedDate(parsedDate);
                                } else if (isValidOffsetDateTime(dateString)) {
                                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateString);
                                    bookMetadata.setPublishedDate(offsetDateTime.toLocalDate());
                                } else {
                                    log.error("Unable to parse date: {}", dateString);
                                }
                            });
                }

                String seriesName = epubMetadata.getMetaAttribute("calibre:series");
                if (seriesName != null && !seriesName.isEmpty()) {
                    bookMetadata.setSeriesName(truncate(seriesName, 1000));
                }

                String seriesIndex = epubMetadata.getMetaAttribute("calibre:series_index");
                if (seriesIndex != null && !seriesIndex.isEmpty()) {
                    try {
                        double indexValue = Double.parseDouble(seriesIndex);
                        bookMetadata.setSeriesNumber((float) indexValue);
                    } catch (NumberFormatException e) {
                        log.warn("Unable to parse series number: {}", seriesIndex);
                    }
                }

                bookCreatorService.addAuthorsToBook(getAuthors(book), bookEntity);
                bookCreatorService.addCategoriesToBook(epubMetadata.getSubjects(), bookEntity);
            }
        } catch (Exception e) {
            log.error("Error loading epub file {}, error: {}", bookEntity.getFileName(), e.getMessage());
        }
    }

    private boolean saveCoverImage(Resource coverImage, long bookId) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(coverImage.getData()));
        return fileProcessingUtils.saveCoverImage(originalImage, bookId);
    }

    private boolean isValidLocalDate(String dateString) {
        try {
            LocalDate.parse(dateString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isValidOffsetDateTime(String dateString) {
        try {
            OffsetDateTime.parse(dateString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}