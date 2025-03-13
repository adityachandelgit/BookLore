package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.EmailProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailProviderRepository extends JpaRepository<EmailProviderEntity, Long> {
}
