package com.caat.repository;

import com.caat.entity.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {
    Optional<Content> findByHash(String hash);
    
    boolean existsByHash(String hash);
    
    Page<Content> findByUserId(UUID userId, Pageable pageable);
    
    Page<Content> findByPlatformId(UUID platformId, Pageable pageable);
    
    @Query("SELECT c FROM Content c WHERE c.user.id = :userId AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime")
    List<Content> findByUserIdAndPublishedAtBetween(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    @Query("SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId")
    Long countByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId AND c.isRead = false")
    Long countUnreadByUserId(@Param("userId") UUID userId);
}
