package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.MetadataFetchProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadataFetchProposalRepository extends JpaRepository<MetadataFetchProposalEntity, Long> {

}
