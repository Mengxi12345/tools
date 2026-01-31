package com.caat.repository;

import com.caat.entity.ExportTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExportTaskRepository extends JpaRepository<ExportTask, UUID> {
    List<ExportTask> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<ExportTask> findByStatus(ExportTask.TaskStatus status);
    
    Page<ExportTask> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    Page<ExportTask> findByStatusOrderByCreatedAtDesc(ExportTask.TaskStatus status, Pageable pageable);
}
