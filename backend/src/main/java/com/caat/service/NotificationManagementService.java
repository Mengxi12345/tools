package com.caat.service;

import com.caat.entity.Notification;
import com.caat.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationManagementService {
    
    private final NotificationRepository notificationRepository;
    
    /**
     * 获取通知列表（分页）
     */
    public Page<Notification> getNotifications(Pageable pageable, Boolean isRead) {
        if (isRead != null) {
            return notificationRepository.findByIsReadOrderByCreatedAtDesc(isRead, pageable);
        }
        return notificationRepository.findAll(pageable);
    }
    
    /**
     * 获取未读通知列表
     */
    public Page<Notification> getUnreadNotifications(Pageable pageable) {
        return notificationRepository.findByIsReadFalseOrderByCreatedAtDesc(pageable);
    }
    
    /**
     * 获取未读通知数量
     */
    public long getUnreadCount() {
        return notificationRepository.countByIsReadFalse();
    }
    
    /**
     * 标记通知为已读
     */
    @Transactional
    public Notification markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在: " + notificationId));
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }
    
    /**
     * 标记所有通知为已读
     */
    @Transactional
    public void markAllAsRead() {
        Page<Notification> unreadNotifications = notificationRepository.findByIsReadFalseOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 1000));
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : unreadNotifications.getContent()) {
            notification.setIsRead(true);
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unreadNotifications.getContent());
    }
    
    /**
     * 删除通知
     */
    @Transactional
    public void deleteNotification(UUID notificationId) {
        notificationRepository.deleteById(notificationId);
    }
    
    /**
     * 根据 ID 获取通知
     */
    public Notification getNotificationById(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在: " + notificationId));
    }
}
