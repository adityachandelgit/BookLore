package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfProcessorUnicodeTests {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookCreatorService bookCreatorService;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private FileProcessingUtils fileProcessingUtils;

    @Mock
    private BookMetadataRepository bookMetadataRepository;

    @Mock
    private MetadataMatchService metadataMatchService;

    @Mock
    private PdfMetadataExtractor pdfMetadataExtractor;

    private PdfProcessor pdfProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfProcessor = new PdfProcessor(
                bookRepository,
                bookCreatorService,
                bookMapper,
                fileProcessingUtils,
                bookMetadataRepository,
                metadataMatchService,
                pdfMetadataExtractor
        );
    }

    @Test
    void processFileWithUnicodeFilename_shouldUseFullPath() throws IOException {
        // Given: A file with unicode characters in filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.PDF))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = pdfProcessor.processFile(libraryFile, false);

        // Then: Should use full path and process successfully
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.PDF);
        verify(bookRepository).save(mockBookEntity);
        verify(bookRepository).flush();
    }

    @Test
    void processFileWithUnicodeFilename_shouldNotThrowException() throws IOException {
        // Given: Various unicode filenames
        String[] unicodeFilenames = {
                "普通话教程.pdf",                    // Chinese
                "Japonés básico 日本語.pdf",         // Spanish + Japanese
                "Русский язык учебник.pdf",          // Russian
                "العربية للمبتدئين.pdf",            // Arabic
                "हिंदी भाषा सीखें.pdf",              // Hindi
                "한국어 기초.pdf"                     // Korean
        };

        for (String filename : unicodeFilenames) {
            Path unicodeFile = tempDir.resolve(filename);
            Files.createFile(unicodeFile);

            LibraryEntity libraryEntity = createMockLibraryEntity();
            LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
            
            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath("")
                    .fileName(filename)
                    .bookFileType(BookFileType.PDF)
                    .build();

            BookEntity mockBookEntity = createMockBookEntity();
            when(bookRepository.findBookByFileNameAndLibraryId(filename, 1L))
                    .thenReturn(Optional.empty());
            when(bookCreatorService.createShellBook(libraryFile, BookFileType.PDF))
                    .thenReturn(mockBookEntity);
            when(metadataMatchService.calculateMatchScore(mockBookEntity))
                    .thenReturn(0.8f);
            when(bookRepository.save(mockBookEntity))
                    .thenReturn(mockBookEntity);

            // When & Then: Should not throw any exception
            assertThatCode(() -> pdfProcessor.processFile(libraryFile, false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void processFileWithUnicodeFilename_forceProcess_shouldProcessEvenIfExists() throws IOException {
        // Given: A file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        BookEntity newBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.PDF))
                .thenReturn(newBookEntity);
        when(metadataMatchService.calculateMatchScore(newBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(newBookEntity))
                .thenReturn(newBookEntity);
        when(bookMapper.toBook(newBookEntity))
                .thenReturn(mockBook);

        // When: Force processing the file
        Book result = pdfProcessor.processFile(libraryFile, true);

        // Then: Should process new file even though one exists
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.PDF);
        verify(bookRepository).save(newBookEntity);
    }

    @Test
    void processFileWithUnicodeFilename_existingFile_shouldReturnExisting() throws IOException {
        // Given: A file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        Book existingBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookMapper.toBook(existingBookEntity))
                .thenReturn(existingBook);

        // When: Processing the file (not forced)
        Book result = pdfProcessor.processFile(libraryFile, false);

        // Then: Should return existing book without processing
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingBook);
        verify(bookCreatorService, never()).createShellBook(any(), any());
        verify(bookRepository, never()).save(any());
    }

    private LibraryEntity createMockLibraryEntity() {
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(1L);
        libraryEntity.setName("Test Library");
        return libraryEntity;
    }

    private LibraryPathEntity createMockLibraryPathEntity() {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(tempDir.toString());
        return libraryPathEntity;
    }

    private BookEntity createMockBookEntity() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        bookEntity.setFileName("test.pdf");
        
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        bookEntity.setMetadata(metadata);
        
        return bookEntity;
    }

    private Book createMockBook() {
        return Book.builder()
                .id(1L)
                .fileName("test.pdf")
                .build();
    }
} 