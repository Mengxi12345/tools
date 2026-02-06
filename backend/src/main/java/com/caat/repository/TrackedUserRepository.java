package com.caat.repository;

import com.caat.entity.TrackedUser;
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
public interface TrackedUserRepository extends JpaRepository<TrackedUser, UUID> {

    /** 分页查询时一次性加载 platform，避免 N+1 */
    @Query(value = "SELECT u FROM TrackedUser u LEFT JOIN FETCH u.platform",
           countQuery = "SELECT COUNT(u) FROM TrackedUser u")
    Page<TrackedUser> findAllWithPlatform(Pageable pageable);

    List<TrackedUser> findByPlatformId(UUID platformId);

    List<TrackedUser> findByIsActiveTrue();

    Page<TrackedUser> findByGroupId(UUID groupId, Pageable pageable);

    @Query("SELECT u FROM TrackedUser u WHERE u.platform.id = :platformId AND u.userId = :userId")
    Optional<TrackedUser> findByPlatformIdAndUserId(@Param("platformId") UUID platformId, @Param("userId") String userId);

    boolean existsByPlatformIdAndUserId(UUID platformId, String userId);

    @Modifying
    @Query("UPDATE TrackedUser u SET u.groupId = null WHERE u.groupId = :groupId")
    void clearGroupId(@Param("groupId") UUID groupId);

    /**
     * 根据标签查询用户（标签包含指定值），一次性加载 platform
     */
    @Query(value = "SELECT DISTINCT u FROM TrackedUser u LEFT JOIN FETCH u.platform JOIN u.tags t WHERE t = :tag",
           countQuery = "SELECT COUNT(DISTINCT u) FROM TrackedUser u JOIN u.tags t WHERE t = :tag")
    Page<TrackedUser> findByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * 根据多个标签查询用户（标签包含任一指定值），一次性加载 platform
     */
    @Query(value = "SELECT DISTINCT u FROM TrackedUser u LEFT JOIN FETCH u.platform JOIN u.tags t WHERE t IN :tags",
           countQuery = "SELECT COUNT(DISTINCT u) FROM TrackedUser u JOIN u.tags t WHERE t IN :tags")
    Page<TrackedUser> findByTagsIn(@Param("tags") List<String> tags, Pageable pageable);

    /**
     * 统计每个标签被多少用户使用
     */
    @Query(value = "SELECT tag, COUNT(DISTINCT user_id) as count FROM tracked_user_tags GROUP BY tag", nativeQuery = true)
    List<Object[]> countUsersByTag();
}
