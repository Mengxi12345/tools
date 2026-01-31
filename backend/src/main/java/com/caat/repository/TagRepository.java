package com.caat.repository;

import com.caat.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByName(String name);
    boolean existsByName(String name);
    
    @Query("SELECT t FROM Tag t ORDER BY t.usageCount DESC")
    List<Tag> findTopNByOrderByUsageCountDesc(Pageable pageable);
    
    default List<Tag> findTopNByOrderByUsageCountDesc(int limit) {
        return findTopNByOrderByUsageCountDesc(Pageable.ofSize(limit));
    }
}
