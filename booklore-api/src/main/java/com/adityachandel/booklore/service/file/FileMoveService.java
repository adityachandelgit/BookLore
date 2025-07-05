package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class FileMoveService {

    private final BookQueryService bookQueryService;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final LibraryService libraryService;
    private final MonitoringService monitoringService;

    public void moveFiles(FileMoveRequest request) {
        Set<Long> bookIds = request.getBookIds();
        String pattern = request.getPattern();
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);

        log.info("Starting file move for {} books using pattern: '{}'", books.size(), pattern);

        Set<Long> libraryIds = new HashSet<>();
        List<Book> updatedBooks = new ArrayList<>();
        boolean didPause = false;

        if (!monitoringService.isPaused()) {
            monitoringService.pauseMonitoring();
            didPause = true;
        }

        try {
            for (BookEntity book : books) {
                if (book.getMetadata() == null) continue;

                if (book.getLibraryPath() == null || book.getLibraryPath().getPath() == null
                        || book.getFileSubPath() == null || book.getFileName() == null) {
                    log.error("Missing required path components for book id {}. Skipping.", book.getId());
                    continue;
                }

                Path oldFilePath = book.getFullFilePath();
                if (!Files.exists(oldFilePath)) {
                    log.warn("File does not exist for book id {}: {}", book.getId(), oldFilePath);
                    continue;
                }

                log.info("Processing book id {}: '{}'", book.getId(), book.getMetadata().getTitle());

                String newRelativePathStr = generatePathFromPattern(book, pattern);
                if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
                    newRelativePathStr = newRelativePathStr.substring(1);
                }

                Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
                Path newFilePath = libraryRoot.resolve(newRelativePathStr).normalize();

                if (oldFilePath.equals(newFilePath)) {
                    log.info("Source and destination paths are identical for book id {}. Skipping.", book.getId());
                    continue;
                }

                try {
                    if (newFilePath.getParent() != null) {
                        Files.createDirectories(newFilePath.getParent());
                    }

                    log.info("Moving file from {} to {}", oldFilePath, newFilePath);
                    Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

                    String newFileName = newFilePath.getFileName().toString();
                    Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
                    String newFileSubPath = newRelativeSubPath.toString().replace('\\', '/');

                    book.setFileSubPath(newFileSubPath);
                    book.setFileName(newFileName);
                    bookRepository.save(book);

                    updatedBooks.add(bookMapper.toBook(book));
                    log.info("Updated book id {} with new path", book.getId());

                    deleteEmptyParentDirsUpToLibraryFolders(oldFilePath.getParent(), Set.of(libraryRoot));

                    if (book.getLibraryPath().getLibrary().getId() != null) {
                        libraryIds.add(book.getLibraryPath().getLibrary().getId());
                    }
                } catch (IOException e) {
                    log.error("Failed to move file for book id {}: {}", book.getId(), e.getMessage(), e);
                }
            }

            log.info("Completed file move for {} books.", books.size());

            if (!updatedBooks.isEmpty()) {
                notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_UPDATE, updatedBooks);
            }

            for (Long libraryId : libraryIds) {
                try {
                    libraryService.rescanLibrary(libraryId);
                    log.info("Rescanned library id {} after file move", libraryId);
                } catch (Exception e) {
                    log.error("Failed to rescan library id {}: {}", libraryId, e.getMessage(), e);
                }
            }

        } finally {
            if (didPause && !monitoringService.isPaused()) {
                log.info("Monitoring already resumed by another thread.");
            } else if (didPause) {
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(5_000);
                        monitoringService.resumeMonitoring();
                        log.info("Monitoring resumed after 5s delay");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while delaying resume of monitoring");
                    }
                });
            }
        }
    }

    public String generatePathFromPattern(BookEntity book, String pattern) {
        return PathPatternResolver.resolvePattern(book, pattern);
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) throws IOException {
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        currentDir = currentDir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (currentDir != null) {
            boolean isLibraryRoot = false;
            for (Path root : normalizedRoots) {
                try {
                    if (Files.isSameFile(root, currentDir)) {
                        isLibraryRoot = true;
                        break;
                    }
                } catch (IOException e) {
                    log.warn("Failed to compare paths: {} and {}", root, currentDir);
                }
            }

            if (isLibraryRoot) {
                log.debug("Reached library root: {}. Stopping cleanup.", currentDir);
                break;
            }

            File[] files = currentDir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", currentDir);
                break;
            }

            boolean hasImportantFiles = false;
            for (File file : files) {
                if (!ignoredFilenames.contains(file.getName())) {
                    hasImportantFiles = true;
                    break;
                }
            }

            if (!hasImportantFiles) {
                for (File file : files) {
                    try {
                        Files.delete(file.toPath());
                        log.info("Deleted ignored file: {}", file.getAbsolutePath());
                    } catch (IOException e) {
                        log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
                    }
                }
                try {
                    Files.delete(currentDir);
                    log.info("Deleted empty directory: {}", currentDir);
                } catch (IOException e) {
                    log.warn("Failed to delete directory: {}", currentDir, e);
                    break;
                }
                currentDir = currentDir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", currentDir);
                break;
            }
        }
    }
}