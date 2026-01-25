package com.caat.service;

import com.caat.entity.ScheduleConfig;
import com.caat.entity.UserSchedule;
import com.caat.repository.ScheduleConfigRepository;
import com.caat.repository.UserScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 定时任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {
    
    private final ScheduleConfigRepository scheduleConfigRepository;
    private final UserScheduleRepository userScheduleRepository;
    
    /**
     * 获取全局定时任务状态
     */
    public boolean getGlobalScheduleStatus() {
        return scheduleConfigRepository.findFirstByOrderByCreatedAtAsc()
            .map(ScheduleConfig::getIsGlobalEnabled)
            .orElse(true);
    }
    
    /**
     * 启用/禁用全局定时任务
     */
    @Transactional
    public void setGlobalScheduleStatus(boolean enabled) {
        ScheduleConfig config = scheduleConfigRepository.findFirstByOrderByCreatedAtAsc()
            .orElseGet(() -> {
                ScheduleConfig newConfig = new ScheduleConfig();
                newConfig.setIsGlobalEnabled(enabled);
                return scheduleConfigRepository.save(newConfig);
            });
        config.setIsGlobalEnabled(enabled);
        scheduleConfigRepository.save(config);
    }
    
    /**
     * 启用/禁用用户定时任务
     */
    @Transactional
    public void setUserScheduleStatus(UUID userId, boolean enabled) {
        UserSchedule schedule = userScheduleRepository.findByUserId(userId)
            .orElseGet(() -> {
                // TODO: 需要先获取 TrackedUser
                UserSchedule newSchedule = new UserSchedule();
                newSchedule.setIsEnabled(enabled);
                return userScheduleRepository.save(newSchedule);
            });
        schedule.setIsEnabled(enabled);
        userScheduleRepository.save(schedule);
    }
}
