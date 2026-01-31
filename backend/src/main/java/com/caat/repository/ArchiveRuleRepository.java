package com.caat.repository;

import com.caat.entity.ArchiveRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArchiveRuleRepository extends JpaRepository<ArchiveRule, UUID> {
    List<ArchiveRule> findByIsEnabledTrue();
    
    List<ArchiveRule> findByAutoExecuteTrueAndIsEnabledTrue();
}
