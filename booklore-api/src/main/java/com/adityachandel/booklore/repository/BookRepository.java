package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {

    @Query("SELECT b.id FROM BookEntity b WHERE b.libraryPath.id IN :libraryPathIds")
    List<Long> findAllBookIdsByLibraryPathIdIn(@Param("libraryPathIds") Collection<Long> libraryPathIds);

    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId")
    List<BookEntity> findAllByLibraryId(@Param("libraryId") Long libraryId);

    Optional<BookEntity> findBookByIdAndLibraryId(long id, long libraryId);

    Optional<BookEntity> findBookByFileNameAndLibraryId(String fileName, long libraryId);

    @EntityGraph(attributePaths = {"metadata", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId")
    List<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId);

    @EntityGraph(attributePaths = {"metadata", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.fileSizeKb IS NULL")
    List<BookEntity> findAllWithMetadataByFileSizeKbIsNull();

    @EntityGraph(attributePaths = {"metadata", "shelves"})
    @Query("SELECT b FROM BookEntity b")
    List<BookEntity> findAllWithMetadata();

    @EntityGraph(attributePaths = {"metadata", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds")
    List<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {"metadata", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN FETCH b.metadata m
            LEFT JOIN FETCH m.authors
            LEFT JOIN FETCH m.categories
            LEFT JOIN FETCH b.shelves
            """)
    List<BookEntity> findAllFullBooks();

    @Query("""
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN FETCH b.metadata m
                LEFT JOIN FETCH m.authors a
                LEFT JOIN FETCH m.categories
                WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(m.description) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(m.seriesName) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :text, '%'))
                ORDER BY m.title ASC
            """)
    List<BookEntity> findBooksContainingMetadata(@Param("text") String text);

    Optional<BookEntity> findByCurrentHash(String currentHash);
}

