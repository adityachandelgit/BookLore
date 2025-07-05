package com.adityachandel.booklore.service.scheduler;

import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedBooksCleanupService {

    private final BookRepository bookRepository;

    @Scheduled(cron = "0 0 0 * * MON")  // At 00:00 every Monday
    public void cleanupDeletedBooks() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deletedCount = bookRepository.deleteAllByDeletedAtBefore(cutoff);
        log.info("DeletedBooksCleanupService: Removed {} deleted books older than {}", deletedCount, cutoff);
    }
}
