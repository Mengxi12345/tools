package com.caat.repository;

import com.caat.entity.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {
    Optional<Content> findByHash(String hash);
    
    boolean existsByHash(String hash);

    /** 分页查询时一次性加载 platform、user，避免 N+1 */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user",
           countQuery = "SELECT COUNT(c) FROM Content c")
    Page<Content> findAllWithPlatformAndUser(Pageable pageable);

    /** 按用户分页，一次性加载 platform、user */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.user.id = :userId",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId")
    Page<Content> findByUserIdWithPlatformAndUser(@Param("userId") UUID userId, Pageable pageable);

    /** 按平台分页，一次性加载 platform、user */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.platform.id = :platformId",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.platform.id = :platformId")
    Page<Content> findByPlatformIdWithPlatformAndUser(@Param("platformId") UUID platformId, Pageable pageable);

    /** 按类型分页，一次性加载 platform、user */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.contentType = :contentType",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.contentType = :contentType")
    Page<Content> findByContentTypeWithPlatformAndUser(@Param("contentType") Content.ContentType contentType, Pageable pageable);

    /** 关键词搜索，一次性加载 platform、user，按发布时间倒序 */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY c.publishedAt DESC",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Content> searchByKeywordWithPlatformAndUser(@Param("q") String q, Pageable pageable);
    
    /** 关键词搜索 + 平台过滤，一次性加载 platform、user，按发布时间倒序 */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.platform.id = :platformId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY c.publishedAt DESC",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.platform.id = :platformId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Content> searchByKeywordAndPlatformIdWithPlatformAndUser(@Param("q") String q, @Param("platformId") UUID platformId, Pageable pageable);
    
    /** 关键词搜索 + 用户过滤，一次性加载 platform、user，按发布时间倒序 */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.user.id = :userId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY c.publishedAt DESC",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Content> searchByKeywordAndUserIdWithPlatformAndUser(@Param("q") String q, @Param("userId") UUID userId, Pageable pageable);
    
    /** 关键词搜索 + 平台 + 用户过滤，一次性加载 platform、user，按发布时间倒序 */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.platform.id = :platformId AND c.user.id = :userId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY c.publishedAt DESC",
           countQuery = "SELECT COUNT(c) FROM Content c WHERE c.platform.id = :platformId AND c.user.id = :userId AND (LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Content> searchByKeywordAndPlatformIdAndUserIdWithPlatformAndUser(@Param("q") String q, @Param("platformId") UUID platformId, @Param("userId") UUID userId, Pageable pageable);
    
    Page<Content> findByUserId(UUID userId, Pageable pageable);
    
    Page<Content> findByPlatformId(UUID platformId, Pageable pageable);
    
    @Query("SELECT c FROM Content c WHERE c.user.id = :userId AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime")
    List<Content> findByUserIdAndPublishedAtBetween(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /** 按用户、发布时间范围分页（platformId 为空时使用，避免 PostgreSQL 无法推断 null 参数类型） */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
        "WHERE c.user.id = :userId AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime",
        countQuery = "SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId " +
            "AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime")
    Page<Content> findByUserIdAndPublishedAtBetweenWithPlatformAndUser(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /** 按平台、用户、发布时间范围分页（用于内容管理「某月/某年文章」） */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
        "WHERE (:platformId IS NULL OR c.platform.id = :platformId) AND c.user.id = :userId " +
        "AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime",
        countQuery = "SELECT COUNT(c) FROM Content c WHERE (:platformId IS NULL OR c.platform.id = :platformId) AND c.user.id = :userId " +
            "AND c.publishedAt >= :startTime AND c.publishedAt <= :endTime")
    Page<Content> findByPlatformIdAndUserIdAndPublishedAtBetween(
        @Param("platformId") UUID platformId,
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /** 按平台、用户、年-月聚合数量，用于内容管理树形展示（平台→用户→月）。返回 Object[]: platformId, platformName, userId, username, year, month, count */
    @Query(value = "SELECT c.platform_id AS platform_id, p.name AS platform_name, c.user_id AS user_id, u.username AS username, " +
        "EXTRACT(YEAR FROM c.published_at) AS y, EXTRACT(MONTH FROM c.published_at) AS m, COUNT(*) AS cnt " +
        "FROM contents c JOIN platforms p ON c.platform_id = p.id JOIN tracked_users u ON c.user_id = u.id " +
        "GROUP BY c.platform_id, p.name, c.user_id, u.username, EXTRACT(YEAR FROM c.published_at), EXTRACT(MONTH FROM c.published_at) " +
        "ORDER BY p.name, u.username, y DESC, m DESC", nativeQuery = true)
    List<Object[]> findGroupedCountsByPlatformUserYearMonth();
    
    @Query("SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId")
    Long countByUserId(@Param("userId") UUID userId);

    /** 批量统计各用户文章数，返回 (userId, count) */
    @Query("SELECT c.user.id, COUNT(c) FROM Content c WHERE c.user.id IN :userIds GROUP BY c.user.id")
    List<Object[]> countByUserIds(@Param("userIds") List<UUID> userIds);

    /** 该用户已存文章中最新一条的发布时间，用于增量拉取（无则全量） */
    @Query("SELECT MAX(c.publishedAt) FROM Content c WHERE c.user.id = :userId")
    Optional<LocalDateTime> findMaxPublishedAtByUserId(@Param("userId") UUID userId);

    /** 该用户已存文章中最新一条（按发布时间倒序），用于知识星球第二步「遇此文即停」 */
    Optional<Content> findTop1ByUserIdOrderByPublishedAtDesc(UUID userId);
    
    @Query("SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId AND c.isRead = false")
    Long countUnreadByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM Content c WHERE c.user.id = :userId AND c.isFavorite = true")
    Long countFavoriteByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM Content c WHERE c.isRead = false")
    Long countUnread();

    @Query("SELECT COUNT(c) FROM Content c WHERE c.isFavorite = true")
    Long countFavorite();

    @Query("SELECT c.platform.name, COUNT(c) FROM Content c GROUP BY c.platform.name")
    List<Object[]> countByPlatform();

    @Query("SELECT c FROM Content c WHERE LOWER(COALESCE(c.title,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(c.body,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Content> searchByKeyword(@Param("q") String q, Pageable pageable);
    
    /**
     * 根据标签查询内容
     */
    @Query("SELECT DISTINCT c FROM Content c JOIN c.tags t WHERE t.id = :tagId")
    Page<Content> findByTagId(@Param("tagId") UUID tagId, Pageable pageable);
    
    /**
     * 根据多个标签查询内容（包含任一标签）
     */
    @Query("SELECT DISTINCT c FROM Content c JOIN c.tags t WHERE t.id IN :tagIds")
    Page<Content> findByTagIds(@Param("tagIds") List<UUID> tagIds, Pageable pageable);
    
    /**
     * 根据标签查询内容（使用子查询避免DISTINCT与ORDER BY冲突）
     */
    @Query("SELECT c FROM Content c WHERE c.id IN (SELECT DISTINCT c2.id FROM Content c2 JOIN c2.tags t WHERE t.id = :tagId)")
    Page<Content> findByTagIdFixed(@Param("tagId") UUID tagId, Pageable pageable);
    
    /**
     * 根据多个标签查询内容（使用子查询避免DISTINCT与ORDER BY冲突）
     */
    @Query("SELECT c FROM Content c WHERE c.id IN (SELECT DISTINCT c2.id FROM Content c2 JOIN c2.tags t WHERE t.id IN :tagIds)")
    Page<Content> findByTagIdsFixed(@Param("tagIds") List<UUID> tagIds, Pageable pageable);
    
    /**
     * 根据内容类型查询
     */
    Page<Content> findByContentType(Content.ContentType contentType, Pageable pageable);
    
    /**
     * 统计各平台的内容数量
     */
    @Query("SELECT c.platform.id, COUNT(c) FROM Content c GROUP BY c.platform.id")
    List<Object[]> countByPlatformGrouped();
    
    /**
     * 统计各作者的内容数量
     */
    @Query("SELECT c.user.id, COUNT(c) FROM Content c GROUP BY c.user.id")
    List<Object[]> countByAuthorGrouped();
    
    /**
     * 统计各标签的内容数量
     */
    @Query("SELECT t.id, COUNT(c) FROM Content c JOIN c.tags t GROUP BY t.id")
    List<Object[]> countByTagGrouped();
    
    /**
     * 内容时间分布统计（按天）
     */
    @Query(value = "SELECT DATE(published_at) as period, COUNT(*) as count " +
            "FROM contents WHERE published_at >= :startDate AND published_at <= :endDate " +
            "GROUP BY DATE(published_at) ORDER BY period", nativeQuery = true)
    List<Object[]> findTimeDistribution(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * 内容类型分布统计
     */
    @Query("SELECT c.contentType, COUNT(c) FROM Content c GROUP BY c.contentType")
    List<Object[]> findContentTypeDistribution();
    
    /**
     * 活跃用户排行（按内容数量）
     */
    @Query(value = "SELECT c.user_id, u.username, COUNT(c.id) as count " +
           "FROM contents c " +
           "LEFT JOIN tracked_users u ON c.user_id = u.id " +
           "GROUP BY c.user_id, u.username " +
           "ORDER BY count DESC " +
           "LIMIT :limit", nativeQuery = true)
    List<Object[]> findActiveUsersRanking(@Param("limit") int limit);
    
    /**
     * 内容增长趋势（按天）
     */
    @Query(value = "SELECT DATE(published_at) as date, COUNT(*) as count " +
            "FROM contents WHERE published_at >= :startDate AND published_at <= :endDate " +
            "GROUP BY DATE(published_at) ORDER BY date", nativeQuery = true)
    List<Object[]> findContentGrowthTrend(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /** 按作者（追踪用户）删除该用户下的全部内容 */
    @Modifying
    @Query("DELETE FROM Content c WHERE c.user.id = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** 按平台类型查询所有内容（用于 TimeStore 图片修复等） */
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user WHERE c.platform.type = :platformType")
    List<Content> findByPlatformTypeWithPlatformAndUser(@Param("platformType") String platformType);
    
    /**
     * 获取上一篇内容（按发布时间倒序，发布时间小于当前内容）
     */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
            "WHERE c.publishedAt < :publishedAt ORDER BY c.publishedAt DESC")
    List<Content> findPreviousContent(@Param("publishedAt") LocalDateTime publishedAt, Pageable pageable);
    
    /**
     * 获取下一篇内容（按发布时间正序，发布时间大于当前内容）
     */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
            "WHERE c.publishedAt > :publishedAt ORDER BY c.publishedAt ASC")
    List<Content> findNextContent(@Param("publishedAt") LocalDateTime publishedAt, Pageable pageable);
    
    /**
     * 获取同一用户的上一篇内容（按发布时间倒序）
     */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
            "WHERE c.user.id = :userId AND c.publishedAt < :publishedAt ORDER BY c.publishedAt DESC")
    List<Content> findPreviousContentByUser(@Param("userId") UUID userId, @Param("publishedAt") LocalDateTime publishedAt, Pageable pageable);
    
    /**
     * 获取同一用户的下一篇内容（按发布时间正序）
     */
    @Query(value = "SELECT c FROM Content c LEFT JOIN FETCH c.platform LEFT JOIN FETCH c.user " +
            "WHERE c.user.id = :userId AND c.publishedAt > :publishedAt ORDER BY c.publishedAt ASC")
    List<Content> findNextContentByUser(@Param("userId") UUID userId, @Param("publishedAt") LocalDateTime publishedAt, Pageable pageable);
}
