package com.adityachandel.booklore.service.fileprocessor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
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
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

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
        log.debug("***Setting metadata for book {}", bookEntity.getFileName());
        try (FileInputStream fis =
                new FileInputStream(FileUtils.getBookFullPath(bookEntity))) {

            io.documentnode.epub4j.domain.Book book =
                new EpubReader().readEpub(fis);

            BookMetadataEntity bookMetadata = bookEntity.getMetadata();
            Metadata            epubMetadata = book.getMetadata();

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
                        .toList();
                if (!identifiers.isEmpty()) {
                    String isbn13 = identifiers.stream().filter(id -> id.length() == 13).findFirst().orElse(null);
                    String isbn10 = identifiers.stream().filter(id -> id.length() == 10).findFirst().orElse(null);
                    bookMetadata.setIsbn13(truncate(isbn13, 64));
                    bookMetadata.setIsbn10(truncate(isbn10, 64));
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
                
                // Calibre (EPUB2) series tags
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

                //  fall-back to OPF for anything still missing
                extractFromOpf(FileUtils.getBookFullPath(bookEntity), bookMetadata);

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

    private void extractFromOpf(String epubPath, BookMetadataEntity meta) {
        log.debug("*** Extracting metadata from OPF for book: {}", epubPath);
        try {
            // 1. open as a zip
            log.debug("Step 1: Opening EPUB as zip...");
            ZipFile zip = new ZipFile(epubPath);
            log.debug("Step 1: EPUB zip opened: {}", epubPath);

            // 2. prepare secure DOM builder
            log.debug("Step 2: Preparing secure XML DOM builder...");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            log.debug("Step 2: Secure XML DOM builder prepared.");

            // 3. pull container.xml
            log.debug("Step 3: Retrieving META-INF/container.xml...");
            FileHeader containerHdr = zip.getFileHeader("META-INF/container.xml");
            log.debug("Step 3: container.xml file header: {}", containerHdr);

            if (containerHdr == null) {
                log.debug("Step 3: container.xml NOT found in EPUB: {}", epubPath);
                return;
            }
            try (InputStream cis = zip.getInputStream(containerHdr)) {
                log.debug("Step 3: Parsing container.xml...");
                Document containerDoc = builder.parse(cis);
                NodeList roots = containerDoc.getElementsByTagName("rootfile");
                log.debug("Step 3: rootfile NodeList length: {}", roots.getLength());
                if (roots.getLength() == 0) {
                    log.debug("Step 3: No <rootfile> entries in container.xml for: {}", epubPath);
                    return;
                }
                log.debug("Step 3: Found <rootfile> entries in container.xml");

                String opfPath = ((Element) roots.item(0)).getAttribute("full-path");
                log.debug("Step 3: opfPath extracted: '{}'", opfPath);
                if (StringUtils.isBlank(opfPath)) {
                    log.debug("Step 3: OPF path is blank in container.xml for: {}", epubPath);
                    return;
                }
                log.debug("Step 3: Located OPF path: {}", opfPath);

                // 4. pull the OPF itself
                log.debug("Step 4: Retrieving OPF file header...");
                FileHeader opfHdr = zip.getFileHeader(opfPath);
                log.debug("Step 4: OPF file header: {}", opfHdr);
                if (opfHdr == null) {
                    log.debug("Step 4: OPF file not found at {} in EPUB: {}", opfPath, epubPath);
                    return;
                }
                log.debug("Step 4: Found OPF file at {}", opfPath);

                try (InputStream in = zip.getInputStream(opfHdr)) {
                    log.debug("Step 4: Parsing OPF document...");
                    Document doc = builder.parse(in);
                    log.debug("Step 4: OPF document parsed successfully.");

                // ── IDENTIFIERS (ISBN) ───────────────────────────────
                if (StringUtils.isBlank(meta.getIsbn13()) ||
                    StringUtils.isBlank(meta.getIsbn10())) {
                    log.debug("Step 5: Extracting ISBNs from OPF...");
                    NodeList idNodes = doc.getElementsByTagNameNS("*", "identifier");
                    log.debug("Step 5: Found {} identifier nodes.", idNodes.getLength());
                    for (int i = 0; i < idNodes.getLength(); i++) {
                        String idRaw = idNodes.item(i).getTextContent().trim();
                        log.debug("Step 5: Identifier node content: '{}'", idRaw);

                        String isbn = idRaw;
                        // If identifier starts with "isbn:", extract what follows
                        if (isbn.toLowerCase().startsWith("isbn:")) {
                            isbn = isbn.substring(5);
                        }

                        if (isbn.length() == 13 && StringUtils.isBlank(meta.getIsbn13())) {
                            meta.setIsbn13(truncate(isbn, 64));
                            log.info("Extracted ISBN-13: {}", isbn);
                        }
                        if (isbn.length() == 10 && StringUtils.isBlank(meta.getIsbn10())) {
                            meta.setIsbn10(truncate(isbn, 64));
                            log.info("Extracted ISBN-10: {}", isbn);
                        }
                    }
                }

                    // ── <meta> TAGS (series & pages) ────────────────────
                    log.debug("Step 6: Extracting <meta> tags for series/pages...");
                    NodeList metaNodes = doc.getElementsByTagNameNS("*", "meta");
                    log.debug("Step 6: Found {} <meta> nodes.", metaNodes.getLength());
                    for (int i = 0; i < metaNodes.getLength(); i++) {
                        Element m        = (Element) metaNodes.item(i);
                        String  nameAttr = m.getAttribute("name");
                        String  propAttr = m.getAttribute("property");
                        String  content  = m.hasAttribute("content")
                                        ? m.getAttribute("content").trim()
                                        : m.getTextContent().trim();
                        log.debug("Step 6: meta[{}]: name='{}', property='{}', content='{}'", i, nameAttr, propAttr, content);

                        // series name
                        if (StringUtils.isBlank(meta.getSeriesName())
                            && ("calibre:series".equalsIgnoreCase(nameAttr)
                                || "belongs-to-collection".equalsIgnoreCase(propAttr))) {
                            meta.setSeriesName(truncate(content, 1000));
                            log.info("Extracted series name: {}", content);
                        }
                        // series number
                        if (meta.getSeriesNumber() == null
                            && ("calibre:series_index".equalsIgnoreCase(nameAttr)
                                || "group-position".equalsIgnoreCase(propAttr))) {
                            try {
                                int number = (int) Double.parseDouble(content);
                                meta.setSeriesNumber(number);
                                log.info("Extracted series number: {}", number);
                            } catch (NumberFormatException ignored) {
                                log.debug("Invalid series number value: '{}'", content);
                            }
                        }
                        // page count
                        if (meta.getPageCount() == null
                            && ("calibre:pages".equalsIgnoreCase(nameAttr)
                                || "pageCount".equalsIgnoreCase(nameAttr)
                                || "schema:pageCount".equalsIgnoreCase(propAttr)
                                || "media:pageCount".equalsIgnoreCase(propAttr))) {
                            try {
                                int pages = Integer.parseInt(content);
                                meta.setPageCount(pages);
                                log.info("Extracted page count: {}", pages);
                            } catch (NumberFormatException ignored) {
                                log.debug("Invalid page count value: '{}'", content);
                            }
                        }
                    }
                    log.debug("Step 6: <meta> extraction complete.");
                }
            }
        } catch (Exception e) {
            log.debug("Unable to parse OPF metadata for {}: {}", epubPath, e.getMessage(), e);
        }
    }
}