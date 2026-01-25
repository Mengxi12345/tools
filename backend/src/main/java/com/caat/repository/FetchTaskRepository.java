package com.caat.repository;

import com.caat.entity.FetchTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FetchTaskRepository extends JpaRepository<FetchTask, UUID> {
    List<FetchTask> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<FetchTask> findByStatus(FetchTask.TaskStatus status);
    
    Page<FetchTask> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
