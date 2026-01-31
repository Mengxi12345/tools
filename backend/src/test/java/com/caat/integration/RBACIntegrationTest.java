package com.caat.integration;

import com.caat.entity.Role;
import com.caat.entity.User;
import com.caat.repository.RoleRepository;
import com.caat.repository.UserRepository;
import com.caat.service.PermissionService;
import com.caat.service.RoleService;
import com.caat.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 权限控制集成测试
 */
@SpringBootTest
@Transactional
public class RBACIntegrationTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testCreateRole() {
        // 测试创建角色
        Role role = roleService.createRole("TEST_ROLE", "测试角色", List.of("test:read", "test:write"));
        assertNotNull(role);
        assertEquals("TEST_ROLE", role.getName());
    }

    @Test
    public void testAssignRole() {
        // 测试分配角色
        User user = userService.createUser("testuser", "password", "test@example.com");
        Role role = roleRepository.findByName("USER").orElseThrow();
        
        userService.assignRoles(user.getId(), List.of(role.getId()));
        
        User updatedUser = userService.findById(user.getId());
        assertFalse(updatedUser.getRoles().isEmpty());
    }

    @Test
    public void testPermissionCheck() {
        // 测试权限检查
        User user = userService.createUser("testuser2", "password", "test2@example.com");
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        
        userService.assignRoles(user.getId(), List.of(adminRole.getId()));
        
        // 管理员应该拥有所有权限
        assertTrue(permissionService.hasPermission(user.getId(), "any:permission"));
    }
}
