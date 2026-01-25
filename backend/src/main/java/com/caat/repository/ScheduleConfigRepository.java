package com.caat.repository;

import com.caat.entity.ScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleConfigRepository extends JpaRepository<ScheduleConfig, UUID> {
    Optional<ScheduleConfig> findFirstByOrderByCreatedAtAsc();
}
