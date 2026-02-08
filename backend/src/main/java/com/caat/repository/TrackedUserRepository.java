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

    /**
     * 按平台类型查询用户（含 platform 预加载），用于 TimeStore 补漏等
     */
    @Query("SELECT u FROM TrackedUser u JOIN FETCH u.platform WHERE u.platform.type = :platformType")
    List<TrackedUser> findByPlatformTypeWithPlatform(@Param("platformType") String platformType);

    List<TrackedUser> findByIsActiveTrue();

    @Query("SELECT u FROM TrackedUser u WHERE u.platform.id = :platformId AND u.userId = :userId")
    Optional<TrackedUser> findByPlatformIdAndUserId(@Param("platformId") UUID platformId, @Param("userId") String userId);

    boolean existsByPlatformIdAndUserId(UUID platformId, String userId);
}
