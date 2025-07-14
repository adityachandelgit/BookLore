package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryFileUnicodeTests {

    @TempDir
    Path tempDir;

    @Test
    void getFullPath_withUnicodeFilename_shouldReturnCorrectPath() throws IOException {
        // Given: A LibraryFile with unicode filename
        String unicodeFilename = "Integrated Chinese 中文听说读写 Text.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        // When: Getting full path
        Path fullPath = libraryFile.getFullPath();

        // Then: Should return correct path with unicode characters
        assertThat(fullPath).isNotNull();
        assertThat(fullPath.toString()).endsWith(unicodeFilename);
        assertThat(fullPath.getFileName().toString()).isEqualTo(unicodeFilename);
        assertThat(Files.exists(fullPath)).isTrue();
    }

    @Test
    void getFullPath_withUnicodeDirectoryAndFilename_shouldReturnCorrectPath() throws IOException {
        // Given: Unicode directory and filename
        String unicodeDir = "中文书籍";
        String unicodeSubDir = "教育类";
        String unicodeFilename = "中文教程.pdf";

        Path nestedDir = tempDir.resolve(unicodeDir).resolve(unicodeSubDir);
        Files.createDirectories(nestedDir);
        Path unicodeFile = nestedDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(unicodeDir + "/" + unicodeSubDir)
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        // When: Getting full path
        Path fullPath = libraryFile.getFullPath();

        // Then: Should return correct path with unicode directories and filename
        assertThat(fullPath).isNotNull();
        assertThat(fullPath.toString()).contains(unicodeDir);
        assertThat(fullPath.toString()).contains(unicodeSubDir);
        assertThat(fullPath.toString()).endsWith(unicodeFilename);
        assertThat(fullPath.getFileName().toString()).isEqualTo(unicodeFilename);
        assertThat(Files.exists(fullPath)).isTrue();
    }

    @Test
    void getFullPath_withVariousUnicodeCharsets_shouldHandleAllCorrectly() throws IOException {
        // Given: Filenames with various unicode character sets
        String[] unicodeFilenames = {
                "普通话教程.pdf",                    // Chinese
                "Japonés básico 日本語.epub",        // Spanish + Japanese mixed
                "Русский язык учебник.cbz",          // Russian
                "العربية للمبتدئين.pdf",            // Arabic
                "हिंदी भाषा सीखें.epub",              // Hindi
                "한국어 기초.cbz",                   // Korean
                "Ελληνικά για αρχάριους.pdf",       // Greek
                "עברית למתחילים.epub",              // Hebrew
                "Türkçe öğrenme kitabı.pdf",        // Turkish
                "Português básico.cbz",             // Portuguese with accents
                "Français débutant naïve café.epub" // French with accents
        };

        BookFileType[] fileTypes = {
                BookFileType.PDF, BookFileType.EPUB, BookFileType.CBX,
                BookFileType.PDF, BookFileType.EPUB, BookFileType.CBX,
                BookFileType.PDF, BookFileType.EPUB, BookFileType.PDF,
                BookFileType.CBX, BookFileType.EPUB
        };

        for (int i = 0; i < unicodeFilenames.length; i++) {
            String filename = unicodeFilenames[i];
            Path unicodeFile = tempDir.resolve(filename);
            Files.createFile(unicodeFile);

            LibraryEntity libraryEntity = createMockLibraryEntity();
            LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath("")
                    .fileName(filename)
                    .bookFileType(fileTypes[i])
                    .build();

            // When: Getting full path
            Path fullPath = libraryFile.getFullPath();

            // Then: Should handle all unicode character sets correctly
            assertThat(fullPath).isNotNull();
            assertThat(fullPath.toString()).endsWith(filename);
            assertThat(fullPath.getFileName().toString()).isEqualTo(filename);
            assertThat(Files.exists(fullPath)).isTrue();
        }
    }

    @Test
    void getFullPath_withMixedEncodingInPath_shouldHandleCorrectly() throws IOException {
        // Given: Path with mixed ASCII and Unicode characters
        String asciiDir = "books";
        String unicodeDir = "中文书籍";
        String mixedFilename = "Learn Chinese 学中文 - Lesson 1.pdf";

        Path nestedDir = tempDir.resolve(asciiDir).resolve(unicodeDir);
        Files.createDirectories(nestedDir);
        Path mixedFile = nestedDir.resolve(mixedFilename);
        Files.createFile(mixedFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(asciiDir + "/" + unicodeDir)
                .fileName(mixedFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        // When: Getting full path
        Path fullPath = libraryFile.getFullPath();

        // Then: Should handle mixed encoding correctly
        assertThat(fullPath).isNotNull();
        assertThat(fullPath.toString()).contains(asciiDir);
        assertThat(fullPath.toString()).contains(unicodeDir);
        assertThat(fullPath.toString()).endsWith(mixedFilename);
        assertThat(fullPath.getFileName().toString()).isEqualTo(mixedFilename);
        assertThat(Files.exists(fullPath)).isTrue();
    }

    @Test
    void getFullPath_withEmptyFileSubPath_shouldReturnCorrectPath() throws IOException {
        // Given: LibraryFile with empty fileSubPath and unicode filename
        String unicodeFilename = "中文教程.pdf";
        Path unicodeFile = tempDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        // When: Getting full path
        Path fullPath = libraryFile.getFullPath();

        // Then: Should return path directly under library path
        assertThat(fullPath).isNotNull();
        assertThat(fullPath.getParent()).isEqualTo(tempDir);
        assertThat(fullPath.getFileName().toString()).isEqualTo(unicodeFilename);
        assertThat(Files.exists(fullPath)).isTrue();
    }

    @Test
    void getFullPath_withUnicodeLibraryPath_shouldReturnCorrectPath() throws IOException {
        // Given: LibraryPath itself contains unicode characters
        String unicodeLibraryPath = "图书馆";
        Path unicodeLibraryDir = tempDir.resolve(unicodeLibraryPath);
        Files.createDirectories(unicodeLibraryDir);

        String unicodeFilename = "中文教程.pdf";
        Path unicodeFile = unicodeLibraryDir.resolve(unicodeFilename);
        Files.createFile(unicodeFile);

        LibraryEntity libraryEntity = createMockLibraryEntity();
        LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(unicodeLibraryDir);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("")
                .fileName(unicodeFilename)
                .bookFileType(BookFileType.PDF)
                .build();

        // When: Getting full path
        Path fullPath = libraryFile.getFullPath();

        // Then: Should handle unicode library path correctly
        assertThat(fullPath).isNotNull();
        assertThat(fullPath.toString()).contains(unicodeLibraryPath);
        assertThat(fullPath.toString()).endsWith(unicodeFilename);
        assertThat(fullPath.getFileName().toString()).isEqualTo(unicodeFilename);
        assertThat(Files.exists(fullPath)).isTrue();
    }

    @Test
    void getFullPath_withSpecialCharactersInFilename_shouldHandleCorrectly() throws IOException {
        // Given: Filename with special characters that might cause encoding issues
        String[] specialFilenames = {
                "Book with spaces and unicode 中文.pdf",
                "Book-with-dashes_and_underscores中文.epub",
                "Book (with parentheses) 中文.cbz",
                "Book [with brackets] 中文.pdf",
                "Book {with braces} 中文.epub",
                "Book@with#special$chars%中文.cbz"
        };

        for (String filename : specialFilenames) {
            Path specialFile = tempDir.resolve(filename);
            Files.createFile(specialFile);

            LibraryEntity libraryEntity = createMockLibraryEntity();
            LibraryPathEntity libraryPathEntity = createMockLibraryPathEntity(tempDir);

            LibraryFile libraryFile = LibraryFile.builder()
                    .libraryEntity(libraryEntity)
                    .libraryPathEntity(libraryPathEntity)
                    .fileSubPath("")
                    .fileName(filename)
                    .bookFileType(BookFileType.PDF)
                    .build();

            // When: Getting full path
            Path fullPath = libraryFile.getFullPath();

            // Then: Should handle special characters correctly
            assertThat(fullPath).isNotNull();
            assertThat(fullPath.toString()).endsWith(filename);
            assertThat(fullPath.getFileName().toString()).isEqualTo(filename);
            assertThat(Files.exists(fullPath)).isTrue();
        }
    }

    private LibraryEntity createMockLibraryEntity() {
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(1L);
        libraryEntity.setName("Test Library");
        return libraryEntity;
    }

    private LibraryPathEntity createMockLibraryPathEntity(Path path) {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(path.toString());
        return libraryPathEntity;
    }
} 