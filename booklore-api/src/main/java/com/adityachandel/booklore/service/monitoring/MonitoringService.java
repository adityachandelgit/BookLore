package com.adityachandel.booklore.service.monitoring;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.service.LibraryFileEventProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MonitoringService {

    private final LibraryFileEventProcessor libraryFileEventProcessor;
    private final WatchService watchService;
    private final MonitoringTask monitoringTask;

    public final Set<Path> monitoredPaths = ConcurrentHashMap.newKeySet();
    private final Map<Path, Long> pathToLibraryIdMap = new ConcurrentHashMap<>();

    private final BlockingQueue<FileChangeEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    public MonitoringService(LibraryFileEventProcessor libraryFileEventProcessor, WatchService watchService, MonitoringTask monitoringTask) {
        this.libraryFileEventProcessor = libraryFileEventProcessor;
        this.watchService = watchService;
        this.monitoringTask = monitoringTask;
    }

    @PostConstruct
    public void initializeMonitoring() {
        monitoringTask.monitor();
        startProcessingThread();
    }

    public void registerLibrariesForMonitoring(List<Library> libraries) {
        libraries.stream()
                .filter(Library::isWatch)
                .forEach(library -> {
                    int[] registeredCount = {0};

                    library.getPaths().forEach(libraryPath -> {
                        Path rootPath = Paths.get(libraryPath.getPath());
                        if (Files.isDirectory(rootPath)) {
                            try (Stream<Path> pathStream = Files.walk(rootPath)) {
                                pathStream
                                        .filter(Files::isDirectory)
                                        .forEach(path -> {
                                            if (registerPath(path, library.getId())) {
                                                registeredCount[0]++;
                                            }
                                        });
                            } catch (IOException e) {
                                log.error("Failed to register paths for library '{}': {}", library.getName(), e.getMessage(), e);
                            }
                        }
                    });
                    log.info("Registered {} folders for library '{}'", registeredCount[0], library.getName());
                });

        log.info("Registered {} libraries for recursive monitoring", libraries.size());
    }

    public synchronized boolean registerPath(Path path, Long libraryId) {
        try {
            if (monitoredPaths.add(path)) {
                path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                pathToLibraryIdMap.put(path, libraryId);
                return true;
            }
        } catch (IOException e) {
            log.error("Error registering path: {}", path, e);
        }
        return false;
    }

    public synchronized void unregisterPath(Path path) {
        if (monitoredPaths.remove(path)) {
            pathToLibraryIdMap.remove(path);
            log.info("Unregistered path: {}", path);
        }
    }

    private void unregisterSubPaths(Path deletedPath) {
        Set<Path> toRemove = monitoredPaths.stream()
                .filter(p -> p.startsWith(deletedPath))
                .collect(Collectors.toSet());

        for (Path path : toRemove) {
            unregisterPath(path);
        }
    }

    @EventListener
    public void handleFileChangeEvent(FileChangeEvent event) {
        Path fullPath = event.getFilePath();
        WatchEvent.Kind<?> kind = event.getEventKind();

        if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_DELETE) {
            return;
        }

        boolean isDir = kind == StandardWatchEventKinds.ENTRY_CREATE
                ? Files.isDirectory(fullPath)
                : monitoredPaths.contains(fullPath);

        boolean isRelevantFile = isRelevantBookFile(fullPath);

        if (!(isDir || isRelevantFile)) {
            return;
        }

        if (isDir && kind == StandardWatchEventKinds.ENTRY_CREATE) {
            Long parentLibraryId = pathToLibraryIdMap.get(event.getWatchedFolder());
            if (parentLibraryId != null) {
                try (Stream<Path> stream = Files.walk(fullPath)) {
                    stream.filter(Files::isDirectory).forEach(path -> registerPath(path, parentLibraryId));
                } catch (IOException e) {
                    log.warn("Failed to register nested paths: {}", fullPath, e);
                }
            }
        }

        if (isDir && kind == StandardWatchEventKinds.ENTRY_DELETE) {
            log.info("Unregistered path: {}", fullPath);
            unregisterSubPaths(fullPath);
        }

        if (!eventQueue.offer(event)) {
            log.warn("Event queue full, dropping: {}", fullPath);
        } else {
            log.debug("Queued: {} [{}]", fullPath, kind.name());
        }
    }

    private void startProcessingThread() {
        log.info("Starting file change processor...");
        singleThreadExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    FileChangeEvent event = eventQueue.take();
                    processFileChangeEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in processing thread", e);
                }
            }
        });
    }

    private void processFileChangeEvent(FileChangeEvent event) {
        Path filePath = event.getFilePath();
        Path watchedFolder = event.getWatchedFolder();
        Long libraryId = pathToLibraryIdMap.get(watchedFolder);

        if (libraryId != null) {
            try {
                libraryFileEventProcessor.processFile(event.getEventKind(), libraryId, watchedFolder.toString(), filePath.toString());
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException for libraryId={}", libraryId);
            }
        } else {
            log.warn("No library ID found for folder: {}", watchedFolder);
        }
    }

    @PreDestroy
    public void stopMonitoring() {
        log.info("Shutting down monitoring service...");
        singleThreadExecutor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("Failed to close WatchService", e);
        }
    }

    @EventListener
    public void handleWatchKeyInvalidation(WatchKeyInvalidatedEvent event) {
        Path invalidPath = event.getInvalidPath();
        if (monitoredPaths.contains(invalidPath)) {
            log.warn("Removing invalid path from monitoring: {}", invalidPath);
            monitoredPaths.remove(invalidPath);
            pathToLibraryIdMap.remove(invalidPath);
        }
    }


    private boolean isRelevantBookFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf")
                || name.endsWith(".epub")
                || name.endsWith(".cbz")
                || name.endsWith(".cbr")
                || name.endsWith(".cb7");
    }
}