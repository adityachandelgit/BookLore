package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@AllArgsConstructor
public class LibraryFileEventProcessor {

    private final BlockingQueue<FileEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LibraryRepository libraryRepository;
    private final BookFileTransactionalHandler bookFileTransactionalHandler;
    private final BookFilePersistenceService bookFilePersistenceService;
    private final NotificationService notificationService;

    @PostConstruct
    public void init() {
        Thread.ofVirtual().start(() -> {
            log.info("LibraryFileEventProcessor virtual thread started.");
            while (true) {
                try {
                    FileEvent event = eventQueue.take();
                    handleEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("LibraryFileEventProcessor virtual thread interrupted.");
                    break;
                } catch (Exception e) {
                    log.error("Error while processing file event", e);
                }
            }
        });
    }

    private void handleEvent(FileEvent event) {
        log.info("[PROCESS] Handling '{}' event for '{}'", event.eventKind().name(), event.filePath());

        LibraryEntity libraryEntity = libraryRepository.findById(event.libraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(event.libraryId()));

        Path path = Paths.get(event.filePath());
        String fileName = path.getFileName().toString();

        boolean underLibrary = libraryEntity.getLibraryPaths().stream().anyMatch(lp -> path.toString().startsWith(lp.getPath()));

        boolean hasBookExtension = isRelevantBookFile(path);
        boolean isFolderCandidate = !fileName.contains(".");

        if (!underLibrary) {
            log.debug("[PROCESS] Ignoring path outside library: '{}'", path);
            return;
        }

        // === Folder Paths ===
        if (isFolderCandidate) {
            if (event.eventKind() == StandardWatchEventKinds.ENTRY_CREATE) {
                log.info("[FOLDER_CREATE] New folder detected: '{}'", path);
                handleFolderCreate(libraryEntity, path);
            } else if (event.eventKind() == StandardWatchEventKinds.ENTRY_DELETE) {
                log.info("[FOLDER_DELETE] Folder deleted: '{}'", path);
                handleFolderDelete(libraryEntity, path);
            } else {
                log.debug("[FOLDER_IGNORED] Ignored folder event '{}' for '{}'", event.eventKind().name(), path);
            }
            return;
        }

        // === File Paths ===
        if (!hasBookExtension) {
            log.debug("[PROCESS] Ignored non-book file: '{}'", path);
            return;
        }

        if (event.eventKind() == StandardWatchEventKinds.ENTRY_CREATE) {
            handleFileCreate(libraryEntity, path);
        } else if (event.eventKind() == StandardWatchEventKinds.ENTRY_DELETE) {
            log.info("[FILE_DELETE] Book file deleted: '{}'", path);
            handleFileDelete(libraryEntity, path);
        } else {
            log.debug("[FILE_IGNORED] Ignored file event '{}' for '{}'", event.eventKind().name(), path);
        }
    }

    private void handleFileCreate(LibraryEntity libraryEntity, Path path) {
        log.info("[FILE_CREATE] Handling file create for '{}'", path);

        String currentHash = FileUtils.computeFileHash(path);
        if (currentHash == null) {
            log.warn("[FILE_CREATE] Could not compute hash for '{}'", path);
            return;
        }

        bookFileTransactionalHandler.handleNewBookFile(libraryEntity.getId(), path, currentHash);
    }

    private void handleFileDelete(LibraryEntity libraryEntity, Path path) {
        log.info("[FILE_DELETE] Handling file delete for '{}'", path);

        try {
            String matchingLibraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, path);
            LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, matchingLibraryPath);

            Path libraryRoot = Paths.get(libraryPathEntity.getPath()).toAbsolutePath().normalize();
            Path fullPath = path.toAbsolutePath().normalize();

            Path relativePath = libraryRoot.relativize(fullPath);

            String fileName = relativePath.getFileName().toString();
            String fileSubPath = relativePath.getParent() == null ? "" : relativePath.getParent().toString();

            Optional<BookEntity> bookOpt = bookFilePersistenceService.findByLibraryPathSubPathAndFileName(libraryPathEntity.getId(), fileSubPath, fileName);

            if (bookOpt.isPresent()) {
                BookEntity book = bookOpt.get();
                book.setDeleted(true);
                bookFilePersistenceService.save(book);
                notificationService.sendMessage(Topic.BOOKS_REMOVE, Set.of(book.getId()));
                log.info("[FILE_DELETE] Marked book as deleted for '{}'", path);
            } else {
                log.warn("[FILE_DELETE] No book found for deleted path '{}'", path);
            }

        } catch (Exception e) {
            log.warn("[FILE_DELETE] Failed to locate library path or book for '{}': {}", path, e.getMessage());
        }
    }

    private void handleFolderCreate(LibraryEntity libraryEntity, Path folderPath) {
        log.info("[FOLDER_CREATE] Handling folder create for '{}'", folderPath);
        try (var stream = Files.walk(folderPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isRelevantBookFile)
                    .forEach(path -> {
                        try {
                            String hash = FileUtils.computeFileHash(path);
                            if (hash != null) {
                                bookFileTransactionalHandler.handleNewBookFile(libraryEntity.getId(), path, hash);
                            }
                        } catch (Exception e) {
                            log.warn("[FOLDER_CREATE] Error processing file '{}': {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[FOLDER_CREATE] Failed to walk folder '{}': {}", folderPath, e.getMessage());
        }
    }

    private void handleFolderDelete(LibraryEntity libraryEntity, Path folderPath) {
        log.info("[FOLDER_DELETE] Handling folder delete for '{}'", folderPath);
        try {
            String matchingLibraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, folderPath);
            LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, matchingLibraryPath);

            String relativePrefix = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), folderPath);

            int markedCount = bookFilePersistenceService.markAllBooksUnderPathAsDeleted(libraryPathEntity.getId(), relativePrefix);
            log.info("[FOLDER_DELETE] Marked {} books as deleted under '{}'", markedCount, folderPath);
        } catch (Exception e) {
            log.warn("[FOLDER_DELETE] Failed to mark books as deleted under '{}': {}", folderPath, e.getMessage());
        }
    }

    public boolean isRelevantBookFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf")
                || name.endsWith(".epub")
                || name.endsWith(".cbz")
                || name.endsWith(".cbr")
                || name.endsWith(".cb7");
    }

    public void processFile(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
        FileEvent event = new FileEvent(eventKind, libraryId, libraryPath, filePath);
        eventQueue.offer(event);
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
        log.info("LibraryFileEventProcessor worker shutdown.");
    }

    public record FileEvent(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
    }
}