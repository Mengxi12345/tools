package com.caat.service;

import com.caat.entity.NotificationRule;
import com.caat.repository.NotificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationRuleService {

    private final NotificationRuleRepository notificationRuleRepository;

    public Page<NotificationRule> findAll(Pageable pageable) {
        return notificationRuleRepository.findAll(pageable);
    }

    public List<NotificationRule> findAllEnabled() {
        return notificationRuleRepository.findByIsEnabledTrueOrderByCreatedAtDesc();
    }

    public NotificationRule getById(UUID id) {
        return notificationRuleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("通知规则不存在: " + id));
    }

    @Transactional
    public NotificationRule create(NotificationRule rule) {
        return notificationRuleRepository.save(rule);
    }

    @Transactional
    public NotificationRule update(UUID id, NotificationRule rule) {
        NotificationRule existing = getById(id);
        if (rule.getName() != null) existing.setName(rule.getName());
        if (rule.getRuleType() != null) existing.setRuleType(rule.getRuleType());
        if (rule.getConfig() != null) existing.setConfig(rule.getConfig());
        if (rule.getIsEnabled() != null) existing.setIsEnabled(rule.getIsEnabled());
        return notificationRuleRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        notificationRuleRepository.deleteById(id);
    }
}
