package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataUpdater {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final CategoryRepository categoryRepository;
    private final BookAwardRepository awardRepository;
    private final FileService fileService;
    private final BookAwardRepository bookAwardRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookMetadataEntity setBookMetadata(long bookId, BookMetadata newMetadata, boolean setThumbnail, boolean mergeCategories) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        BookMetadataEntity metadata = bookEntity.getMetadata();

        updateLocks(newMetadata, metadata);

        if (metadata.areAllFieldsLocked()) {
            log.warn("Attempted to update metadata for book with ID {}, but all fields are locked. No update performed.", bookId);
            return metadata;
        }

        if ((metadata.getTitleLocked() == null || !metadata.getTitleLocked()) && newMetadata.getTitle() != null) {
            metadata.setTitle(newMetadata.getTitle().isEmpty() ? null : newMetadata.getTitle());
        }

        if ((metadata.getSubtitleLocked() == null || !metadata.getSubtitleLocked()) && newMetadata.getSubtitle() != null) {
            metadata.setSubtitle(newMetadata.getSubtitle().isBlank() ? null : newMetadata.getSubtitle());
        }

        if ((metadata.getPublisherLocked() == null || !metadata.getPublisherLocked()) && newMetadata.getPublisher() != null) {
            metadata.setPublisher(newMetadata.getPublisher().isBlank() ? null : newMetadata.getPublisher());
        }

        if ((metadata.getPublishedDateLocked() == null || !metadata.getPublishedDateLocked()) && newMetadata.getPublishedDate() != null) {
            metadata.setPublishedDate(newMetadata.getPublishedDate());
        }

        if ((metadata.getLanguageLocked() == null || !metadata.getLanguageLocked()) && newMetadata.getLanguage() != null) {
            metadata.setLanguage(newMetadata.getLanguage().isBlank() ? null : newMetadata.getLanguage());
        }

        if ((metadata.getIsbn10Locked() == null || !metadata.getIsbn10Locked()) && newMetadata.getIsbn10() != null) {
            metadata.setIsbn10(newMetadata.getIsbn10().isBlank() ? null : newMetadata.getIsbn10());
        }

        if ((metadata.getIsbn13Locked() == null || !metadata.getIsbn13Locked()) && newMetadata.getIsbn13() != null) {
            metadata.setIsbn13(newMetadata.getIsbn13().isBlank() ? null : newMetadata.getIsbn13());
        }
        if ((metadata.getAsinLocked() == null || !metadata.getAsinLocked()) && newMetadata.getAsin() != null) {
            metadata.setAsin(newMetadata.getAsin().isBlank() ? null : newMetadata.getAsin());
        }
        if ((metadata.getDescriptionLocked() == null || !metadata.getDescriptionLocked()) && newMetadata.getDescription() != null) {
            metadata.setDescription(newMetadata.getDescription().isBlank() ? null : newMetadata.getDescription());
        }

        if ((metadata.getPageCountLocked() == null || !metadata.getPageCountLocked()) && newMetadata.getPageCount() != null) {
            metadata.setPageCount(newMetadata.getPageCount());
        }

        if ((metadata.getAmazonRatingLocked() == null || !metadata.getAmazonRatingLocked()) && newMetadata.getAmazonRating() != null) {
            metadata.setAmazonRating(newMetadata.getAmazonRating());
        }

        if ((metadata.getAmazonReviewCountLocked() == null || !metadata.getAmazonReviewCountLocked()) && newMetadata.getAmazonReviewCount() != null) {
            metadata.setAmazonReviewCount(newMetadata.getAmazonReviewCount());
        }

        if ((metadata.getGoodreadsRatingLocked() == null || !metadata.getGoodreadsRatingLocked()) && newMetadata.getGoodreadsRating() != null) {
            metadata.setGoodreadsRating(newMetadata.getGoodreadsRating());
        }

        if ((metadata.getGoodreadsReviewCountLocked() == null || !metadata.getGoodreadsReviewCountLocked()) && newMetadata.getGoodreadsReviewCount() != null) {
            metadata.setGoodreadsReviewCount(newMetadata.getGoodreadsReviewCount());
        }

        if ((metadata.getSeriesNameLocked() == null || !metadata.getSeriesNameLocked()) && newMetadata.getSeriesName() != null) {
            metadata.setSeriesName(newMetadata.getSeriesName());
        }

        if ((metadata.getSeriesNumberLocked() == null || !metadata.getSeriesNumberLocked()) && newMetadata.getSeriesNumber() != null) {
            metadata.setSeriesNumber(newMetadata.getSeriesNumber());
        }

        if ((metadata.getSeriesTotalLocked() == null || !metadata.getSeriesTotalLocked()) && newMetadata.getSeriesTotal() != null) {
            metadata.setSeriesTotal(newMetadata.getSeriesTotal());
        }

        if (newMetadata.getAwards() != null && !newMetadata.getAwards().isEmpty()) {
            HashSet<BookAwardEntity> newAwards = new HashSet<>();
            newMetadata.getAwards()
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(award -> {
                        boolean awardExists = bookMetadataRepository.findAwardByBookIdAndNameAndCategoryAndAwardedAt(
                                metadata.getBookId(),
                                award.getName(),
                                award.getCategory(),
                                award.getAwardedAt()) != null;

                        if (!awardExists) {
                            BookAwardEntity awardEntity = new BookAwardEntity();
                            awardEntity.setBook(metadata);
                            awardEntity.setName(award.getName());
                            awardEntity.setCategory(award.getCategory());
                            awardEntity.setDesignation(award.getDesignation());
                            awardEntity.setAwardedAt(award.getAwardedAt() != null ? award.getAwardedAt() : Instant.now().atZone(ZoneId.systemDefault()).toLocalDate());

                            newAwards.add(awardEntity);
                        }
                    });
            if (!newAwards.isEmpty()) {
                metadata.setAwards(new ArrayList<>(newAwards));
                bookAwardRepository.saveAll(newAwards);
            }
        }

        if ((metadata.getAuthorsLocked() == null || !metadata.getAuthorsLocked()) && newMetadata.getAuthors() != null && !newMetadata.getAuthors().isEmpty()) {
            metadata.setAuthors(newMetadata.getAuthors()
                    .stream()
                    .filter(a -> a != null && !a.isBlank())
                    .map(authorName -> authorRepository.findByName(authorName)
                            .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(authorName).build())))
                    .collect(Collectors.toList()));
        }

        if (mergeCategories) {
            if ((metadata.getCategoriesLocked() == null || !metadata.getCategoriesLocked()) && newMetadata.getCategories() != null) {
                HashSet<CategoryEntity> existingCategories = new HashSet<>(metadata.getCategories());
                newMetadata.getCategories()
                        .stream()
                        .filter(c -> c != null && !c.isBlank())
                        .forEach(categoryName -> {
                            CategoryEntity categoryEntity = categoryRepository.findByName(categoryName)
                                    .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(categoryName).build()));
                            existingCategories.add(categoryEntity);
                        });
                metadata.setCategories(new ArrayList<>(existingCategories));
            }
        } else {
            if ((metadata.getCategoriesLocked() == null || !metadata.getCategoriesLocked()) && newMetadata.getCategories() != null && !newMetadata.getCategories().isEmpty()) {
                metadata.setCategories(newMetadata.getCategories()
                        .stream()
                        .filter(c -> c != null && !c.isBlank())
                        .map(categoryName -> categoryRepository.findByName(categoryName)
                                .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(categoryName).build())))
                        .collect(Collectors.toList()));
            }
        }

        if (setThumbnail && (metadata.getThumbnailLocked() == null || !metadata.getThumbnailLocked()) && newMetadata.getThumbnailUrl() != null && !newMetadata.getThumbnailUrl().isEmpty()) {
            String thumbnailPath = null;
            try {
                thumbnailPath = fileService.createThumbnail(bookId, newMetadata.getThumbnailUrl());
                metadata.setCoverUpdatedOn(Instant.now());
            } catch (IOException e) {
                log.error(e.getMessage());
            }
            metadata.setThumbnail(thumbnailPath);
        }

        if (!metadata.getAuthors().isEmpty()) {
            authorRepository.saveAll(metadata.getAuthors());
        }
        if (!metadata.getCategories().isEmpty()) {
            categoryRepository.saveAll(metadata.getCategories());
        }
        if (!metadata.getAwards().isEmpty()) {
            awardRepository.saveAll(metadata.getAwards());
        }
        bookMetadataRepository.save(metadata);
        return metadata;
    }

    private void updateLocks(BookMetadata newMetadata, BookMetadataEntity metadata) {
        if (newMetadata.getTitleLocked() != null) {
            metadata.setTitleLocked(newMetadata.getTitleLocked());
        }
        if (newMetadata.getSubtitleLocked() != null) {
            metadata.setSubtitleLocked(newMetadata.getSubtitleLocked());
        }
        if (newMetadata.getPublisherLocked() != null) {
            metadata.setPublisherLocked(newMetadata.getPublisherLocked());
        }
        if (newMetadata.getPublishedDateLocked() != null) {
            metadata.setPublishedDateLocked(newMetadata.getPublishedDateLocked());
        }
        if (newMetadata.getDescriptionLocked() != null) {
            metadata.setDescriptionLocked(newMetadata.getDescriptionLocked());
        }
        if (newMetadata.getIsbn13Locked() != null) {
            metadata.setIsbn13Locked(newMetadata.getIsbn13Locked());
        }
        if (newMetadata.getIsbn10Locked() != null) {
            metadata.setIsbn10Locked(newMetadata.getIsbn10Locked());
        }
        if (newMetadata.getAsinLocked() != null) {
            metadata.setAsinLocked(newMetadata.getAsinLocked());
        }
        if (newMetadata.getPageCountLocked() != null) {
            metadata.setPageCountLocked(newMetadata.getPageCountLocked());
        }
        if (newMetadata.getLanguageLocked() != null) {
            metadata.setLanguageLocked(newMetadata.getLanguageLocked());
        }
        if (newMetadata.getAmazonRatingLocked() != null) {
            metadata.setAmazonRatingLocked(newMetadata.getAmazonRatingLocked());
        }
        if (newMetadata.getAmazonReviewCountLocked() != null) {
            metadata.setAmazonReviewCountLocked(newMetadata.getAmazonReviewCountLocked());
        }
        if (newMetadata.getGoodreadsRatingLocked() != null) {
            metadata.setGoodreadsRatingLocked(newMetadata.getGoodreadsRatingLocked());
        }
        if (newMetadata.getGoodreadsReviewCountLocked() != null) {
            metadata.setGoodreadsReviewCountLocked(newMetadata.getGoodreadsReviewCountLocked());
        }
        if (newMetadata.getSeriesNameLocked() != null) {
            metadata.setSeriesNameLocked(newMetadata.getSeriesNameLocked());
        }
        if (newMetadata.getSeriesNumberLocked() != null) {
            metadata.setSeriesNumberLocked(newMetadata.getSeriesNumberLocked());
        }
        if (newMetadata.getSeriesTotalLocked() != null) {
            metadata.setSeriesTotalLocked(newMetadata.getSeriesTotalLocked());
        }
        if (newMetadata.getAuthorsLocked() != null) {
            metadata.setAuthorsLocked(newMetadata.getAuthorsLocked());
        }
        if (newMetadata.getCategoriesLocked() != null) {
            metadata.setCategoriesLocked(newMetadata.getCategoriesLocked());
        }
        if (newMetadata.getCoverLocked() != null) {
            metadata.setCoverLocked(newMetadata.getCoverLocked());
        }
    }

}