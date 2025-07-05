package com.adityachandel.booklore;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileMoveService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMoveServiceMoveFilesTest {

    @Mock
    private BookQueryService bookQueryService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MonitoringService monitoringService;

    @InjectMocks
    private FileMoveService fileMoveService;

    @TempDir
    Path tempLibraryRoot;

    private BookEntity createBookWithFile(Path libraryRoot, String fileSubPath, String fileName) throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(42L)
                .name("Test Library")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(libraryRoot.toString())
                .library(library)
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .authors(new HashSet<>(List.of(new AuthorEntity(1L, "Author Name", new ArrayList<>()))))
                .publishedDate(LocalDate.of(2020, 1, 1))
                .build();

        BookEntity book = BookEntity.builder()
                .id(1L)
                .fileName(fileName)
                .fileSubPath(fileSubPath)
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFilePath = book.getFullFilePath();
        Files.createDirectories(oldFilePath.getParent());
        Files.createFile(oldFilePath);

        return book;
    }

    @Test
    void testMoveFiles_successfulMove() throws IOException {
        String fileName = "testbook.epub";
        String fileSubPath = "oldfolder";

        BookEntity book = createBookWithFile(tempLibraryRoot, fileSubPath, fileName);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(1L));
        request.setPattern("NewFolder/{title}");

        Path oldFilePath = book.getFullFilePath();

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("NewFolder").resolve("Test Book.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("Expected the file to be moved to %s", expectedNewPath)
                .isTrue();

        assertThat(Files.exists(oldFilePath))
                .withFailMessage("Original file path should no longer exist: %s", oldFilePath)
                .isFalse();

        Files.deleteIfExists(expectedNewPath);
        Files.deleteIfExists(expectedNewPath.getParent());
    }

    @Test
    void testMoveFiles_skipsNonexistentFile() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NoFile")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .build();

        BookEntity book = BookEntity.builder()
                .id(2L)
                .fileName("nofile.epub")
                .fileSubPath("subfolder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(2L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(2L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NoFile.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("No file should be created for nonexistent source")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithoutLibraryPath() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("MissingLibrary")
                .build();

        BookEntity book = BookEntity.builder()
                .id(3L)
                .fileName("missinglibrary.epub")
                .fileSubPath("subfolder")
                .metadata(metadata)
                .libraryPath(null)
                .build();

        Path fakeOldFile = tempLibraryRoot.resolve("subfolder").resolve("missinglibrary.epub");
        Files.createDirectories(fakeOldFile.getParent());
        Files.createFile(fakeOldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(3L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(3L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("MissingLibrary.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if library path is missing")
                .isFalse();
        assertThat(Files.exists(fakeOldFile))
                .withFailMessage("Original file should remain when library path is missing")
                .isTrue();

        Files.deleteIfExists(fakeOldFile);
    }

    @Test
    void testMoveFiles_handlesEmptyPattern() throws IOException {
        String fileName = "emptypattern.epub";
        String fileSubPath = "folder";

        BookEntity book = createBookWithFile(tempLibraryRoot, fileSubPath, fileName);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(4L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(4L));
        request.setPattern("");

        Path oldFilePath = book.getFullFilePath();

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve(fileName);
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should be moved to the original filename path when pattern is empty")
                .isTrue();

        assertThat(Files.exists(oldFilePath))
                .withFailMessage("Original file path should no longer exist")
                .isFalse();

        Files.deleteIfExists(expectedNewPath);
    }

    @Test
    void testMoveFiles_removesIllegalCharactersFromPath() throws IOException {
        String fileName = "badchars.epub";
        String fileSubPath = "folder";

        BookEntity book = createBookWithFile(tempLibraryRoot, fileSubPath, fileName);
        book.getMetadata().setTitle("Bad|Title:Test*?");

        when(bookQueryService.findAllWithMetadataByIds(Set.of(5L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(5L));
        request.setPattern("{title}");

        Path oldFilePath = book.getFullFilePath();

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("BadTitleTest.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("Illegal characters should be removed from generated path")
                .isTrue();

        assertThat(Files.exists(oldFilePath))
                .withFailMessage("Original file path should no longer exist")
                .isFalse();

        Files.deleteIfExists(expectedNewPath);
    }

    @Test
    void testMoveFiles_deletesEmptyOldDirectories() throws IOException {
        String fileName = "deleteold.epub";
        String fileSubPath = "folder/emptydir";

        BookEntity book = createBookWithFile(tempLibraryRoot, fileSubPath, fileName);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(6L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(6L));
        request.setPattern("NewFolder/{title}");

        Path oldDir = tempLibraryRoot.resolve(fileSubPath).normalize();
        assertThat(Files.exists(oldDir))
                .withFailMessage("Precondition failed: old directory must exist before move")
                .isTrue();

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("NewFolder").resolve("Test Book.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("Expected file to be moved to new location")
                .isTrue();

        assertThat(Files.exists(oldDir))
                .withFailMessage("Expected old empty directory to be deleted after move")
                .isFalse();

        assertThat(Files.exists(oldDir.getParent()))
                .withFailMessage("Expected parent directory of old directory to be deleted if empty")
                .isFalse();

        Files.deleteIfExists(expectedNewPath);
        Files.deleteIfExists(expectedNewPath.getParent());
    }

    @Test
    void testMoveFiles_keepsOldDirectoryIfNotEmpty() throws IOException {
        String fileName = "keepold.epub";
        String fileSubPath = "folder/notempty";

        BookEntity book = createBookWithFile(tempLibraryRoot, fileSubPath, fileName);

        Path oldDir = tempLibraryRoot.resolve(fileSubPath).normalize();
        Files.createDirectories(oldDir);

        Path otherFile = oldDir.resolve("otherfile.txt");
        Files.createFile(otherFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(7L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(7L));
        request.setPattern("NewFolder/{title}");

        Path oldFilePath = book.getFullFilePath();

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("NewFolder").resolve("Test Book.epub");

        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("Expected file to be moved to new location")
                .isTrue();
        assertThat(Files.exists(oldFilePath))
                .withFailMessage("Original file path should no longer exist")
                .isFalse();

        assertThat(Files.exists(oldDir))
                .withFailMessage("Old directory should remain if it contains other files")
                .isTrue();
        assertThat(Files.exists(otherFile))
                .withFailMessage("Other files in old directory should remain intact")
                .isTrue();

        Files.deleteIfExists(otherFile);
        Files.deleteIfExists(expectedNewPath);
        Files.deleteIfExists(expectedNewPath.getParent());
    }

    @Test
    void testMoveFiles_handlesMultipleBooks() throws IOException {
        BookEntity book1 = createBookWithFile(tempLibraryRoot, "folder1", "book1.epub");
        book1.getMetadata().setTitle("Book One");

        BookEntity book2 = createBookWithFile(tempLibraryRoot, "folder2", "book2.epub");
        book2.getMetadata().setTitle("Book Two");

        when(bookQueryService.findAllWithMetadataByIds(Set.of(8L, 9L))).thenReturn(List.of(book1, book2));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(8L, 9L));
        request.setPattern("Moved/{title}");

        Path oldPath1 = book1.getFullFilePath();
        Path oldPath2 = book2.getFullFilePath();

        fileMoveService.moveFiles(request);

        Path expectedPath1 = tempLibraryRoot.resolve("Moved").resolve("Book One.epub");
        Path expectedPath2 = tempLibraryRoot.resolve("Moved").resolve("Book Two.epub");

        assertThat(Files.exists(expectedPath1))
                .withFailMessage("Expected first book to be moved to new location")
                .isTrue();
        assertThat(Files.exists(expectedPath2))
                .withFailMessage("Expected second book to be moved to new location")
                .isTrue();

        assertThat(Files.exists(oldPath1))
                .withFailMessage("Original path for first book should no longer exist")
                .isFalse();
        assertThat(Files.exists(oldPath2))
                .withFailMessage("Original path for second book should no longer exist")
                .isFalse();

        Files.deleteIfExists(expectedPath1);
        Files.deleteIfExists(expectedPath2);
        Files.deleteIfExists(expectedPath1.getParent());
    }

    @Test
    void testMoveFiles_skipsBookWithNullFileName() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NullFileName")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .build();

        BookEntity book = BookEntity.builder()
                .id(10L)
                .fileName(null)
                .fileSubPath("folder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(10L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(10L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NullFileName");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if filename is null")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithEmptyFileName() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("EmptyFileName")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .build();

        BookEntity book = BookEntity.builder()
                .id(11L)
                .fileName("")
                .fileSubPath("folder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(11L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(11L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("EmptyFileName");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if filename is empty")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithNullFileSubPath() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NullSubPath")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .build();

        BookEntity book = BookEntity.builder()
                .id(12L)
                .fileName("file.epub")
                .fileSubPath(null)
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFile = tempLibraryRoot.resolve("file.epub");
        Files.createFile(oldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(12L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(12L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NullSubPath.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if fileSubPath is null")
                .isFalse();

        Files.deleteIfExists(oldFile);
    }

    @Test
    void testMoveFiles_skipsBookWithNullMetadata() throws IOException {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .build();

        BookEntity book = BookEntity.builder()
                .id(13L)
                .fileName("file.epub")
                .fileSubPath("folder")
                .metadata(null)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFile = tempLibraryRoot.resolve("folder").resolve("file.epub");
        Files.createDirectories(oldFile.getParent());
        Files.createFile(oldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(13L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(13L));
        request.setPattern("Moved/{title}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve(".epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if metadata is null")
                .isFalse();

        Files.deleteIfExists(oldFile);
    }

    @Disabled
    @Test
    void testMoveFiles_patternWithUnknownPlaceholder() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "folder", "unknown.epub");
        book.getMetadata().setTitle("UnknownPlaceholder");

        when(bookQueryService.findAllWithMetadataByIds(Set.of(14L))).thenReturn(List.of(book));

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(14L));
        request.setPattern("Moved/{title}/{foo}");

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("UnknownPlaceholder").resolve("{foo}.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("Unknown placeholders should remain in the generated path")
                .isTrue();

        Files.deleteIfExists(expectedNewPath);
        Files.deleteIfExists(expectedNewPath.getParent());
    }
}