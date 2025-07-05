package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PathPatternResolver {

    public static String resolvePattern(BookEntity book, String pattern) {
        String currentFilename = book.getFileName() != null ? book.getFileName().trim() : "";

        if (pattern == null || pattern.isBlank()) {
            return currentFilename;
        }

        String title = sanitize(book.getMetadata() != null && book.getMetadata().getTitle() != null
                ? book.getMetadata().getTitle()
                : "Untitled");

        String authors = sanitize(
                book.getMetadata() != null && book.getMetadata().getAuthors() != null
                        ? book.getMetadata().getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .collect(Collectors.joining(", "))
                        : ""
        );
        String year = sanitize(
                book.getMetadata() != null && book.getMetadata().getPublishedDate() != null
                        ? String.valueOf(book.getMetadata().getPublishedDate().getYear())
                        : ""
        );
        String series = sanitize(book.getMetadata() != null ? book.getMetadata().getSeriesName() : "");
        String seriesIndex = "";
        if (book.getMetadata() != null && book.getMetadata().getSeriesNumber() != null) {
            Float seriesNumber = book.getMetadata().getSeriesNumber();
            seriesIndex = (seriesNumber % 1 == 0)
                    ? String.valueOf(seriesNumber.intValue())
                    : seriesNumber.toString();
            seriesIndex = sanitize(seriesIndex);
        }
        String language = sanitize(book.getMetadata() != null ? book.getMetadata().getLanguage() : "");
        String publisher = sanitize(book.getMetadata() != null ? book.getMetadata().getPublisher() : "");
        String isbn = sanitize(
                book.getMetadata() != null
                        ? (book.getMetadata().getIsbn13() != null
                        ? book.getMetadata().getIsbn13()
                        : book.getMetadata().getIsbn10() != null
                        ? book.getMetadata().getIsbn10()
                        : "")
                        : ""
        );

        Map<String, String> values = new LinkedHashMap<>();
        values.put("authors", authors);
        values.put("title", title);
        values.put("year", year);
        values.put("series", series);
        values.put("seriesIndex", seriesIndex);
        values.put("language", language);
        values.put("publisher", publisher);
        values.put("isbn", isbn);
        values.put("currentFilename", currentFilename);

        return resolvePatternWithValues(pattern, values, currentFilename);
    }

    public static String resolvePattern(BookMetadata metadata, String pattern, String filename) {
        if (pattern == null || pattern.isBlank()) {
            return filename;
        }

        String title = sanitize(metadata != null && metadata.getTitle() != null
                ? metadata.getTitle()
                : "Untitled");

        String authors = sanitize(
                metadata != null && metadata.getAuthors() != null
                        ? String.join(", ", metadata.getAuthors())
                        : ""
        );
        String year = sanitize(
                metadata != null && metadata.getPublishedDate() != null
                        ? String.valueOf(metadata.getPublishedDate().getYear())
                        : ""
        );
        String series = sanitize(metadata != null ? metadata.getSeriesName() : "");
        String seriesIndex = "";
        if (metadata != null && metadata.getSeriesNumber() != null) {
            Float seriesNumber = metadata.getSeriesNumber();
            seriesIndex = (seriesNumber % 1 == 0)
                    ? String.valueOf(seriesNumber.intValue())
                    : seriesNumber.toString();
            seriesIndex = sanitize(seriesIndex);
        }
        String language = sanitize(metadata != null ? metadata.getLanguage() : "");
        String publisher = sanitize(metadata != null ? metadata.getPublisher() : "");
        String isbn = sanitize(
                metadata != null
                        ? (metadata.getIsbn13() != null
                        ? metadata.getIsbn13()
                        : metadata.getIsbn10() != null
                        ? metadata.getIsbn10()
                        : "")
                        : ""
        );

        Map<String, String> values = new LinkedHashMap<>();
        values.put("authors", authors);
        values.put("title", title);
        values.put("year", year);
        values.put("series", series);
        values.put("seriesIndex", seriesIndex);
        values.put("language", language);
        values.put("publisher", publisher);
        values.put("isbn", isbn);
        values.put("currentFilename", filename);

        return resolvePatternWithValues(pattern, values, filename);
    }

    private static String resolvePatternWithValues(String pattern, Map<String, String> values, String currentFilename) {
        String extension = "";
        int lastDot = currentFilename.lastIndexOf(".");
        if (lastDot >= 0 && lastDot < currentFilename.length() - 1) {
            extension = sanitize(currentFilename.substring(lastDot + 1));  // e.g. "epub"
        }

        values.put("extension", extension);

        // Handle optional blocks enclosed in <...>
        Pattern optionalBlockPattern = Pattern.compile("<([^<>]*)>");
        Matcher matcher = optionalBlockPattern.matcher(pattern);
        StringBuffer resolved = new StringBuffer();

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher placeholderMatcher = Pattern.compile("\\{(.*?)}").matcher(block);
            boolean allHaveValues = true;

            // Check if all placeholders inside optional block have non-blank values
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

        // Replace known placeholders with values, preserve unknown ones
        Pattern placeholderPattern = Pattern.compile("\\{(.*?)}");
        Matcher placeholderMatcher = placeholderPattern.matcher(result);
        StringBuffer finalResult = new StringBuffer();

        while (placeholderMatcher.find()) {
            String key = placeholderMatcher.group(1);
            if (values.containsKey(key)) {
                String replacement = values.get(key);
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
            } else {
                // Preserve unknown placeholders (e.g., {foo})
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement("{" + key + "}"));
            }
        }
        placeholderMatcher.appendTail(finalResult);

        result = finalResult.toString();

        if (result.isBlank()) {
            result = values.getOrDefault("currentFilename", "untitled");
        }

        boolean hasExtension = result.matches(".*\\.[a-zA-Z0-9]+$");
        boolean explicitlySetExtension = pattern.contains("{extension}");

        if (!explicitlySetExtension && !hasExtension && !extension.isBlank()) {
            result += "." + extension;
        }

        return result;
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}