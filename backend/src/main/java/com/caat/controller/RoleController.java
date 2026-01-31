package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Role;
import com.caat.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色管理控制器
 */
@Tag(name = "角色管理", description = "角色与权限管理接口")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {
    
    private final RoleService roleService;
    
    @Operation(summary = "获取所有角色", description = "获取系统中所有角色列表")
    @GetMapping
    public ApiResponse<List<Role>> getAllRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }
    
    @Operation(summary = "根据ID获取角色", description = "根据角色ID获取角色详情")
    @GetMapping("/{id}")
    public ApiResponse<Role> getRoleById(@PathVariable UUID id) {
        return ApiResponse.success(roleService.getRoleById(id));
    }
    
    @Operation(summary = "创建角色", description = "创建新角色")
    @PostMapping
    public ApiResponse<Role> createRole(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) request.get("permissions");
        
        return ApiResponse.success(roleService.createRole(name, description, permissions));
    }
    
    @Operation(summary = "更新角色", description = "更新角色信息")
    @PutMapping("/{id}")
    public ApiResponse<Role> updateRole(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request
    ) {
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) request.get("permissions");
        
        return ApiResponse.success(roleService.updateRole(id, name, description, permissions));
    }
    
    @Operation(summary = "删除角色", description = "删除指定角色")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }
}
