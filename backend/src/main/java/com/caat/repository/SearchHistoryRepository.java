package com.caat.repository;

import com.caat.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, UUID> {
    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    Page<SearchHistory> findByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT DISTINCT s.query FROM SearchHistory s ORDER BY MAX(s.createdAt) DESC")
    List<String> findDistinctQueries(Pageable pageable);
    
    @Query("SELECT s.query FROM SearchHistory s GROUP BY s.query ORDER BY MAX(s.createdAt) DESC")
    List<String> findRecentQueries(Pageable pageable);
    
    @Query("SELECT s.query, COUNT(s) as count FROM SearchHistory s GROUP BY s.query ORDER BY count DESC")
    List<Object[]> findPopularQueries(Pageable pageable);
}
