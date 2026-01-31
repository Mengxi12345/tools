package com.caat.service;

import com.caat.entity.User;
import com.caat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 权限检查服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {
    
    private final UserRepository userRepository;
    
    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(UUID userId, String permission) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        // 管理员拥有所有权限
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));
        if (isAdmin) {
            return true;
        }
        
        // 检查用户角色是否包含该权限
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(p -> p.equals(permission) || p.equals("*"));
    }
    
    /**
     * 检查用户是否有任一权限
     */
    public boolean hasAnyPermission(UUID userId, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(userId, permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查用户是否有所有权限
     */
    public boolean hasAllPermissions(UUID userId, String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(userId, permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取用户的所有权限
     */
    public Set<String> getUserPermissions(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }
    
    /**
     * 检查用户是否有指定角色
     */
    public boolean hasRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }
}
