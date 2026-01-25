package com.caat.repository;

import com.caat.entity.TrackedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackedUserRepository extends JpaRepository<TrackedUser, UUID> {
    List<TrackedUser> findByPlatformId(UUID platformId);
    
    List<TrackedUser> findByIsActiveTrue();
    
    @Query("SELECT u FROM TrackedUser u WHERE u.platform.id = :platformId AND u.userId = :userId")
    Optional<TrackedUser> findByPlatformIdAndUserId(@Param("platformId") UUID platformId, @Param("userId") String userId);
    
    boolean existsByPlatformIdAndUserId(UUID platformId, String userId);
}
