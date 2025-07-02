package com.adityachandel.booklore;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceProcessFileCreateTest {

    @InjectMocks
    private LibraryProcessingService service;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EpubProcessor epubProcessor;

    private final Path testPath = Paths.get("/mnt/books/Fiction/book.epub");
    private final String fileHash = "abc123";

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setup() {
        libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/mnt/books");

        library = new LibraryEntity();
        library.setName("Main");
        library.setLibraryPaths(List.of(libraryPath));

        service.recentlyDeletedFiles.clear();
        service.recentlyDeletedTimestamps.clear();
        service.deletionTasks.clear();
    }

    @Test
    void shouldCancelScheduledDeletionAndUpdateExistingBookPath() {
        BookEntity book = new BookEntity();
        book.setFileSubPath("Old/path/book.epub");
        book.setLibraryPath(new LibraryPathEntity());

        try (MockedStatic<FileUtils> utilities = mockStatic(FileUtils.class)) {
            utilities.when(() -> FileUtils.computeFileHash(testPath)).thenReturn(fileHash);
            utilities.when(() -> FileUtils.getRelativeSubPath(anyString(), eq(testPath)))
                    .thenReturn("Fiction/book.epub");

            when(bookRepository.findByCurrentHash(fileHash)).thenReturn(Optional.of(book));
            when(entityManager.merge(any(LibraryPathEntity.class))).thenReturn(libraryPath);

            ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);

            service.deletionTasks.put(fileHash, mockFuture);

            service.processFileCreate(library, "/mnt/books", testPath);

            verify(bookRepository).save(book);
            verify(mockFuture).cancel(false);
            verify(notificationService, never()).sendMessage(eq(Topic.LOG), any());
        }
    }

    @Test
    void shouldIgnoreStaleRecentlyDeletedAndProcessFresh() {
        BookEntity book = new BookEntity();
        service.recentlyDeletedFiles.put(fileHash, book);
        service.recentlyDeletedTimestamps.put(fileHash, Instant.now().minusSeconds(999));

        try (MockedStatic<FileUtils> utilities = mockStatic(FileUtils.class)) {
            utilities.when(() -> FileUtils.computeFileHash(testPath)).thenReturn(fileHash);
            when(entityManager.merge(any())).thenReturn(libraryPath);

            service.processFileCreate(library, "/mnt/books", testPath);

            verify(bookRepository, never()).save(book);

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                return ((LogNotification) arg).getMessage().contains("Started processing file");
            }));

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                return ((LogNotification) arg).getMessage().contains("Finished processing file");
            }));
        }
    }

    @Test
    void shouldProcessFreshFileWhenHashIsNull() {
        try (MockedStatic<FileUtils> mockedUtils = mockStatic(FileUtils.class)) {
            mockedUtils.when(() -> FileUtils.computeFileHash(testPath)).thenReturn(null);
            when(entityManager.merge(any())).thenReturn(libraryPath);

            service.processFileCreate(library, "/mnt/books", testPath);

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                LogNotification log = (LogNotification) arg;
                return log.getMessage() != null && log.getMessage().contains("Started processing file");
            }));

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                LogNotification log = (LogNotification) arg;
                return log.getMessage() != null && log.getMessage().contains("Finished processing file");
            }));
        }
    }

    @Test
    void shouldCleanUpIfNoMatchFound() {
        try (MockedStatic<FileUtils> mockedUtils = mockStatic(FileUtils.class)) {
            mockedUtils.when(() -> FileUtils.computeFileHash(testPath)).thenReturn(fileHash);

            service.recentlyDeletedFiles.put(fileHash, new BookEntity());
            service.recentlyDeletedTimestamps.put(fileHash, Instant.now());

            when(bookRepository.findByCurrentHash(fileHash)).thenReturn(Optional.empty());
            when(entityManager.merge(any())).thenReturn(libraryPath);

            service.processFileCreate(library, "/mnt/books", testPath);

            assertThatNoException().isThrownBy(() ->
                    service.processFileCreate(library, "/mnt/books", testPath));

            InOrder inOrder = inOrder(notificationService);

            inOrder.verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                return ((LogNotification) arg).getMessage().contains("Started processing file");
            }));

            inOrder.verify(notificationService).sendMessage(eq(Topic.LOG), argThat(arg -> {
                if (!(arg instanceof LogNotification)) return false;
                return ((LogNotification) arg).getMessage().contains("Finished processing file");
            }));

            assertThat(service.recentlyDeletedFiles).doesNotContainKey(fileHash);
            assertThat(service.recentlyDeletedTimestamps).doesNotContainKey(fileHash);
        }
    }
}