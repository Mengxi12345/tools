package com.caat.service;

import com.caat.entity.UserGroup;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.TrackedUserRepository;
import com.caat.repository.UserGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户分组管理服务
 */
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final TrackedUserRepository trackedUserRepository;

    public List<UserGroup> getAllGroups() {
        return userGroupRepository.findAllByOrderBySortOrderAsc();
    }

    public UserGroup getById(UUID id) {
        return userGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public UserGroup create(String name, String description, Integer sortOrder) {
        UserGroup g = new UserGroup();
        g.setName(name);
        g.setDescription(description);
        g.setSortOrder(sortOrder != null ? sortOrder : 0);
        return userGroupRepository.save(g);
    }

    @Transactional
    public UserGroup update(UUID id, String name, String description, Integer sortOrder) {
        UserGroup g = getById(id);
        if (name != null) g.setName(name);
        if (description != null) g.setDescription(description);
        if (sortOrder != null) g.setSortOrder(sortOrder);
        return userGroupRepository.save(g);
    }

    @Transactional
    public void delete(UUID id) {
        UserGroup g = getById(id);
        trackedUserRepository.clearGroupId(id);
        userGroupRepository.delete(g);
    }
}
