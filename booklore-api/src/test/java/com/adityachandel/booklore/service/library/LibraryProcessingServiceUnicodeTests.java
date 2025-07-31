package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceUnicodeTests {

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PdfProcessor pdfProcessor;

    @Mock
    private EpubProcessor epubProcessor;

    @Mock
    private CbxProcessor cbxProcessor;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private FileService fileService;

    @Mock
    private BookQueryService bookQueryService;

    private LibraryProcessingService libraryProcessingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        libraryProcessingService = new LibraryProcessingService(
                libraryRepository,
                notificationService,
                pdfProcessor,
                epubProcessor,
                cbxProcessor,
                bookRepository,
                entityManager,
                transactionTemplate,
                fileService,
                bookQueryService
        );
    }

    @Test
    void init_shouldLogCharsetInformation() {
        // When: Initializing the service
        libraryProcessingService.init();

        // Then: Should log charset information for debugging unicode issues
        // Note: This test verifies that the init method can be called without throwing exceptions
        assertThatCode(() -> libraryProcessingService.init()).doesNotThrowAnyException();
        
        // Verify that the current charset is properly set
        assertThat(Charset.defaultCharset()).isNotNull();
    }

    @Test
    void processLibraryFile_withUnicodePdfFilename_shouldUsePdfProcessor() throws IOException {
        // Given: A LibraryFile with unicode PDF filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, BookFileType.PDF);
        Book mockBook = createMockBook();

        when(pdfProcessor.processFile(libraryFile, false)).thenReturn(mockBook);

        // When: Processing the library file
        Book result = libraryProcessingService.processLibraryFile(libraryFile);

        // Then: Should use PDF processor and return result
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockBook);
        verify(pdfProcessor).processFile(libraryFile, false);
        verifyNoInteractions(epubProcessor, cbxProcessor);
    }

    @Test
    void processLibraryFile_withUnicodeEpubFilename_shouldUseEpubProcessor() throws IOException {
        // Given: A LibraryFile with unicode EPUB filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.epub";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, BookFileType.EPUB);
        Book mockBook = createMockBook();

        when(epubProcessor.processFile(libraryFile, false)).thenReturn(mockBook);

        // When: Processing the library file
        Book result = libraryProcessingService.processLibraryFile(libraryFile);

        // Then: Should use EPUB processor and return result
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockBook);
        verify(epubProcessor).processFile(libraryFile, false);
        verifyNoInteractions(pdfProcessor, cbxProcessor);
    }

    @Test
    void processLibraryFile_withUnicodeCbxFilename_shouldUseCbxProcessor() throws IOException {
        // Given: A LibraryFile with unicode CBX filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Comic.cbz";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, BookFileType.CBX);
        Book mockBook = createMockBook();

        when(cbxProcessor.processFile(libraryFile, false)).thenReturn(mockBook);

        // When: Processing the library file
        Book result = libraryProcessingService.processLibraryFile(libraryFile);

        // Then: Should use CBX processor and return result
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockBook);
        verify(cbxProcessor).processFile(libraryFile, false);
        verifyNoInteractions(pdfProcessor, epubProcessor);
    }

    @Test
    void processLibraryFiles_withMultipleUnicodeFilenames_shouldProcessAll() throws IOException {
        // Given: Multiple files with different unicode filenames
        String[] unicodeFilenames = {
                "普通话教程.pdf",                    // Chinese PDF
                "Japonés básico 日本語.epub",        // Spanish + Japanese EPUB
                "Русский комикс.cbz",               // Russian CBZ
                "العربية للمبتدئين.pdf",            // Arabic PDF
                "한국어 기초.epub"                   // Korean EPUB
        };

        List<LibraryFile> libraryFiles = List.of(
                createMockLibraryFile(unicodeFilenames[0], BookFileType.PDF),
                createMockLibraryFile(unicodeFilenames[1], BookFileType.EPUB),
                createMockLibraryFile(unicodeFilenames[2], BookFileType.CBX),
                createMockLibraryFile(unicodeFilenames[3], BookFileType.PDF),
                createMockLibraryFile(unicodeFilenames[4], BookFileType.EPUB)
        );

        // Mock processor responses
        Book mockBook = createMockBook();
        when(pdfProcessor.processFile(any(), eq(false))).thenReturn(mockBook);
        when(epubProcessor.processFile(any(), eq(false))).thenReturn(mockBook);
        when(cbxProcessor.processFile(any(), eq(false))).thenReturn(mockBook);

        // When: Processing multiple files
        assertThatCode(() -> libraryProcessingService.processLibraryFiles(libraryFiles))
                .doesNotThrowAnyException();

        // Then: Should process all files successfully
        verify(pdfProcessor, times(2)).processFile(any(), eq(false));
        verify(epubProcessor, times(2)).processFile(any(), eq(false));
        verify(cbxProcessor, times(1)).processFile(any(), eq(false));
        verify(notificationService, times(5)).sendMessage(any(), any());
    }

    @Test
    void processLibraryFiles_withUnicodeFilename_shouldLogProcessingSteps() throws IOException {
        // Given: A single file with unicode filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, BookFileType.PDF);
        Book mockBook = createMockBook();

        when(pdfProcessor.processFile(libraryFile, false)).thenReturn(mockBook);

        // When: Processing the file
        libraryProcessingService.processLibraryFiles(List.of(libraryFile));

        // Then: Should process successfully and send notifications
        verify(pdfProcessor).processFile(libraryFile, false);
        verify(notificationService, times(2)).sendMessage(any(), any()); // BOOK_ADD + LOG
    }

    @Test
    void processLibraryFiles_withUnicodeFilename_shouldHandleProcessingErrors() throws IOException {
        // Given: A file with unicode filename that causes processing errors
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, BookFileType.PDF);

        when(pdfProcessor.processFile(libraryFile, false))
                .thenThrow(new RuntimeException("Unicode encoding error"));

        // When: Processing the file that throws an exception
        assertThatCode(() -> libraryProcessingService.processLibraryFiles(List.of(libraryFile)))
                .doesNotThrowAnyException();

        // Then: Should handle the error gracefully and still notify
        verify(pdfProcessor).processFile(libraryFile, false);
        verify(notificationService).sendMessage(any(), any()); // Error notification
    }

    @Test
    void getBookFileType_withUnicodeFilenames_shouldDetectCorrectTypes() {
        // Given: Various unicode filenames with different extensions
        String[] testCases = {
                "中文教程.pdf",
                "日本語基礎.epub", 
                "한국어漫画.cbz",
                "العربية_كتاب.cbr",
                "Русский_файл.cb7"
        };

        BookFileType[] expectedTypes = {
                BookFileType.PDF,
                BookFileType.EPUB,
                BookFileType.CBX,
                BookFileType.CBX,
                BookFileType.CBX
        };

        // When & Then: Should detect correct file types regardless of unicode characters
        for (int i = 0; i < testCases.length; i++) {
            BookFileType result = libraryProcessingService.getBookFileType(testCases[i]);
            assertThat(result).isEqualTo(expectedTypes[i]);
        }
    }

    @Test
    void processLibraryFiles_withNullProcessor_shouldReturnNull() throws IOException {
        // Given: A file with unsupported type
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.txt";
        LibraryFile libraryFile = createMockLibraryFile(unicodeFilename, null);

        // When: Processing the file
        Book result = libraryProcessingService.processLibraryFile(libraryFile);

        // Then: Should return null for unsupported file types
        assertThat(result).isNull();
        verifyNoInteractions(pdfProcessor, epubProcessor, cbxProcessor);
    }

    @Test
    void processLibraryFiles_withUnicodeFilename_shouldUseCorrectFullPath() throws IOException {
        // Given: A file with unicode filename and unicode directory path
        String unicodeDir = "中文书籍";
        String unicodeFilename = "中文教程.pdf";
        
        Path unicodeDirectory = tempDir.resolve(unicodeDir);
        Files.createDirectories(unicodeDirectory);
        Path unicodeFile = unicodeDirectory.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(unicodeDir)
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        Book mockBook = createMockBook();
        when(pdfProcessor.processFile(libraryFile, false)).thenReturn(mockBook);

        // When: Processing the file
        Book result = libraryProcessingService.processLibraryFile(libraryFile);

        // Then: Should use correct full path including unicode directories
        assertThat(result).isNotNull();
        assertThat(libraryFile.getFullPath().toString()).contains(unicodeDir);
        assertThat(libraryFile.getFullPath().toString()).endsWith(unicodeFilename);
        verify(pdfProcessor).processFile(libraryFile, false);
    }

    private LibraryFile createMockLibraryFile(String filename, BookFileType fileType) throws IOException {
        Path file = tempDir.resolve(filename);
        if (!Files.exists(file)) {
            Files.createFile(file);
        }

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();

        return LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(filename)
                .bookFileType(fileType)
                .build();
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

    private Book createMockBook() {
        return Book.builder()
                .id(1L)
                .fileName("test.pdf")
                .build();
    }
} 