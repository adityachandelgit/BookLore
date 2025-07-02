package com.adityachandel.booklore;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import com.adityachandel.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.StandardWatchEventKinds;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LibraryProcessingServiceTest {

    @InjectMocks
    private LibraryProcessingService service;

    @Mock private LibraryRepository libraryRepository;
    @Mock private NotificationService notificationService;
    @Mock private PdfProcessor pdfProcessor;
    @Mock private EpubProcessor epubProcessor;
    @Mock private CbxProcessor cbxProcessor;
    @Mock private BookRepository bookRepository;
    @Mock private EntityManager entityManager;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private FileService fileService;

    private final long libraryId = 1L;
    private final String filePath = "/books/sample.pdf";
    private final String fileName = "sample.pdf";
    private final String libraryPath = "/books";

    private LibraryEntity libraryEntity;
    private LibraryPathEntity libraryPathEntity;

    @BeforeEach
    void setup() {
        libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);

        libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath(libraryPath);
        libraryEntity.setLibraryPaths(List.of(libraryPathEntity));
    }

    @Test
    void testEntryDelete_SchedulesDebounceAndStaging() {
        BookEntity book = new BookEntity();
        book.setId(123L);
        book.setFileName(fileName);

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(bookRepository.findBookByFileNameAndLibraryId(fileName, libraryId)).thenReturn(Optional.of(book));

        service.processFile(StandardWatchEventKinds.ENTRY_DELETE, libraryId, libraryPath, filePath);

        assertThat(service.recentlyDeletedFiles).containsKey(filePath);
        assertThat(service.recentlyDeletedTimestamps).containsKey(filePath);
    }

    @Test
    void testEntryCreate_CancelsScheduledDeleteAndProcessesNewFile() {
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        service.deletionTasks.put(filePath, mockFuture);

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(entityManager.merge(any())).thenReturn(libraryPathEntity);
        when(pdfProcessor.processFile(any(), eq(false))).thenReturn(Book.builder().build());

        service.processFile(StandardWatchEventKinds.ENTRY_CREATE, libraryId, libraryPath, filePath);

        verify(mockFuture).cancel(false);

        verify(notificationService, atLeastOnce()).sendMessage(
                eq(Topic.LOG),
                argThat(arg -> arg instanceof com.adityachandel.booklore.model.websocket.LogNotification ln &&
                        ln.getMessage().contains("Started processing file"))
        );
    }

    @Test
    void testEntryCreate_DetectsMoveAndUpdatesBook() {
        BookEntity book = new BookEntity();
        book.setFileName(fileName);
        service.recentlyDeletedFiles.put(filePath, book);
        service.recentlyDeletedTimestamps.put(filePath, Instant.now());

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(entityManager.merge(any())).thenReturn(libraryPathEntity);

        service.processFile(StandardWatchEventKinds.ENTRY_CREATE, libraryId, libraryPath, filePath);

        verify(bookRepository).save(book);
        assertThat(service.recentlyDeletedFiles).doesNotContainKey(filePath);
    }

    @Test
    void testEntryDelete_SkipsIfAlreadyScheduled() {
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        service.deletionTasks.put(filePath, scheduled);

        BookEntity book = new BookEntity();
        book.setFileName(fileName);

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(bookRepository.findBookByFileNameAndLibraryId(fileName, libraryId)).thenReturn(Optional.of(book));

        service.processFile(StandardWatchEventKinds.ENTRY_DELETE, libraryId, libraryPath, filePath);

        assertThat(service.deletionTasks).containsKey(filePath);
        assertThat(service.recentlyDeletedFiles).containsKey(filePath);
    }

    @Test
    void testEntryCreate_ThrowsIfLibraryNotFound() {
        when(libraryRepository.findById(libraryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processFile(StandardWatchEventKinds.ENTRY_CREATE, libraryId, libraryPath, filePath))
                .isInstanceOf(RuntimeException.class) // or ApiException if custom
                .hasMessageContaining("Library not found");
    }

    @Test
    void testEntryCreate_MoveNotDetectedDueToStaleTimestamp() {
        BookEntity book = new BookEntity();
        book.setFileName(fileName);
        service.recentlyDeletedFiles.put(filePath, book);
        service.recentlyDeletedTimestamps.put(filePath, Instant.now().minusSeconds(1000)); // stale

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(entityManager.merge(any())).thenReturn(libraryPathEntity);
        when(pdfProcessor.processFile(any(), eq(false))).thenReturn(Book.builder().build());

        service.processFile(StandardWatchEventKinds.ENTRY_CREATE, libraryId, libraryPath, filePath);

        verify(bookRepository, never()).save(any());
        verify(notificationService, atLeastOnce()).sendMessage(eq(Topic.LOG), any());
    }

    @Test
    void testEntryDelete_DuplicateDeleteEventDoesNotReschedule() {
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        service.deletionTasks.put(filePath, scheduled);

        BookEntity book = new BookEntity();
        book.setFileName(fileName);

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(bookRepository.findBookByFileNameAndLibraryId(fileName, libraryId)).thenReturn(Optional.of(book));

        service.processFile(StandardWatchEventKinds.ENTRY_DELETE, libraryId, libraryPath, filePath);

        ScheduledFuture<?> result = service.deletionTasks.get(filePath);
        assertThat((Object) result).isSameAs(scheduled);
    }
}