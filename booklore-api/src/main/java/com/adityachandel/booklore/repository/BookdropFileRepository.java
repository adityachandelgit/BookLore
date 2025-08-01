package com.adityachandel.booklore.repository;


import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.entity.BookdropFileEntity.Status;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookdropFileRepository extends JpaRepository<BookdropFileEntity, Long> {

    Optional<BookdropFileEntity> findByFilePath(String filePath);

    List<BookdropFileEntity> findAllByStatus(Status status);

    long countByStatus(Status status);

    @Transactional
    @Modifying
    @Query("DELETE FROM BookdropFileEntity f WHERE f.filePath LIKE CONCAT(:prefix, '%')")
    int deleteAllByFilePathStartingWith(@Param("prefix") String prefix);
}
