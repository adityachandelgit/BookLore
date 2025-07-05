package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@AllArgsConstructor
@Service
@Slf4j
public class LibraryProcessingService {

    private final LibraryRepository libraryRepository;
    private final NotificationService notificationService;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final CbxProcessor cbxProcessor;
    private final BookRepository bookRepository;
    private final FileService fileService;

    @Transactional
    public void processLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started processing library: " + libraryEntity.getName()));
        List<LibraryFile> libraryFiles = getLibraryFiles(libraryEntity);
        processLibraryFiles(libraryFiles);
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished processing library: " + libraryEntity.getName()));
    }

    @Transactional
    public void rescanLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started refreshing library: " + libraryEntity.getName()));
        deleteRemovedBooks(detectDeletedBookIds(libraryEntity));
        processLibraryFiles(detectNewBookPaths(libraryEntity));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished refreshing library: " + libraryEntity.getName()));
    }

    public List<Long> detectDeletedBookIds(LibraryEntity libraryEntity) throws IOException {
        Set<String> currentFileNames = getLibraryFiles(libraryEntity).stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());

        return libraryEntity.getBookEntities().stream()
                .filter(book -> (book.getDeleted() == null || !book.getDeleted()))
                .filter(book -> !currentFileNames.contains(book.getFileName()))
                .map(BookEntity::getId)
                .collect(Collectors.toList());
    }

    public List<LibraryFile> detectNewBookPaths(LibraryEntity libraryEntity) throws IOException {
        Set<String> existingFileNames = libraryEntity.getBookEntities().stream()
                .map(BookEntity::getFileName)
                .collect(Collectors.toSet());
        return getLibraryFiles(libraryEntity).stream()
                .filter(file -> !existingFileNames.contains(file.getFileName()))
                .collect(Collectors.toList());
    }

    @Transactional
    protected void deleteRemovedBooks(List<Long> bookIds) {
        List<BookEntity> books = bookRepository.findAllById(bookIds);
        for (BookEntity book : books) {
            try {
                if (book.getMetadata() != null && StringUtils.isNotBlank(book.getMetadata().getThumbnail())) {
                    deleteDirectoryRecursively(Path.of(fileService.getThumbnailPath(book.getId())));
                }
                Path backupDir = Path.of(fileService.getBookMetadataBackupPath(book.getId()));
                if (Files.exists(backupDir)) {
                    deleteDirectoryRecursively(backupDir);
                }
            } catch (Exception e) {
                log.warn("Failed to clean up files for book ID {}: {}", book.getId(), e.getMessage());
            }
        }
        bookRepository.deleteAll(books);
        notificationService.sendMessage(Topic.BOOKS_REMOVE, bookIds);
        if (bookIds.size() > 1) log.info("Books removed: {}", bookIds);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete file or directory: {}", p, e);
                    }
                });
            }
        }
    }

    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles) {
        for (LibraryFile libraryFile : libraryFiles) {
            log.info("Processing file: {}", libraryFile.getFileName());
            Book book = processLibraryFile(libraryFile);
            if (book != null) {
                notificationService.sendMessage(Topic.BOOK_ADD, book);
                notificationService.sendMessage(Topic.LOG, createLogNotification("Book added: " + book.getFileName()));
                log.info("Processed file: {}", libraryFile.getFileName());
            }
        }
    }

    @Transactional
    protected Book processLibraryFile(LibraryFile libraryFile) {
        return switch (libraryFile.getBookFileType()) {
            case PDF -> pdfProcessor.processFile(libraryFile, false);
            case EPUB -> epubProcessor.processFile(libraryFile, false);
            case CBX -> cbxProcessor.processFile(libraryFile, false);
        };
    }

    private List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFiles(pathEntity, libraryEntity));
        }
        return allFiles;
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity pathEntity, LibraryEntity libraryEntity) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        try (Stream<Path> stream = Files.walk(libraryPath)) {
            return stream.filter(Files::isRegularFile)
                    .map(fullPath -> {
                        String fileName = fullPath.getFileName().toString();
                        return BookFileExtension.fromFileName(fileName)
                                .map(ext -> LibraryFile.builder()
                                        .libraryEntity(libraryEntity)
                                        .libraryPathEntity(pathEntity)
                                        .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), fullPath))
                                        .fileName(fileName)
                                        .bookFileType(ext.getType())
                                        .build())
                                .orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .filter(file -> !file.getFileName().startsWith("."))
                    .toList();
        }
    }
}