package com.caat.repository;

import com.caat.entity.ScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleConfigRepository extends JpaRepository<ScheduleConfig, UUID> {
    Optional<ScheduleConfig> findByType(ScheduleConfig.ConfigType type);
    Optional<ScheduleConfig> findByTypeAndUserId(ScheduleConfig.ConfigType type, UUID userId);
    /** 所有指定类型的配置（用于批量查询用户定时任务状态） */
    List<ScheduleConfig> findByTypeOrderByCreatedAtAsc(ScheduleConfig.ConfigType type);
    Optional<ScheduleConfig> findFirstByOrderByCreatedAtAsc();
}
