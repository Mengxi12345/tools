package com.caat.service;

import com.caat.entity.FetchTask;
import com.caat.repository.FetchTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 刷新任务进度更新服务
 * 使用 REQUIRES_NEW 事务，确保进度更新立即提交，前端轮询能获取到最新进度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchTaskProgressUpdater {

    private final FetchTaskRepository fetchTaskRepository;

    /**
     * 更新任务为运行中（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusRunning(UUID taskId, LocalDateTime startedAt) {
        FetchTask task = fetchTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(FetchTask.TaskStatus.RUNNING);
            if (startedAt != null) {
                task.setStartedAt(startedAt);
            }
            task.setProgress(0);
            task.setFetchedCount(0);
            fetchTaskRepository.saveAndFlush(task);
            log.debug("刷新任务状态已立即提交: taskId={}, status=RUNNING", taskId);
        } else {
            log.warn("无法更新刷新任务状态：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务开始/结束时间（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStartEndTime(UUID taskId, LocalDateTime startTime, LocalDateTime endTime) {
        FetchTask task = fetchTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            if (startTime != null) {
                task.setStartTime(startTime);
            }
            if (endTime != null) {
                task.setEndTime(endTime);
            }
            fetchTaskRepository.saveAndFlush(task);
        }
    }

    /**
     * 更新任务进度（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID taskId, int progress, int fetchedCount, Integer totalCount) {
        FetchTask task = fetchTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setProgress(progress);
            task.setFetchedCount(fetchedCount);
            if (totalCount != null) {
                task.setTotalCount(totalCount);
            }
            fetchTaskRepository.saveAndFlush(task);
        }
    }

    /**
     * 更新任务完成状态（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCompleted(UUID taskId, LocalDateTime completedAt, int fetchedCount, Integer totalCount) {
        FetchTask task = fetchTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(FetchTask.TaskStatus.COMPLETED);
            if (completedAt != null) {
                task.setCompletedAt(completedAt);
            }
            task.setProgress(100);
            task.setFetchedCount(fetchedCount);
            if (totalCount != null) {
                task.setTotalCount(totalCount);
            }
            fetchTaskRepository.saveAndFlush(task);
            log.debug("刷新任务完成状态已立即提交: taskId={}, fetchedCount={}", taskId, fetchedCount);
        } else {
            log.warn("无法更新刷新任务完成状态：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务失败状态（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(UUID taskId, LocalDateTime completedAt, String errorMessage) {
        FetchTask task = fetchTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(FetchTask.TaskStatus.FAILED);
            if (completedAt != null) {
                task.setCompletedAt(completedAt);
            }
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            fetchTaskRepository.saveAndFlush(task);
            log.debug("刷新任务失败状态已立即提交: taskId={}, errorMessage={}", taskId, errorMessage);
        } else {
            log.warn("无法更新刷新任务失败状态：任务不存在，taskId={}", taskId);
        }
    }
}
