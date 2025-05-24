package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.util.FileUtils;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
@AllArgsConstructor
public class CbxProcessor implements FileProcessor {

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
            return bookOptional.map(bookMapper::toBook).orElseGet(() -> processNewFile(libraryFile));
        } else {
            return processNewFile(libraryFile);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Book processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.CBX);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        setMetadata(bookEntity);
        bookCreatorService.saveConnections(bookEntity);
        bookEntity = bookRepository.save(bookEntity);
        bookRepository.flush();
        return bookMapper.toBook(bookEntity);
    }

    public boolean generateCover(BookEntity bookEntity) {
        File file = new File(FileUtils.getBookFullPath(bookEntity));
        try {
            Optional<BufferedImage> imageOptional = extractImagesFromArchive(file);
            if (imageOptional.isPresent()) {
                BufferedImage firstImage = imageOptional.get();
                boolean saved = fileProcessingUtils.saveCoverImage(firstImage, bookEntity.getId());
                if (saved) {
                    bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
                    bookMetadataRepository.save(bookEntity.getMetadata());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error generating cover from archive {}, error: {}", bookEntity.getFileName(), e.getMessage());
            return false;
        }
    }

    private Optional<BufferedImage> extractImagesFromArchive(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".cbz")) {
            return extractFirstImageFromZip(file);
        } else if (name.endsWith(".cb7")) {
            return extractFirstImageFrom7z(file);
        } else if (name.endsWith(".cbr")) {
            return extractFirstImageFromRar(file);
        } else {
            log.warn("Unsupported archive format: {}", name);
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> extractFirstImageFromRar(File file) {
        try (Archive archive = new Archive(file)) {
            List<FileHeader> headers = archive.getFileHeaders();
            List<FileHeader> imageHeaders = headers.stream()
                    .filter(h -> !h.isDirectory())
                    .filter(h -> {
                        String fileName = h.getFileNameString().replace("\\", "/").toLowerCase();
                        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".webp");
                    })
                    .sorted(Comparator.comparing(FileHeader::getFileNameString))
                    .toList();

            for (FileHeader header : imageHeaders) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(header, baos);
                    byte[] imageData = baos.toByteArray();
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
                    if (img != null) {
                        return Optional.of(img);
                    }
                } catch (Exception e) {
                    log.warn("Failed to read image from RAR entry {}: {}", header.getFileNameString(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error extracting image from RAR archive {}: {}", file.getName(), e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BufferedImage> extractFirstImageFromZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return Collections.list(zipFile.getEntries()).stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName().matches("(?i).*\\.(jpg|jpeg|png|webp)"))
                    .min(Comparator.comparing(ZipArchiveEntry::getName))
                    .map(entry -> {
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            return ImageIO.read(is);
                        } catch (Exception e) {
                            log.warn("Failed to read image from ZIP entry {}: {}", entry.getName(), e.getMessage());
                            return null;
                        }
                    });
        } catch (Exception e) {
            log.error("Error extracting image from ZIP archive {}: {}", file.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> extractFirstImageFrom7z(File file) {
        try (SevenZFile sevenZFile = new SevenZFile(file)) {
            List<SevenZArchiveEntry> imageEntries = new ArrayList<>();
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().matches("(?i).*\\.(jpg|jpeg|png|webp)")) {
                    imageEntries.add(entry);
                }
            }
            imageEntries.sort(Comparator.comparing(SevenZArchiveEntry::getName));
            sevenZFile.close();

            try (SevenZFile sevenZFileReset = new SevenZFile(file)) {
                for (SevenZArchiveEntry imgEntry : imageEntries) {
                    SevenZArchiveEntry currentEntry;
                    while ((currentEntry = sevenZFileReset.getNextEntry()) != null) {
                        if (currentEntry.equals(imgEntry)) {
                            byte[] content = new byte[(int) currentEntry.getSize()];
                            int offset = 0;
                            while (offset < content.length) {
                                int bytesRead = sevenZFileReset.read(content, offset, content.length - offset);
                                if (bytesRead < 0) {
                                    break;
                                }
                                offset += bytesRead;
                            }
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(content));
                            if (img != null) {
                                return Optional.of(img);
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error extracting image from 7z archive {}: {}", file.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private void setMetadata(BookEntity bookEntity) {
        String baseName = new File(bookEntity.getFileName()).getName();
        String title = baseName.replaceAll("(?i)\\.cb[sz7]$", "").replaceAll("[_\\-]", " ").trim();
        bookEntity.getMetadata().setTitle(truncate(title, 1000));
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}