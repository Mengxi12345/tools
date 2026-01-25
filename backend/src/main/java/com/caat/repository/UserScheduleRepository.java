package com.caat.repository;

import com.caat.entity.UserSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserScheduleRepository extends JpaRepository<UserSchedule, UUID> {
    Optional<UserSchedule> findByUserId(UUID userId);
    
    List<UserSchedule> findByIsEnabledTrue();
}
