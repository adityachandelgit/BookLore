package com.adityachandel.booklore.service.migration;

import com.adityachandel.booklore.model.entity.AppMigrationEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.AppMigrationRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private AppMigrationRepository migrationRepository;
    private BookRepository bookRepository;
    private BookQueryService bookQueryService;
    private MetadataMatchService metadataMatchService;

    @Transactional
    public void populateMissingFileSizesOnce() {
        if (migrationRepository.existsById("populateFileSizes")) {
            return;
        }

        List<BookEntity> books = bookRepository.findAllWithMetadataByFileSizeKbIsNull();
        for (BookEntity book : books) {
            Long sizeInKb = FileUtils.getFileSizeInKb(book);
            if (sizeInKb != null) {
                book.setFileSizeKb(sizeInKb);
            }
        }
        bookRepository.saveAll(books);

        log.info("Starting migration 'populateFileSizes' for {} books.", books.size());
        AppMigrationEntity migration = new AppMigrationEntity();
        migration.setKey("populateFileSizes");
        migration.setExecutedAt(LocalDateTime.now());
        migration.setDescription("Populate file size for existing books");
        migrationRepository.save(migration);
        log.info("Migration 'populateFileSizes' executed successfully.");
    }

    @Transactional
    public void populateMetadataScoresOnce() {
        if (migrationRepository.existsById("populateMetadataScores")) return;

        List<BookEntity> books = bookQueryService.getAllFullBookEntities();
        for (BookEntity book : books) {
            Float score = metadataMatchService.calculateMatchScore(book);
            book.setMetadataMatchScore(score);
        }
        bookRepository.saveAll(books);

        log.info("Migration 'populateMetadataScores' applied to {} books.", books.size());
        migrationRepository.save(new AppMigrationEntity("populateMetadataScores", LocalDateTime.now(), "Calculate and store metadata match score for all books"));
    }

    @Transactional
    public void populateFileHashesOnce() {
        if (migrationRepository.existsById("populateFileHashes")) return;

        List<BookEntity> books = bookRepository.findAll();
        int updated = 0;

        for (BookEntity book : books) {
            if (book.getCurrentHash() == null || book.getInitialHash() == null) {
                String hash = FileUtils.computeFileHash(book);
                if (hash != null) {
                    if (book.getInitialHash() == null) {
                        book.setInitialHash(hash);
                    }
                    book.setCurrentHash(hash);
                    updated++;
                }
            }
        }

        bookRepository.saveAll(books);

        log.info("Migration 'populateFileHashes' applied to {} books.", updated);
        migrationRepository.save(new AppMigrationEntity(
                "populateFileHashes",
                LocalDateTime.now(),
                "Calculate and store initialHash and currentHash for all books"
        ));
    }
}
