package com.caat.service;

import com.caat.entity.FetchTask;
import com.caat.entity.ScheduleConfig;
import com.caat.entity.TrackedUser;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.ScheduleConfigRepository;
import com.caat.repository.TrackedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 定时任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** 内容拉取定时任务执行间隔（与 QuartzConfig 中一致） */
    public static final String SCHEDULE_INTERVAL_DESCRIPTION = "每10分钟";

    /** Quartz 中内容拉取触发器的名称（与 QuartzConfig 中一致） */
    private static final String CONTENT_FETCH_TRIGGER_NAME = "contentFetchTrigger";

    private final ScheduleConfigRepository scheduleConfigRepository;
    private final FetchTaskRepository fetchTaskRepository;
    private final TrackedUserRepository trackedUserRepository;
    private final Scheduler scheduler;

    /**
     * 获取全局定时任务状态
     */
    public boolean getGlobalScheduleStatus() {
        return scheduleConfigRepository.findByType(ScheduleConfig.ConfigType.GLOBAL)
            .map(ScheduleConfig::getIsEnabled)
            .orElse(true);
    }
    
    /**
     * 启用/禁用全局定时任务
     */
    @Transactional
    public void setGlobalScheduleStatus(boolean enabled) {
        ScheduleConfig config = scheduleConfigRepository.findByType(ScheduleConfig.ConfigType.GLOBAL)
            .orElseGet(() -> {
                ScheduleConfig newConfig = new ScheduleConfig();
                newConfig.setType(ScheduleConfig.ConfigType.GLOBAL);
                newConfig.setIsEnabled(enabled);
                return scheduleConfigRepository.save(newConfig);
            });
        config.setIsEnabled(enabled);
        scheduleConfigRepository.save(config);
    }
    
    /**
     * 启用/禁用用户定时任务
     */
    @Transactional
    public void setUserScheduleStatus(UUID userId, boolean enabled) {
        ScheduleConfig config = scheduleConfigRepository.findByTypeAndUserId(ScheduleConfig.ConfigType.USER, userId)
            .orElseGet(() -> {
                ScheduleConfig newConfig = new ScheduleConfig();
                newConfig.setType(ScheduleConfig.ConfigType.USER);
                newConfig.setUserId(userId);
                newConfig.setIsEnabled(enabled);
                return scheduleConfigRepository.save(newConfig);
            });
        config.setIsEnabled(enabled);
        scheduleConfigRepository.save(config);
    }

    /**
     * 获取各用户定时任务当前状态：userId -> isEnabled。
     * 无配置的用户视为已启用（与 ContentFetchJob 逻辑一致）。
     */
    public Map<UUID, Boolean> getUsersScheduleStatus() {
        List<TrackedUser> users = trackedUserRepository.findAll();
        List<ScheduleConfig> userConfigs = scheduleConfigRepository.findByTypeOrderByCreatedAtAsc(ScheduleConfig.ConfigType.USER);
        Map<UUID, Boolean> configMap = userConfigs.stream()
            .filter(c -> c.getUserId() != null)
            .collect(Collectors.toMap(ScheduleConfig::getUserId, ScheduleConfig::getIsEnabled, (a, b) -> b));
        Map<UUID, Boolean> result = new HashMap<>();
        for (TrackedUser user : users) {
            result.put(user.getId(), configMap.getOrDefault(user.getId(), true));
        }
        return result;
    }

    /**
     * 获取定时任务执行状态详情（用于仪表盘/状态页）
     */
    public Map<String, Object> getScheduleStatusDetail() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("isEnabled", getGlobalScheduleStatus());
        detail.put("interval", SCHEDULE_INTERVAL_DESCRIPTION);
        detail.put("triggerName", CONTENT_FETCH_TRIGGER_NAME);
        detail.put("jobName", "contentFetchJob");

        try {
            TriggerKey triggerKey = new TriggerKey(CONTENT_FETCH_TRIGGER_NAME, Scheduler.DEFAULT_GROUP);
            var trigger = scheduler.getTrigger(triggerKey);
            if (trigger != null) {
                java.util.Date next = trigger.getNextFireTime();
                if (next != null) {
                    ZonedDateTime nextZdt = Instant.ofEpochMilli(next.getTime()).atZone(ZoneId.systemDefault());
                    detail.put("nextFireTime", nextZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    detail.put("nextFireTimeEpoch", next.getTime());
                }
                java.util.Date prev = trigger.getPreviousFireTime();
                if (prev != null) {
                    ZonedDateTime prevZdt = Instant.ofEpochMilli(prev.getTime()).atZone(ZoneId.systemDefault());
                    detail.put("previousFireTime", prevZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            }
        } catch (SchedulerException e) {
            log.warn("获取 Quartz 触发器下次执行时间失败", e);
            detail.put("nextFireTimeError", e.getMessage());
        }

        java.util.List<FetchTask> running = fetchTaskRepository.findByStatus(FetchTask.TaskStatus.RUNNING);
        long scheduledRunning = running.stream()
            .filter(t -> t.getTaskType() == FetchTask.TaskType.SCHEDULED)
            .count();
        detail.put("runningCount", running.size());
        detail.put("scheduledRunningCount", scheduledRunning);

        fetchTaskRepository.findFirstByTaskTypeOrderByCreatedAtDesc(FetchTask.TaskType.SCHEDULED)
            .ifPresent(last -> {
                detail.put("lastScheduledRunAt", last.getCreatedAt());
                detail.put("lastScheduledStatus", last.getStatus() != null ? last.getStatus().name() : null);
                detail.put("lastScheduledCompletedAt", last.getCompletedAt());
                detail.put("lastScheduledFetchedCount", last.getFetchedCount());
            });
        return detail;
    }
}
