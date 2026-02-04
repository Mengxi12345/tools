package com.caat.service;

import com.caat.entity.ExportTask;
import com.caat.repository.ExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}
