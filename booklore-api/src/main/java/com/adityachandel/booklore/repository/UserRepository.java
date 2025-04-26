package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long> {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findById(Long id);

    @Query("SELECT u FROM BookLoreUserEntity u JOIN u.permissions p WHERE p.permissionAdmin = true")
    List<BookLoreUserEntity> findAllWithAdminPermissions();

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM BookLoreUserEntity u JOIN u.permissions p WHERE p.permissionAdmin = true")
    boolean existsWithAdminPermissions();
}
