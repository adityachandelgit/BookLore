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
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpubProcessorUnicodeTests {

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
    private EpubMetadataExtractor epubMetadataExtractor;

    private EpubProcessor epubProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        epubProcessor = new EpubProcessor(
                bookRepository,
                bookCreatorService,
                bookMapper,
                fileProcessingUtils,
                bookMetadataRepository,
                metadataMatchService,
                epubMetadataExtractor
        );
    }

    @Test
    void processFileWithUnicodeFilename_shouldUseFullPath() throws IOException {
        // Given: An EPUB file with unicode characters in filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.epub";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.EPUB)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = epubProcessor.processFile(libraryFile, false);

        // Then: Should use full path and process successfully
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.EPUB);
        verify(bookRepository).save(mockBookEntity);
        verify(bookRepository).flush();
    }

    @Test
    void processFileWithVariousUnicodeFilenames_shouldNotThrowException() throws IOException {
        // Given: Various unicode EPUB filenames
        String[] unicodeFilenames = {
                "普通话教程.epub",                    // Chinese
                "Japonés básico 日本語.epub",         // Spanish + Japanese
                "Русский язык учебник.epub",          // Russian
                "العربية للمبتدئين.epub",            // Arabic
                "हिंदी भाषा सीखें.epub",              // Hindi
                "한국어 기초.epub",                   // Korean
                "Ελληνικά για αρχάριους.epub",       // Greek
                "עברית למתחילים.epub"                // Hebrew
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
                    .bookFileType(BookFileType.EPUB)
                    .build();

            BookEntity mockBookEntity = createMockBookEntity();
            when(bookRepository.findBookByFileNameAndLibraryId(filename, 1L))
                    .thenReturn(Optional.empty());
            when(bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB))
                    .thenReturn(mockBookEntity);
            when(metadataMatchService.calculateMatchScore(mockBookEntity))
                    .thenReturn(0.8f);
            when(bookRepository.save(mockBookEntity))
                    .thenReturn(mockBookEntity);

            // When & Then: Should not throw any exception
            assertThatCode(() -> epubProcessor.processFile(libraryFile, false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void processFileWithUnicodeFilename_forceProcess_shouldProcessEvenIfExists() throws IOException {
        // Given: An EPUB file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.epub";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.EPUB)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        BookEntity newBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB))
                .thenReturn(newBookEntity);
        when(metadataMatchService.calculateMatchScore(newBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(newBookEntity))
                .thenReturn(newBookEntity);
        when(bookMapper.toBook(newBookEntity))
                .thenReturn(mockBook);

        // When: Force processing the file
        Book result = epubProcessor.processFile(libraryFile, true);

        // Then: Should process new file even though one exists
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.EPUB);
        verify(bookRepository).save(newBookEntity);
    }

    @Test
    void processFileWithUnicodeFilename_existingFile_shouldReturnExisting() throws IOException {
        // Given: An EPUB file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.epub";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.EPUB)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        Book existingBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookMapper.toBook(existingBookEntity))
                .thenReturn(existingBook);

        // When: Processing the file (not forced)
        Book result = epubProcessor.processFile(libraryFile, false);

        // Then: Should return existing book without processing
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingBook);
        verify(bookCreatorService, never()).createShellBook(any(), any());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void processFileWithUnicodeFilename_withNestedDirectories_shouldUseCorrectFullPath() throws IOException {
        // Given: An EPUB file with unicode filename in nested unicode directories
        String unicodeDir = "中文书籍";
        String unicodeSubDir = "教育类";
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.epub";
        
        Path nestedDir = tempDir.resolve(unicodeDir).resolve(unicodeSubDir);
        Files.createDirectories(nestedDir);
        Path unicodeFile = nestedDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(unicodeDir + "/" + unicodeSubDir)
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.EPUB)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = epubProcessor.processFile(libraryFile, false);

        // Then: Should process successfully with correct full path
        assertThat(result).isNotNull();
        assertThat(libraryFile.getFullPath().toString()).endsWith(unicodeFilename);
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.EPUB);
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
        bookEntity.setFileName("test.epub");
        
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        bookEntity.setMetadata(metadata);
        
        return bookEntity;
    }

    private Book createMockBook() {
        return Book.builder()
                .id(1L)
                .fileName("test.epub")
                .build();
    }
} 