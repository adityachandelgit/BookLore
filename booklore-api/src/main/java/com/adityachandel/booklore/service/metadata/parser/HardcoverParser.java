package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {

    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        log.info("Hardcover: Fetching metadata for book {}", fetchMetadataRequest.getTitle());

        List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(fetchMetadataRequest.getTitle());

        return hits.stream().map(hit -> {
            GraphQLResponse.Document doc = hit.getDocument();
            BookMetadata metadata = new BookMetadata();
            metadata.setProviderBookId(doc.getSlug());
            metadata.setTitle(doc.getTitle());
            metadata.setDescription(doc.getDescription());
            metadata.setAuthors(doc.getAuthorNames());

            if (doc.getFeaturedSeries() != null) {
                if (doc.getFeaturedSeries().getSeries() != null) {
                    metadata.setSeriesName(doc.getFeaturedSeries().getSeries().getName());
                    metadata.setSeriesTotal(doc.getFeaturedSeries().getSeries().getBooksCount());
                }
                if (doc.getFeaturedSeries().getPosition() != null) {
                    try {
                        metadata.setSeriesNumber(Float.parseFloat(String.valueOf(doc.getFeaturedSeries().getPosition())));
                    } catch (NumberFormatException ignored) {

                    }
                }
            }

            if (doc.getRating() != null) {
                BigDecimal roundedRating = BigDecimal.valueOf(doc.getRating()).setScale(2, RoundingMode.HALF_UP);
                metadata.setHardcoverRating(roundedRating.doubleValue());
            }
            metadata.setHardcoverReviewCount(doc.getRatingsCount());
            metadata.setPageCount(doc.getPages());
            metadata.setPublishedDate(doc.getReleaseDate() != null ? LocalDate.parse(doc.getReleaseDate()) : null);
            metadata.setCategories(doc.getGenres());

            if (doc.getIsbns() != null) {
                metadata.setIsbn10(doc.getIsbns().stream()
                        .filter(isbn -> isbn.length() == 10)
                        .findFirst()
                        .orElse(null));

                metadata.setIsbn13(doc.getIsbns().stream()
                        .filter(isbn -> isbn.length() == 13)
                        .findFirst()
                        .orElse(null));
            }

            metadata.setThumbnailUrl(doc.getImage() != null ? doc.getImage().getUrl() : null);
            metadata.setProvider(MetadataProvider.Hardcover);
            return metadata;
        }).toList();
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
}
