package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class FileOperationsService {

    private final BookQueryService bookQueryService;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;

    public void moveFiles(FileMoveRequest request) {
        Set<Long> bookIds = request.getBookIds();
        String pattern = request.getPattern();
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);

        log.info("Starting file move for {} books using pattern: '{}'", books.size(), pattern);

        for (BookEntity book : books) {
            if (book.getMetadata() == null) {
                continue;
            }
            if (book.getLibraryPath() == null || book.getLibraryPath().getPath() == null || book.getFileSubPath() == null || book.getFileName() == null) {
                log.error("❌ Missing library path, file subpath, or file name for book id {}. Skipping.", book.getId());
                continue;
            }
            Path oldFilePath = book.getFullFilePath();
            if (!Files.exists(oldFilePath)) {
                log.warn("❗ File does not exist for book id {}: {}", book.getId(), oldFilePath);
                continue;
            }

            log.info("📚 Processing book id {}: '{}'", book.getId(), book.getMetadata().getTitle());

            String newRelativePathStr = generatePathFromPattern(book, pattern);
            // Ensure relative path doesn't start with slash or backslash
            if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
                newRelativePathStr = newRelativePathStr.substring(1);
            }
            log.info("📁 Generated new relative path: {}", newRelativePathStr);

            Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
            Path newFilePath = libraryRoot.resolve(newRelativePathStr).normalize();

            log.info("➡️ Library file path: {}", libraryRoot);
            log.info("➡️ Resolved new absolute file path: {}", newFilePath);
            log.info("📂 Creating directories if not present: {}", newFilePath.getParent());
            System.out.println();

            // Skip move if no path change
            if (oldFilePath.equals(newFilePath)) {
                log.info("ℹ️ Old and new file paths are the same for book id {}: {}. Skipping move.", book.getId(), oldFilePath);
                continue;
            }

            try {
                if (newFilePath.getParent() != null) {
                    Files.createDirectories(newFilePath.getParent());
                }

                log.info("🚚 Moving file from {} to {}", oldFilePath, newFilePath);
                Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

                log.info("✅ File moved successfully for book id {}", book.getId());

                String newFileName = newFilePath.getFileName().toString();

                // Compute new file sub path (relative to library root)
                Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
                String newFileSubPath = newRelativeSubPath.toString();

                // Normalize slashes (optional)
                newFileSubPath = newFileSubPath.replace('\\', '/');

                book.setFileSubPath(newFileSubPath);
                book.setFileName(newFileName);

                // Save the updated book entity
                bookRepository.save(book);

                // Map to DTO and send update notification to frontend
                Book updatedBookDto = bookMapper.toBook(book);
                notificationService.sendMessage(Topic.BOOK_METADATA_UPDATE, updatedBookDto);

                log.info("📦 Updated BookEntity for book id {} with new subpath '{}' and filename '{}'", book.getId(), newFileSubPath, newFileName);

                log.info("🧹 Cleaning up old directories for {}", oldFilePath.getParent());
                deleteEmptyParentDirsUpToLibraryFolders(oldFilePath.getParent(), Set.of(libraryRoot));
            } catch (IOException e) {
                log.error("❌ Failed to move file for book id {}: {}", book.getId(), e.getMessage(), e);
            }
        }

        log.info("🎉 Completed file move operation for {} books.", books.size());
    }

    public String generatePathFromPattern(BookEntity book, String pattern) {
        String title = sanitize(Optional.ofNullable(book.getMetadata().getTitle()).orElse("Untitled"));

        String authors = sanitize(
                book.getMetadata().getAuthors() != null
                        ? book.getMetadata().getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .collect(Collectors.joining(", "))
                        : "Unknown Author"
        );

        String year = sanitize(
                Optional.ofNullable(book.getMetadata().getPublishedDate())
                        .map(LocalDate::getYear)
                        .map(String::valueOf)
                        .orElse("")
        );

        String series = sanitize(Optional.ofNullable(book.getMetadata().getSeriesName()).orElse(""));
        String seriesIndex = sanitize(book.getMetadata().getSeriesTotal() != null
                ? String.format("%02d", book.getMetadata().getSeriesTotal())
                : "");

        String language = sanitize(Optional.ofNullable(book.getMetadata().getLanguage()).orElse(""));
        String publisher = sanitize(Optional.ofNullable(book.getMetadata().getPublisher()).orElse(""));
        String isbn = sanitize(
                Optional.ofNullable(book.getMetadata().getIsbn13())
                        .orElse(Optional.ofNullable(book.getMetadata().getIsbn10()).orElse(""))
        );

        String fileName = Optional.ofNullable(book.getFileName()).orElse("");

        String extension = "";
        if (fileName.contains(".")) {
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot < fileName.length() - 1) {
                extension = fileName.substring(lastDot);
            }
        }

        Map<String, String> values = Map.of(
                "authors", authors,
                "title", title,
                "year", year,
                "series", series,
                "seriesIndex", seriesIndex,
                "language", language,
                "publisher", publisher,
                "isbn", isbn
        );

        Pattern optionalBlockPattern = Pattern.compile("<([^<>]*)>");
        Matcher matcher = optionalBlockPattern.matcher(pattern);
        StringBuffer resolved = new StringBuffer();

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher placeholderMatcher = Pattern.compile("\\{(.*?)}").matcher(block);
            boolean allHaveValues = true;

            while (placeholderMatcher.find()) {
                String key = placeholderMatcher.group(1);
                String value = values.getOrDefault(key, "");
                if (value.isBlank()) {
                    allHaveValues = false;
                    break;
                }
            }

            if (allHaveValues) {
                String resolvedBlock = block;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    resolvedBlock = resolvedBlock.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolvedBlock));
            } else {
                matcher.appendReplacement(resolved, "");
            }
        }

        matcher.appendTail(resolved);
        String result = resolved.toString();

        // Replace remaining placeholders
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result + extension;
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) throws IOException {
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        currentDir = currentDir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (currentDir != null) {
            boolean isLibraryRoot = false;
            for (Path root : normalizedRoots) {
                try {
                    if (Files.isSameFile(root, currentDir)) {
                        isLibraryRoot = true;
                        break;
                    }
                } catch (IOException e) {
                    log.warn("Failed to compare paths: {} and {}", root, currentDir);
                }
            }

            if (isLibraryRoot) {
                log.debug("Reached library root: {}. Stopping cleanup.", currentDir);
                break;
            }

            File[] files = currentDir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", currentDir);
                break;
            }

            boolean hasImportantFiles = false;
            for (File file : files) {
                if (!ignoredFilenames.contains(file.getName())) {
                    hasImportantFiles = true;
                    break;
                }
            }

            if (!hasImportantFiles) {
                for (File file : files) {
                    try {
                        Files.delete(file.toPath());
                        log.info("Deleted ignored file: {}", file.getAbsolutePath());
                    } catch (IOException e) {
                        log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
                    }
                }
                try {
                    Files.delete(currentDir);
                    log.info("Deleted empty directory: {}", currentDir);
                } catch (IOException e) {
                    log.warn("Failed to delete directory: {}", currentDir, e);
                    break;
                }
                currentDir = currentDir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", currentDir);
                break;
            }
        }
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        return input
                .replaceAll("[\\\\/:*?\"<>|]", "")  // Remove illegal chars (Windows)
                .replaceAll("[\\p{Cntrl}]", "")     // Remove control chars
                .replaceAll("\\s+", " ")            // Collapse multiple spaces
                .trim();                            // Trim surrounding whitespace
    }
}