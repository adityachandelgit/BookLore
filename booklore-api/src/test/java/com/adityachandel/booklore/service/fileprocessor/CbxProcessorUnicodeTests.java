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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbxProcessorUnicodeTests {

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

    private CbxProcessor cbxProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cbxProcessor = new CbxProcessor(
                bookRepository,
                bookCreatorService,
                bookMapper,
                fileProcessingUtils,
                bookMetadataRepository,
                metadataMatchService
        );
    }

    @Test
    void processFileWithUnicodeFilename_shouldUseFullPath() throws IOException {
        // Given: A CBZ file with unicode characters in filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Comic.cbz";
        Path unicodeFile = createMockCbzFile(unicodeFilename);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.CBX)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.CBX))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = cbxProcessor.processFile(libraryFile, false);

        // Then: Should use full path and process successfully
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.CBX);
        verify(bookRepository).save(mockBookEntity);
        verify(bookRepository).flush();
    }

    @Test
    void processFileWithVariousUnicodeArchiveFormats_shouldNotThrowException() throws IOException {
        // Given: Various unicode CBX filenames with different formats
        String[] unicodeFilenames = {
                "普通话漫画.cbz",                     // Chinese CBZ
                "Japonés básico 日本語.cbr",          // Spanish + Japanese CBR
                "Русский комикс.cb7",                // Russian CB7
                "العربية كوميكس.cbz",               // Arabic CBZ
                "हिंदी कॉमिक.cbr",                  // Hindi CBR
                "한국어 만화.cb7",                   // Korean CB7
                "Ελληνικά κόμικς.cbz",              // Greek CBZ
                "עברית קומיקס.cbr"                  // Hebrew CBR
        };

        for (String filename : unicodeFilenames) {
            // Create mock archive file based on extension
            Path unicodeFile;
            if (filename.endsWith(".cbz")) {
                unicodeFile = createMockCbzFile(filename);
            } else {
                // For .cbr and .cb7, just create empty files since we're testing filename handling
                unicodeFile = tempDir.resolve(filename);
                Files.createFile(unicodeFile);
            }

            LibraryEntity libraryEntity = createMockLibraryEntity();
            LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
            
            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath("")
                    .fileName(filename)
                    .bookFileType(BookFileType.CBX)
                    .build();

            BookEntity mockBookEntity = createMockBookEntity();
            when(bookRepository.findBookByFileNameAndLibraryId(filename, 1L))
                    .thenReturn(Optional.empty());
            when(bookCreatorService.createShellBook(libraryFile, BookFileType.CBX))
                    .thenReturn(mockBookEntity);
            when(metadataMatchService.calculateMatchScore(mockBookEntity))
                    .thenReturn(0.8f);
            when(bookRepository.save(mockBookEntity))
                    .thenReturn(mockBookEntity);

            // When & Then: Should not throw any exception
            assertThatCode(() -> cbxProcessor.processFile(libraryFile, false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void processFileWithUnicodeFilename_forceProcess_shouldProcessEvenIfExists() throws IOException {
        // Given: A CBX file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Comic.cbz";
        Path unicodeFile = createMockCbzFile(unicodeFilename);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.CBX)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        BookEntity newBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.CBX))
                .thenReturn(newBookEntity);
        when(metadataMatchService.calculateMatchScore(newBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(newBookEntity))
                .thenReturn(newBookEntity);
        when(bookMapper.toBook(newBookEntity))
                .thenReturn(mockBook);

        // When: Force processing the file
        Book result = cbxProcessor.processFile(libraryFile, true);

        // Then: Should process new file even though one exists
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.CBX);
        verify(bookRepository).save(newBookEntity);
    }

    @Test
    void processFileWithUnicodeFilename_existingFile_shouldReturnExisting() throws IOException {
        // Given: A CBX file with unicode characters that already exists in repository
        String unicodeFilename = "Integrated Chinese 中文听说读写 Comic.cbz";
        Path unicodeFile = createMockCbzFile(unicodeFilename);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.CBX)
                .build();

        BookEntity existingBookEntity = createMockBookEntity();
        Book existingBook = createMockBook();

        // Mock that file exists in repository
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.of(existingBookEntity));
        when(bookMapper.toBook(existingBookEntity))
                .thenReturn(existingBook);

        // When: Processing the file (not forced)
        Book result = cbxProcessor.processFile(libraryFile, false);

        // Then: Should return existing book without processing
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingBook);
        verify(bookCreatorService, never()).createShellBook(any(), any());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void processFileWithUnicodeFilename_withUnicodeArchiveEntries_shouldHandle() throws IOException {
        // Given: A CBZ file with unicode filename containing unicode entries
        String unicodeFilename = "中文漫画合集.cbz";
        Path unicodeFile = createCbzFileWithUnicodeEntries(unicodeFilename);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.CBX)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.CBX))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = cbxProcessor.processFile(libraryFile, false);

        // Then: Should process successfully even with unicode archive entries
        assertThat(result).isNotNull();
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.CBX);
        assertThat(libraryFile.getFullPath().toString()).endsWith(unicodeFilename);
    }

    @Test
    void processFileWithUnicodeFilename_withNestedDirectories_shouldUseCorrectFullPath() throws IOException {
        // Given: A CBX file with unicode filename in nested unicode directories
        String unicodeDir = "中文漫画";
        String unicodeSubDir = "教育类";
        String unicodeFilename = "中文学习漫画.cbz";
        
        Path nestedDir = tempDir.resolve(unicodeDir).resolve(unicodeSubDir);
        Files.createDirectories(nestedDir);
        Path unicodeFile = createMockCbzFileInDirectory(nestedDir, unicodeFilename);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity();
        
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(unicodeDir + "/" + unicodeSubDir)
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.CBX)
                .build();

        BookEntity mockBookEntity = createMockBookEntity();
        Book mockBook = createMockBook();

        // Mock repository calls
        when(bookRepository.findBookByFileNameAndLibraryId(unicodeFilename, 1L))
                .thenReturn(Optional.empty());
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.CBX))
                .thenReturn(mockBookEntity);
        when(metadataMatchService.calculateMatchScore(mockBookEntity))
                .thenReturn(0.8f);
        when(bookRepository.save(mockBookEntity))
                .thenReturn(mockBookEntity);
        when(bookMapper.toBook(mockBookEntity))
                .thenReturn(mockBook);

        // When: Processing the file
        Book result = cbxProcessor.processFile(libraryFile, false);

        // Then: Should process successfully with correct full path
        assertThat(result).isNotNull();
        assertThat(libraryFile.getFullPath().toString()).endsWith(unicodeFilename);
        verify(bookCreatorService).createShellBook(libraryFile, BookFileType.CBX);
    }

    private Path createMockCbzFile(String filename) throws IOException {
        return createMockCbzFileInDirectory(tempDir, filename);
    }

    private Path createMockCbzFileInDirectory(Path directory, String filename) throws IOException {
        Path zipFile = directory.resolve(filename);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            // Add a simple image entry
            ZipEntry entry = new ZipEntry("page01.jpg");
            zos.putNextEntry(entry);
            zos.write("mock image data".getBytes());
            zos.closeEntry();
            
            zos.finish();
            Files.write(zipFile, baos.toByteArray());
        }
        
        return zipFile;
    }

    private Path createCbzFileWithUnicodeEntries(String filename) throws IOException {
        Path zipFile = tempDir.resolve(filename);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            // Add entries with unicode names
            String[] unicodeEntries = {
                    "第01页.jpg",
                    "第02页.jpg", 
                    "페이지_01.png",
                    "страница_01.webp"
            };
            
            for (String entryName : unicodeEntries) {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write("mock image data".getBytes());
                zos.closeEntry();
            }
            
            zos.finish();
            Files.write(zipFile, baos.toByteArray());
        }
        
        return zipFile;
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
        bookEntity.setFileName("test.cbz");
        
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Comic");
        bookEntity.setMetadata(metadata);
        
        return bookEntity;
    }

    private Book createMockBook() {
        return Book.builder()
                .id(1L)
                .fileName("test.cbz")
                .build();
    }
} 