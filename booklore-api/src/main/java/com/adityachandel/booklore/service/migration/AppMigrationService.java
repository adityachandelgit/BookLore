package com.adityachandel.booklore.service.migration;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.AppMigrationEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserSettingEntity;
import com.adityachandel.booklore.repository.AppMigrationRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import com.adityachandel.booklore.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private AppMigrationRepository migrationRepository;
    private BookRepository bookRepository;

    @Transactional
    public void populateMissingFileSizesOnce() {
        if (migrationRepository.existsById("populateFileSizes")) {
            return;
        }

        List<BookEntity> books = bookRepository.findByFileSizeKbIsNull();
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
}
