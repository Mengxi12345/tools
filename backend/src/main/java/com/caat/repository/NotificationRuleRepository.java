package com.caat.repository;

import com.caat.entity.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    List<NotificationRule> findByIsEnabledTrueOrderByCreatedAtDesc();
}
