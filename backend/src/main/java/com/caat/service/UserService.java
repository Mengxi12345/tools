package com.caat.service;

import com.caat.entity.Role;
import com.caat.entity.User;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.RoleRepository;
import com.caat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户认证服务
 */
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    
    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * 创建用户
     */
    @Transactional
    public User createUser(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setIsEnabled(true);
        
        return userRepository.save(user);
    }
    
    /**
     * 验证用户密码
     */
    public boolean validatePassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
    
    /**
     * 根据ID获取用户
     */
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * 为用户分配角色
     */
    @Transactional
    public User assignRoles(UUID userId, List<UUID> roleIds) {
        User user = findById(userId);
        List<Role> roles = roleRepository.findAllById(roleIds);
        user.setRoles(roles);
        return userRepository.save(user);
    }
    
    /**
     * 移除用户角色
     */
    @Transactional
    public User removeRoles(UUID userId, List<UUID> roleIds) {
        User user = findById(userId);
        user.getRoles().removeIf(role -> roleIds.contains(role.getId()));
        return userRepository.save(user);
    }
}
