package com.caat.service;

import com.caat.entity.Role;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    
    private final RoleRepository roleRepository;
    
    /**
     * 获取所有角色
     */
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
    
    /**
     * 根据ID获取角色
     */
    public Role getRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }
    
    /**
     * 根据名称获取角色
     */
    public Role getRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在: " + name));
    }
    
    /**
     * 创建角色
     */
    @Transactional
    public Role createRole(String name, String description, List<String> permissions) {
        if (roleRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色已存在: " + name);
        }
        
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissions != null ? permissions : List.of());
        
        return roleRepository.save(role);
    }
    
    /**
     * 更新角色
     */
    @Transactional
    public Role updateRole(UUID id, String name, String description, List<String> permissions) {
        Role role = getRoleById(id);
        
        if (!role.getName().equals(name) && roleRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色名称已存在: " + name);
        }
        
        role.setName(name);
        role.setDescription(description);
        if (permissions != null) {
            role.setPermissions(permissions);
        }
        
        return roleRepository.save(role);
    }
    
    /**
     * 删除角色
     */
    @Transactional
    public void deleteRole(UUID id) {
        Role role = getRoleById(id);
        roleRepository.delete(role);
    }
}
