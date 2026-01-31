package com.caat.repository;

import com.caat.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByIsReadFalseOrderByCreatedAtDesc(Pageable pageable);
    
    Page<Notification> findByIsReadOrderByCreatedAtDesc(Boolean isRead, Pageable pageable);
    
    long countByIsReadFalse();
}
