package com.caat.repository;

import com.caat.entity.FetchTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FetchTaskRepository extends JpaRepository<FetchTask, UUID> {
    List<FetchTask> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<FetchTask> findByStatus(FetchTask.TaskStatus status);
    
    Page<FetchTask> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    Page<FetchTask> findByTaskTypeOrderByCreatedAtDesc(FetchTask.TaskType taskType, Pageable pageable);

    /** 最近一次定时任务的执行记录（用于状态页展示） */
    Optional<FetchTask> findFirstByTaskTypeOrderByCreatedAtDesc(FetchTask.TaskType taskType);

    /** 所有刷新任务（含手动/定时），按创建时间倒序，一次性加载 user 避免 N+1 */
    @Query(value = "SELECT t FROM FetchTask t LEFT JOIN FETCH t.user ORDER BY t.createdAt DESC",
           countQuery = "SELECT COUNT(t) FROM FetchTask t")
    Page<FetchTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按 ID 查询并加载 user，避免序列化时懒加载 */
    @Query("SELECT t FROM FetchTask t LEFT JOIN FETCH t.user WHERE t.id = :id")
    Optional<FetchTask> findByIdWithUser(@Param("id") UUID id);

    /** 按类型删除所有任务记录，返回删除条数 */
    @Modifying
    @Query("DELETE FROM FetchTask t WHERE t.taskType = :taskType")
    int deleteByTaskType(@Param("taskType") FetchTask.TaskType taskType);
}
