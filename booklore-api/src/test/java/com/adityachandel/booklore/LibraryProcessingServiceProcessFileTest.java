package com.adityachandel.booklore;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceProcessFileTest {

    @InjectMocks
    private LibraryProcessingService fileProcessorService;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private BookRepository bookRepository;

    private final long LIBRARY_ID = 1L;
    private final String LIBRARY_PATH = "/mnt/books";
    private final String FILE_PATH = "/mnt/books/Fiction/book.epub";
    private final Path path = Paths.get(FILE_PATH);
    private LibraryEntity mockLibrary;

    @BeforeEach
    void setUp() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setPath("/mnt/books");

        mockLibrary = new LibraryEntity();
        mockLibrary.setId(LIBRARY_ID);
        mockLibrary.setLibraryPaths(List.of(pathEntity));
    }

    @Test
    void shouldProcessBookFileCreation() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenReturn(false);

            LibraryProcessingService spyService = spy(fileProcessorService);
            doReturn(true).when(spyService).isRelevantBookFile(path);
            doNothing().when(spyService).processFileCreate(any(), any(), any());

            spyService.processFile(StandardWatchEventKinds.ENTRY_CREATE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService).processFileCreate(eq(mockLibrary), eq(LIBRARY_PATH), eq(path));
        }
    }

    @Test
    void shouldProcessFolderCreation() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenReturn(true);

            LibraryProcessingService spyService = spy(fileProcessorService);
            doNothing().when(spyService).processFolderCreate(any(), any(), any());

            spyService.processFile(StandardWatchEventKinds.ENTRY_CREATE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService).processFolderCreate(eq(mockLibrary), eq(LIBRARY_PATH), eq(path));
        }
    }

    @Test
    void shouldIgnoreNonBookFile() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenReturn(false);

            LibraryProcessingService spyService = spy(fileProcessorService);
            doReturn(false).when(spyService).isRelevantBookFile(path);

            spyService.processFile(StandardWatchEventKinds.ENTRY_CREATE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService, never()).processFileCreate(any(), any(), any());
            verify(spyService, never()).processFolderCreate(any(), any(), any());
        }
    }

    @Test
    void shouldProcessBookFileDeletion() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        LibraryProcessingService spyService = spy(fileProcessorService);
        doReturn(true).when(spyService).isRelevantBookFile(path);
        doNothing().when(spyService).processFileDelete(any(), any());

        spyService.processFile(StandardWatchEventKinds.ENTRY_DELETE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

        verify(spyService).processFileDelete(eq(mockLibrary), eq(path));
    }

    @Test
    void shouldProcessHeuristicFolderDeletion() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));
        when(bookRepository.findBookByFileNameAndLibraryId("book.epub", LIBRARY_ID)).thenReturn(Optional.empty());

        LibraryProcessingService spyService = spy(fileProcessorService);
        doReturn(false).when(spyService).isRelevantBookFile(path);
        doNothing().when(spyService).processFolderDelete(any(), any());

        spyService.processFile(StandardWatchEventKinds.ENTRY_DELETE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

        verify(spyService).processFolderDelete(eq(mockLibrary), eq(path));
    }

    @Test
    void shouldProcessBookFileDeletionWhenKnownFile() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));
        when(bookRepository.findBookByFileNameAndLibraryId("book.epub", LIBRARY_ID)).thenReturn(Optional.of(mock())); // known file

        LibraryProcessingService spyService = spy(fileProcessorService);
        doReturn(true).when(spyService).isRelevantBookFile(path);
        doNothing().when(spyService).processFileDelete(any(), any());

        spyService.processFile(StandardWatchEventKinds.ENTRY_DELETE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

        verify(spyService).processFileDelete(eq(mockLibrary), eq(path));
    }

    @Test
    void shouldFallbackToFileWhenFilesThrowsException() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenThrow(new RuntimeException("IO failed"));

            LibraryProcessingService spyService = spy(fileProcessorService);
            doReturn(true).when(spyService).isRelevantBookFile(path);
            doNothing().when(spyService).processFileCreate(any(), any(), any());

            spyService.processFile(StandardWatchEventKinds.ENTRY_CREATE, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService).processFileCreate(eq(mockLibrary), eq(LIBRARY_PATH), eq(path));
        }
    }

    @Test
    void shouldIgnoreUnknownEventType() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenReturn(false);

            LibraryProcessingService spyService = spy(fileProcessorService);
            doReturn(true).when(spyService).isRelevantBookFile(path);

            spyService.processFile(StandardWatchEventKinds.ENTRY_MODIFY, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService, never()).processFileCreate(any(), any(), any());
            verify(spyService, never()).processFileDelete(any(), any());
        }
    }

    @Test
    void shouldIgnoreUnknownEventOnDirectory() {
        
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(path)).thenReturn(true);

            LibraryProcessingService spyService = spy(fileProcessorService);

            spyService.processFile(StandardWatchEventKinds.ENTRY_MODIFY, LIBRARY_ID, LIBRARY_PATH, FILE_PATH);

            verify(spyService, never()).processFolderCreate(any(), any(), any());
            verify(spyService, never()).processFolderDelete(any(), any());
        }
    }

    @Test
    void shouldNotInferFolderIfPathNotUnderLibrary() {
        
        String unrelatedPath = "/other/mount/book.epub";
        Path unrelated = Paths.get(unrelatedPath);
        when(libraryRepository.findById(LIBRARY_ID)).thenReturn(Optional.of(mockLibrary));
        when(bookRepository.findBookByFileNameAndLibraryId("book.epub", LIBRARY_ID)).thenReturn(Optional.empty());

        LibraryProcessingService spyService = spy(fileProcessorService);
        doReturn(false).when(spyService).isRelevantBookFile(unrelated);
        
        spyService.processFile(StandardWatchEventKinds.ENTRY_DELETE, LIBRARY_ID, LIBRARY_PATH, unrelatedPath);
        
        verify(spyService, never()).processFolderDelete(any(), any());
    }
}