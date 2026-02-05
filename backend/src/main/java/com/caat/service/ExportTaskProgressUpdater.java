package com.caat.service;

import com.caat.entity.ExportTask;
import com.caat.repository.ExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 导出任务进度更新服务
 * 使用 REQUIRES_NEW 事务，确保进度更新立即提交，前端轮询能获取到最新进度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskProgressUpdater {

    private final ExportTaskRepository exportTaskRepository;

    /**
     * 更新任务进度和日志（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID taskId, int progress, String logMessagesJson) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setProgress(progress);
            task.setLogMessages(logMessagesJson);
            exportTaskRepository.saveAndFlush(task);
        } else {
            log.warn("无法更新任务进度：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务状态、进度和日志（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID taskId, ExportTask.TaskStatus status, Integer progress, String logMessagesJson) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(status);
            if (progress != null) {
                task.setProgress(progress);
            }
            if (logMessagesJson != null) {
                task.setLogMessages(logMessagesJson);
            }
            exportTaskRepository.saveAndFlush(task);
            log.debug("任务状态已立即提交: taskId={}, status={}, progress={}", taskId, status, progress);
        } else {
            log.warn("无法更新任务状态：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务状态、开始时间、进度和日志（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusWithStartedAt(UUID taskId, ExportTask.TaskStatus status, LocalDateTime startedAt, 
                                         Integer progress, String logMessagesJson) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(status);
            if (startedAt != null) {
                task.setStartedAt(startedAt);
            }
            if (progress != null) {
                task.setProgress(progress);
            }
            if (logMessagesJson != null) {
                task.setLogMessages(logMessagesJson);
            }
            exportTaskRepository.saveAndFlush(task);
            log.debug("任务状态已立即提交: taskId={}, status={}, startedAt={}, progress={}", 
                    taskId, status, startedAt, progress);
        } else {
            log.warn("无法更新任务状态：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务完成状态（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCompleted(UUID taskId, ExportTask.TaskStatus status, LocalDateTime completedAt, 
                               String filePath, Long fileSize, Integer progress, String logMessagesJson) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(status);
            if (completedAt != null) {
                task.setCompletedAt(completedAt);
            }
            if (filePath != null) {
                task.setFilePath(filePath);
            }
            if (fileSize != null) {
                task.setFileSize(fileSize);
            }
            if (progress != null) {
                task.setProgress(progress);
            }
            if (logMessagesJson != null) {
                task.setLogMessages(logMessagesJson);
            }
            exportTaskRepository.saveAndFlush(task);
            log.debug("任务完成状态已立即提交: taskId={}, status={}, fileSize={}", taskId, status, fileSize);
        } else {
            log.warn("无法更新任务完成状态：任务不存在，taskId={}", taskId);
        }
    }

    /**
     * 更新任务失败状态（立即提交，不依赖外部事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(UUID taskId, LocalDateTime completedAt, String errorMessage, String logMessagesJson) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(ExportTask.TaskStatus.FAILED);
            if (completedAt != null) {
                task.setCompletedAt(completedAt);
            }
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            if (logMessagesJson != null) {
                task.setLogMessages(logMessagesJson);
            }
            exportTaskRepository.saveAndFlush(task);
            log.debug("任务失败状态已立即提交: taskId={}, errorMessage={}", taskId, errorMessage);
        } else {
            log.warn("无法更新任务失败状态：任务不存在，taskId={}", taskId);
        }
    }
}
