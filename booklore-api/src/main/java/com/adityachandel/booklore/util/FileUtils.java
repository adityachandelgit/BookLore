package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.BookEntity;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
public class FileUtils {

    private static final String FILE_NOT_FOUND_MESSAGE = "File does not exist: ";

    public static String getBookFullPath(BookEntity bookEntity) {
        return Path.of(bookEntity.getLibraryPath().getPath(), bookEntity.getFileSubPath(), bookEntity.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public static String getRelativeSubPath(String basePath, Path fullFilePath) {
        return Optional.ofNullable(Path.of(basePath)
                        .relativize(fullFilePath)
                        .getParent())
                .map(path -> path.toString().replace("\\", "/"))
                .orElse("");
    }

    public static Long getFileSizeInKb(BookEntity bookEntity) {
        Path filePath = Path.of(getBookFullPath(bookEntity));
        return getFileSizeInKb(filePath);
    }

    public static Long getFileSizeInKb(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                log.warn(FILE_NOT_FOUND_MESSAGE + "{}", filePath.toAbsolutePath());
                return null;
            }
            return Files.size(filePath) / 1024;
        } catch (IOException e) {
            log.error("Failed to get file size for path [{}]: {}", filePath, e.getMessage(), e);
            return null;
        }
    }

    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    public static String computeFileHash(Path path) {
        try {
            return computeSHA256HeadAndTail(path, 64 * 1024);
        } catch (Exception e) {
            log.warn("Failed to compute hash for file '{}': {}", path, e.getMessage());
            return null;
        }
    }

    public static String computeFileHash(BookEntity book) {
        try {
            Path filePath = book.getFullFilePath();
            return computeSHA256HeadAndTail(filePath, 64 * 1024);
        } catch (Exception e) {
            log.warn("Failed to compute hash for book '{}': {}", book.getFileName(), e.getMessage());
            return null;
        }
    }

    public static String computeSHA256HeadAndTail(Path path, int sampleSize) throws IOException, NoSuchAlgorithmException {
        long fileSize = Files.size(path);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {

            byte[] startBytes = new byte[(int) Math.min(sampleSize, fileSize)];
            raf.seek(0);
            raf.readFully(startBytes);
            digest.update(startBytes);

            if (fileSize > sampleSize) {
                byte[] endBytes = new byte[(int) Math.min(sampleSize, fileSize)];
                raf.seek(fileSize - sampleSize);
                raf.readFully(endBytes);
                digest.update(endBytes);
            }
        }

        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }
}