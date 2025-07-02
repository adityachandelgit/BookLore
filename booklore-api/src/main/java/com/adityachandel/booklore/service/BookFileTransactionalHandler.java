package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFileTransactionalHandler {

    private final BookFilePersistenceService bookFilePersistenceService;
    private final LibraryProcessingService libraryProcessingService;
    private final NotificationService notificationService;
    private final LibraryRepository libraryRepository;

    @Transactional()
    public void handleNewBookFile(long libraryId, Path path, String currentHash) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        Optional<BookEntity> existingOpt = bookFilePersistenceService.findByHash(currentHash);
        if (existingOpt.isPresent()) {
            BookEntity existingBook = existingOpt.get();
            bookFilePersistenceService.updatePathIfChanged(existingBook, libraryEntity, path, currentHash);
            return;
        }

        String filePath = path.toString();
        String fileName = path.getFileName().toString();
        String libraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, path);

        notificationService.sendMessage(Topic.LOG, createLogNotification("Started processing file: " + filePath));

        LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, libraryPath);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path))
                .fileName(fileName)
                .bookFileType(getBookFileType(fileName))
                .build();

        libraryProcessingService.processLibraryFiles(List.of(libraryFile));

        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished processing file: " + filePath));
        log.info("[CREATE] Completed processing for file '{}'", filePath);
    }

    protected BookFileType getBookFileType(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        if (lowerCaseName.endsWith(".pdf")) {
            return BookFileType.PDF;
        } else if (lowerCaseName.endsWith(".cbz") || lowerCaseName.endsWith(".cbr") || lowerCaseName.endsWith(".cb7")) {
            return BookFileType.CBX;
        } else {
            return BookFileType.EPUB;
        }
    }
}
