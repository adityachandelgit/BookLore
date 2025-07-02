package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapperv2.BookMapperV2;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
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
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final FileService fileService;

    public final Map<String, BookEntity> recentlyDeletedFiles = new ConcurrentHashMap<>();
    public final Map<String, Instant> recentlyDeletedTimestamps = new ConcurrentHashMap<>();
    private static final int MOVE_DETECTION_WINDOW_SECONDS = 5;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public final Map<String, ScheduledFuture<?>> deletionTasks = new ConcurrentHashMap<>();
    private static final int DELETE_DEBOUNCE_SECONDS = 5;

    @Transactional
    public void processLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started processing library: " + libraryEntity.getName()));
        List<LibraryFile> libraryFiles = getLibraryFiles(libraryEntity);
        processLibraryFiles(libraryFiles);
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished processing library: " + libraryEntity.getName()));
    }

    @Transactional
    public void processFile(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
        log.info("[PROCESS] Event '{}' received for filePath: '{}', libraryId: '{}', libraryPath: '{}'",
                eventKind.name(), filePath, libraryId, libraryPath);

        LibraryEntity libraryEntity = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        Path path = Paths.get(filePath);

        boolean isDir;
        try {
            if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                String fileName = path.getFileName().toString();
                boolean knownFile = bookRepository.findBookByFileNameAndLibraryId(fileName, libraryEntity.getId()).isPresent();
                boolean underLibrary = libraryEntity.getLibraryPaths().stream().anyMatch(lp -> path.toString().startsWith(lp.getPath()));

                // Heuristic fallback: if it looks like a book file, treat as file
                boolean hasBookExtension = isRelevantBookFile(path);

                if (!knownFile && underLibrary && !hasBookExtension) {
                    isDir = true;
                } else {
                    isDir = false;
                }

                log.debug("[PROCESS] ENTRY_DELETE for '{}': knownFile={}, underLibrary={}, hasBookExtension={}, inferred isDir={}",
                        filePath, knownFile, underLibrary, hasBookExtension, isDir);
            } else {
                isDir = Files.isDirectory(path);
                log.debug("[PROCESS] Checked file system for '{}', isDir={}", filePath, isDir);
            }
        } catch (Exception e) {
            log.warn("[PROCESS] Failed to determine if path is directory for '{}', assuming file. Error: {}", filePath, e.toString());
            isDir = false;
        }

        if (isDir) {
            if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                log.info("[PROCESS] Detected folder creation: '{}'", path);
                processFolderCreate(libraryEntity, libraryPath, path);
            } else if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                log.info("[PROCESS] Detected folder deletion: '{}'", path);
                processFolderDelete(libraryEntity, path);
            } else {
                log.info("[PROCESS] Ignored directory event '{}' for '{}'", eventKind.name(), path);
            }
            return;
        }

        if (!isRelevantBookFile(path)) {
            log.debug("[PROCESS] Ignored non-book file: '{}'", path);
            return;
        }

        if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
            log.info("[PROCESS] Detected file creation: '{}'", path);
            processFileCreate(libraryEntity, libraryPath, path);
        } else if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
            log.info("[PROCESS] Detected file deletion: '{}'", path);
            processFileDelete(libraryEntity, path);
        } else {
            log.info("[PROCESS] Ignored file event '{}' for '{}'", eventKind.name(), path);
        }
    }

    @Transactional
    public void processFileCreate(LibraryEntity libraryEntity, String libraryPath, Path path) {
        String filePath = path.toString();
        String fileName = path.getFileName().toString();
        log.info("[CREATE] Event start for file: '{}', library: '{}', path: '{}'", fileName, libraryEntity.getName(), filePath);

        String currentHash = FileUtils.computeFileHash(path);
        log.info("[CREATE] Computed hash '{}' for file '{}'", currentHash, fileName);

        if (currentHash != null) {
            // 🧹 Cancel any scheduled deletion task
            ScheduledFuture<?> scheduledDeletion = deletionTasks.remove(currentHash);
            if (scheduledDeletion != null) {
                scheduledDeletion.cancel(false);
                log.info("[CREATE] Cancelled scheduled deletion for file hash: '{}'", currentHash);
            }

            // 🧹 Remove any leftover staged delete entries (safety)
            recentlyDeletedFiles.remove(currentHash);
            recentlyDeletedTimestamps.remove(currentHash);

            // ✅ Check if file already exists (possible move or reappearance)
            Optional<BookEntity> existingBookOpt = bookRepository.findByCurrentHash(currentHash);
            if (existingBookOpt.isPresent()) {
                BookEntity existingBook = existingBookOpt.get();

                LibraryPathEntity libraryPathEntity = getLibraryPathEntityForFile(libraryEntity, libraryPath);
                libraryPathEntity = entityManager.merge(libraryPathEntity);

                String newSubPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path);

                if (!newSubPath.equals(existingBook.getFileSubPath()) || !libraryPathEntity.equals(existingBook.getLibraryPath())) {
                    existingBook.setLibraryPath(libraryPathEntity);
                    existingBook.setFileSubPath(newSubPath);
                    bookRepository.save(existingBook);

                    log.info("[CREATE] Detected existing book by hash '{}', updated DB path to '{}'", currentHash, filePath);
                    return; // 📦 Move handled
                }
            }
        }

        // 🔄 Fallback: check recently deleted map for debounce-time move detection
        Optional<Map.Entry<String, BookEntity>> movedBookOpt = recentlyDeletedFiles.entrySet().stream()
                .filter(entry -> {
                    String deletedHash = entry.getKey();
                    Instant deletedAt = recentlyDeletedTimestamps.get(deletedHash);
                    if (deletedAt == null) return false;
                    long secondsSinceDelete = Duration.between(deletedAt, Instant.now()).getSeconds();
                    return deletedHash.equals(currentHash) && secondsSinceDelete <= MOVE_DETECTION_WINDOW_SECONDS;
                })
                .findFirst();

        if (movedBookOpt.isPresent()) {
            Map.Entry<String, BookEntity> movedEntry = movedBookOpt.get();
            String oldHash = movedEntry.getKey();
            BookEntity book = movedEntry.getValue();

            log.info("[CREATE] Detected move by hash '{}', old file in DB: '{}', new file: '{}'", oldHash, book.getFileName(), filePath);

            recentlyDeletedFiles.remove(oldHash);
            recentlyDeletedTimestamps.remove(oldHash);

            LibraryPathEntity libraryPathEntity = getLibraryPathEntityForFile(libraryEntity, libraryPath);
            libraryPathEntity = entityManager.merge(libraryPathEntity);

            book.setLibraryPath(libraryPathEntity);
            book.setFileSubPath(FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path));
            bookRepository.save(book);

            log.info("[CREATE] Move processing finished for file '{}'", filePath);
            return;
        }

        // 🧹 Final cleanup if not a move
        if (currentHash != null) {
            recentlyDeletedFiles.remove(currentHash);
            recentlyDeletedTimestamps.remove(currentHash);
            log.info("[CREATE] Cleaned up stale staged entries for hash '{}'", currentHash);
        }

        // 🚀 Proceed with fresh file processing
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started processing file: " + filePath));

        LibraryPathEntity libraryPathEntity = getLibraryPathEntityForFile(libraryEntity, libraryPath);
        libraryPathEntity = entityManager.merge(libraryPathEntity);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path))
                .fileName(fileName)
                .bookFileType(getBookFileType(fileName))
                .build();

        processLibraryFiles(List.of(libraryFile));

        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished processing file: " + filePath));
        log.info("[CREATE] Completed processing for file '{}'", filePath);
    }

    @Transactional
    public void processFileDelete(LibraryEntity libraryEntity, Path path) {
        String fileName = path.getFileName().toString();
        log.info("[DELETE] Event start for file: '{}', library: '{}', path: '{}'", fileName, libraryEntity.getName(), path.toString());

        bookRepository.findBookByFileNameAndLibraryId(fileName, libraryEntity.getId()).ifPresent(book -> {
            String currentHash = book.getCurrentHash();
            if (currentHash != null) {
                recentlyDeletedFiles.put(currentHash, book);
                recentlyDeletedTimestamps.put(currentHash, Instant.now());
                log.info("[DELETE] Staged file for possible move with hash '{}', filename '{}'", currentHash, fileName);
            }
        });

        String hashKey = bookRepository.findBookByFileNameAndLibraryId(fileName, libraryEntity.getId())
                .map(BookEntity::getCurrentHash).orElse(null);

        if (hashKey != null) {
            deletionTasks.putIfAbsent(hashKey, scheduler.schedule(() -> {
                try {
                    if (recentlyDeletedFiles.containsKey(hashKey)) {
                        log.info("[DELETE] File with hash '{}' staged for move — now deleting after debounce window", hashKey);
                    }

                    // 🔍 Check if any file still exists with the same name
                    boolean fileStillExists = libraryEntity.getLibraryPaths().stream()
                            .anyMatch(lp -> Files.exists(Path.of(lp.getPath()).resolve(fileName)));

                    if (!fileStillExists) {
                        // 💡 Also check if that hash still points to an *existing file* in DB
                        Optional<BookEntity> maybeBook = bookRepository.findByCurrentHash(hashKey);
                        if (maybeBook.isPresent()) {
                            BookEntity book = maybeBook.get();
                            if (Files.exists(book.getFullFilePath())) {
                                log.info("[DELETE] Book with hash '{}' still exists at '{}', skipping deletion", hashKey, book.getFullFilePath());
                                return;
                            }
                        }

                        // ✅ Proceed with DB removal
                        transactionTemplate.executeWithoutResult(status -> {
                            bookRepository.findBookByFileNameAndLibraryId(fileName, libraryEntity.getId())
                                    .ifPresent(bookEntity -> {
                                        deleteRemovedBooks(List.of(bookEntity.getId()));
                                        notificationService.sendMessage(Topic.BOOKS_REMOVE, Set.of(bookEntity.getId()));
                                        log.info("[DELETE] Deleted book after debounce: '{}'", fileName);
                                    });
                        });
                    } else {
                        log.info("[DELETE] File '{}' still exists after debounce; skipping delete.", fileName);
                    }
                } catch (Exception e) {
                    log.error("[DELETE] Error during delayed deletion of file with hash '{}': {}", hashKey, e);
                } finally {
                    deletionTasks.remove(hashKey);
                    recentlyDeletedFiles.remove(hashKey);
                    recentlyDeletedTimestamps.remove(hashKey);
                    log.info("[DELETE] Cleaned up staging and deletion task for hash '{}'", hashKey);
                }
            }, DELETE_DEBOUNCE_SECONDS, TimeUnit.SECONDS));
            log.info("[DELETE] Scheduled deletion task for hash '{}'", hashKey);
        }
    }

    @Transactional
    public void processFolderCreate(LibraryEntity libraryEntity, String libraryPath, Path folderPath) {
        log.info("[FOLDER_CREATE] Scanning folder: '{}'", folderPath);

        try (Stream<Path> stream = Files.walk(folderPath)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::isRelevantBookFile)
                    .forEach(file -> {
                        log.info("[FOLDER_CREATE] Detected relevant file inside new folder: '{}'", file);
                        processFileCreate(libraryEntity, libraryPath, file);
                    });
        } catch (IOException e) {
            log.error("[FOLDER_CREATE] Failed to walk folder: '{}'", folderPath, e);
        }
    }

    @Transactional
    public void processFolderDelete(LibraryEntity libraryEntity, Path deletedFolderPath) {
        log.info("🗑️ Folder deleted: {}", deletedFolderPath);

        List<BookEntity> booksToStage = bookRepository.findAllByLibraryId(libraryEntity.getId()).stream()
                .filter(book -> {
                    try {
                        Path bookPath = book.getFullFilePath().normalize();
                        return bookPath.startsWith(deletedFolderPath.normalize());
                    } catch (IllegalStateException e) {
                        log.warn("⚠️ Skipping book with incomplete path info (ID: {}): {}", book.getId(), e.getMessage());
                        return false;
                    }
                })
                .toList();

        for (BookEntity book : booksToStage) {
            String hash = book.getCurrentHash();
            if (hash == null) continue;

            recentlyDeletedFiles.put(hash, book);
            recentlyDeletedTimestamps.put(hash, Instant.now());
            log.info("📦 Staged book '{}' from deleted folder for possible move", book.getFullFilePath());

            deletionTasks.putIfAbsent(hash, scheduler.schedule(() -> {
                try {
                    if (recentlyDeletedFiles.containsKey(hash)) {
                        log.info("File with hash '{}' staged for move — now deleting after debounce window", hash);
                    }

                    // 🔍 Look across all library paths to see if any file still exists with the same hash
                    boolean fileStillExists = libraryEntity.getLibraryPaths().stream()
                            .flatMap(lp -> {
                                try {
                                    return Files.walk(Path.of(lp.getPath()));
                                } catch (IOException e) {
                                    return Stream.empty();
                                }
                            })
                            .filter(Files::isRegularFile)
                            .anyMatch(p -> {
                                try {
                                    return FileUtils.computeFileHash(p).equals(hash);
                                } catch (Exception e) {
                                    return false;
                                }
                            });

                    if (!fileStillExists) {
                        // 💡 Double check if DB record for that hash points to a valid file
                        Optional<BookEntity> maybeBook = bookRepository.findByCurrentHash(hash);
                        if (maybeBook.isPresent()) {
                            BookEntity b = maybeBook.get();
                            if (Files.exists(b.getFullFilePath())) {
                                log.info("Book with hash '{}' still exists at '{}', skipping deletion", hash, b.getFullFilePath());
                                return;
                            }
                        }

                        // ✅ Delete from DB
                        transactionTemplate.executeWithoutResult(status -> {
                            bookRepository.findById(book.getId())
                                    .ifPresent(bookEntity -> {
                                        deleteRemovedBooks(List.of(bookEntity.getId()));
                                        notificationService.sendMessage(Topic.BOOKS_REMOVE, Set.of(bookEntity.getId()));
                                        log.info("Deleted book after debounce: {}", book.getFileName());
                                    });
                        });
                    } else {
                        log.info("File with hash '{}' still exists after debounce; skipping delete.", hash);
                    }
                } catch (Exception e) {
                    log.error("Error during delayed deletion of file: {}", book.getFileName(), e);
                } finally {
                    deletionTasks.remove(hash);
                    recentlyDeletedFiles.remove(hash);
                    recentlyDeletedTimestamps.remove(hash);
                }
            }, DELETE_DEBOUNCE_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Transactional
    protected LibraryPathEntity getLibraryPathEntityForFile(LibraryEntity libraryEntity, String inputPath) {
        Path fullPath = Paths.get(inputPath).toAbsolutePath().normalize();

        LibraryPathEntity libraryPathEntity = libraryEntity.getLibraryPaths().stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> fullPath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(inputPath));
        return libraryPathEntity;
    }

    @Transactional
    protected BookFileType getBookFileType(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        if (lowerCaseName.endsWith(".pdf")) {
            return BookFileType.PDF;
        } else if (lowerCaseName.endsWith(".cbz")) {
            return BookFileType.CBX;
        } else if (lowerCaseName.endsWith(".cbr")) {
            return BookFileType.CBX;
        } else if (lowerCaseName.endsWith(".cb7")) {
            return BookFileType.CBX;
        } else {
            return BookFileType.EPUB;
        }
    }

    @Transactional
    public void rescanLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started refreshing library: " + libraryEntity.getName()));
        detectMovedBooksAndUpdatePaths(libraryEntity);
        entityManager.flush();
        entityManager.clear();
        deleteRemovedBooks(detectDeletedBookIds(libraryEntity));
        entityManager.flush();
        entityManager.clear();
        processLibraryFiles(detectNewBookPaths(libraryEntity));
        entityManager.flush();
        entityManager.clear();
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished refreshing library: " + libraryEntity.getName()));
    }

    void detectMovedBooksAndUpdatePaths(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> currentLibraryFiles = getLibraryFiles(libraryEntity);
        Map<String, Path> currentFileMap = currentLibraryFiles.stream()
                .collect(Collectors.toMap(LibraryFile::getFileName, LibraryFile::getFullPath, (existing, replacement) -> existing));
        for (BookEntity book : libraryEntity.getBookEntities()) {
            Path existingPath = book.getFullFilePath();
            Path updatedPath = currentFileMap.get(book.getFileName());
            if (updatedPath != null && !updatedPath.equals(existingPath)) {
                log.info("Detected moved book: '{}' from '{}' to '{}'", book.getFileName(), existingPath, updatedPath);
                libraryEntity.getLibraryPaths().stream()
                        .filter(lp -> updatedPath.startsWith(Path.of(lp.getPath())))
                        .findFirst()
                        .ifPresent(matchingPath -> {
                            book.setLibraryPath(matchingPath);
                            book.setFileSubPath(FileUtils.getRelativeSubPath(matchingPath.getPath(), updatedPath));
                        });
            }
        }
    }

    public List<Long> detectDeletedBookIds(LibraryEntity libraryEntity) throws IOException {
        Set<String> currentFileNames = getLibraryFiles(libraryEntity).stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());
        return libraryEntity.getBookEntities().stream()
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
                    Path thumbnailPath = Path.of(fileService.getThumbnailPath(book.getId()));
                    deleteDirectoryRecursively(thumbnailPath);
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
        if (bookIds.size() > 1) {
            log.info("Books removed: {}", bookIds);
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
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
        if (libraryFile.getBookFileType() == BookFileType.PDF) {
            return pdfProcessor.processFile(libraryFile, false);
        } else if (libraryFile.getBookFileType() == BookFileType.EPUB) {
            return epubProcessor.processFile(libraryFile, false);
        } else if (libraryFile.getBookFileType() == BookFileType.CBX) {
            return cbxProcessor.processFile(libraryFile, false);
        }
        return null;
    }

    private List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> libraryFiles = new ArrayList<>();
        for (LibraryPathEntity libraryPathEntity : libraryEntity.getLibraryPaths()) {
            libraryFiles.addAll(findLibraryFiles(libraryPathEntity, libraryEntity));
        }
        return libraryFiles;
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity libraryPathEntity, LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> libraryFiles = new ArrayList<>();
        Path libraryPath = Path.of(libraryPathEntity.getPath());
        try (var stream = Files.walk(libraryPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> {
                        String fileName = file.getFileName().toString().toLowerCase();
                        if (fileName.startsWith(".")) {
                            return false;
                        }
                        return fileName.endsWith(".pdf") ||
                                fileName.endsWith(".epub") ||
                                fileName.endsWith(".cbz") ||
                                fileName.endsWith(".cbr") ||
                                fileName.endsWith(".cb7");
                    })
                    .forEach(fullFilePath -> {
                        String fileName = fullFilePath.getFileName().toString().toLowerCase();
                        BookFileType fileType;
                        if (fileName.endsWith(".pdf")) {
                            fileType = BookFileType.PDF;
                        } else if (fileName.endsWith(".epub")) {
                            fileType = BookFileType.EPUB;
                        } else if (fileName.endsWith(".cbz")) {
                            fileType = BookFileType.CBX;
                        } else if (fileName.endsWith(".cbr")) {
                            fileType = BookFileType.CBX;
                        } else if (fileName.endsWith(".cb7")) {
                            fileType = BookFileType.CBX;
                        } else {
                            return;
                        }

                        String relativePath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), fullFilePath);
                        LibraryFile libraryFile = LibraryFile.builder()
                                .libraryEntity(libraryEntity)
                                .libraryPathEntity(libraryPathEntity)
                                .fileSubPath(relativePath)
                                .fileName(fullFilePath.getFileName().toString())
                                .bookFileType(fileType)
                                .build();
                        libraryFiles.add(libraryFile);
                    });
        }
        return libraryFiles;
    }

    public boolean isRelevantBookFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf")
                || name.endsWith(".epub")
                || name.endsWith(".cbz")
                || name.endsWith(".cbr")
                || name.endsWith(".cb7");
    }
}