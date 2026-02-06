package com.caat.job;

import com.caat.entity.FetchTask;
import com.caat.entity.ScheduleConfig;
import com.caat.entity.TrackedUser;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.ScheduleConfigRepository;
import com.caat.repository.TrackedUserRepository;
import com.caat.service.ContentFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容拉取定时任务
 * 定时执行内容拉取任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentFetchJob implements Job {
    
    private final ScheduleConfigRepository scheduleConfigRepository;
    private final TrackedUserRepository trackedUserRepository;
    private final FetchTaskRepository fetchTaskRepository;
    private final ContentFetchService contentFetchService;
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("开始执行定时内容拉取任务");
        
        try {
            // 检查全局定时任务是否启用
            ScheduleConfig globalConfig = scheduleConfigRepository.findByType(ScheduleConfig.ConfigType.GLOBAL)
                .orElse(null);
            
            if (globalConfig == null || !globalConfig.getIsEnabled()) {
                log.info("全局定时任务未启用，跳过执行");
                return;
            }
            
            // 获取所有启用的用户
            List<TrackedUser> enabledUsers = trackedUserRepository.findByIsActiveTrue();
            
            if (enabledUsers.isEmpty()) {
                log.info("没有启用的追踪用户，跳过执行");
                return;
            }
            
            log.info("开始为 {} 个用户拉取内容", enabledUsers.size());
            
            int successCount = 0;
            int failCount = 0;
            
            // 为每个用户拉取内容
            for (TrackedUser user : enabledUsers) {
                try {
                    // 检查用户是否启用定时任务
                    ScheduleConfig userConfig = scheduleConfigRepository
                        .findByTypeAndUserId(ScheduleConfig.ConfigType.USER, user.getId())
                        .orElse(null);
                    
                    if (userConfig != null && !userConfig.getIsEnabled()) {
                        log.debug("用户 {} 的定时任务未启用，跳过", user.getUsername());
                        continue;
                    }
                    
                    // 计算拉取时间范围（从上次拉取时间到现在）
                    LocalDateTime startTime = user.getLastFetchedAt();
                    LocalDateTime endTime = LocalDateTime.now();
                    
                    // 为定时任务创建任务记录
                    FetchTask task = new FetchTask();
                    task.setUser(user);
                    task.setTaskType(FetchTask.TaskType.SCHEDULED);
                    task.setStartTime(startTime);
                    task.setEndTime(endTime);
                    task.setStatus(FetchTask.TaskStatus.PENDING);
                    task = fetchTaskRepository.save(task);
                    
                    // 异步执行拉取任务（传入已创建的任务ID）
                    contentFetchService.fetchUserContentAsync(
                        user.getId(),
                        startTime,
                        endTime,
                        task.getId()
                    );
                    
                    successCount++;
                    log.debug("已提交用户 {} 的内容拉取任务", user.getUsername());
                    
                } catch (Exception e) {
                    failCount++;
                    log.error("为用户 {} 提交拉取任务失败", user.getUsername(), e);
                }
            }
            
            log.info("定时内容拉取任务执行完成: 成功={}, 失败={}, 总计={}", 
                successCount, failCount, enabledUsers.size());
            
        } catch (Exception e) {
            log.error("定时内容拉取任务执行失败", e);
            throw new JobExecutionException("定时内容拉取任务执行失败", e);
        }
    }
}
