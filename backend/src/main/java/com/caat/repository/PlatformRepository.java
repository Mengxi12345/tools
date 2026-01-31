package com.caat.repository;

import com.caat.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, UUID> {
    Optional<Platform> findByName(String name);
    boolean existsByName(String name);
    /** 是否存在同名且 ID 不同的平台（用于更新时排除自身） */
    boolean existsByNameAndIdNot(String name, UUID id);
}
