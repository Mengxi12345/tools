package com.caat.service;

import com.caat.dto.NotificationChannelConfigCreateRequest;
import com.caat.entity.NotificationChannelConfig;
import com.caat.repository.NotificationChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationChannelConfigService {

    private final NotificationChannelConfigRepository repository;

    public List<NotificationChannelConfig> findAll() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public List<NotificationChannelConfig> findByChannelType(String channelType) {
        return repository.findByChannelTypeOrderByUpdatedAtDesc(channelType);
    }

    public NotificationChannelConfig getById(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("通道配置不存在: " + id));
    }

    @Transactional
    public NotificationChannelConfig create(NotificationChannelConfigCreateRequest request) {
        NotificationChannelConfig entity = new NotificationChannelConfig();
        entity.setName(request.getName());
        entity.setChannelType(request.getChannelType());
        entity.setConfig(request.getConfig());
        entity.setIsShared(request.getIsShared() != null ? request.getIsShared() : false);
        entity.setCreatedBy(request.getCreatedBy());
        return repository.save(entity);
    }

    @Transactional
    public NotificationChannelConfig update(UUID id, NotificationChannelConfigCreateRequest request) {
        NotificationChannelConfig existing = getById(id);
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getChannelType() != null) existing.setChannelType(request.getChannelType());
        if (request.getConfig() != null) existing.setConfig(request.getConfig());
        if (request.getIsShared() != null) existing.setIsShared(request.getIsShared());
        if (request.getCreatedBy() != null) existing.setCreatedBy(request.getCreatedBy());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
