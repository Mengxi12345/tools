package com.caat.repository;

import com.caat.entity.NotificationChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationChannelConfigRepository extends JpaRepository<NotificationChannelConfig, UUID> {

    List<NotificationChannelConfig> findByChannelTypeOrderByUpdatedAtDesc(String channelType);

    List<NotificationChannelConfig> findAllByOrderByUpdatedAtDesc();
}
