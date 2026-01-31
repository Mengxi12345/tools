package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.UserGroup;
import com.caat.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "用户分组", description = "用户分组管理接口")
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class UserGroupController {

    private final UserGroupService userGroupService;

    @Operation(summary = "获取全部分组")
    @GetMapping
    public ApiResponse<List<UserGroup>> getAll() {
        return ApiResponse.success(userGroupService.getAllGroups());
    }

    @Operation(summary = "根据ID获取分组")
    @GetMapping("/{id}")
    public ApiResponse<UserGroup> getById(@PathVariable UUID id) {
        return ApiResponse.success(userGroupService.getById(id));
    }

    @Operation(summary = "创建分组")
    @PostMapping
    public ApiResponse<UserGroup> create(@RequestBody UserGroupRequest req) {
        return ApiResponse.success(userGroupService.create(req.getName(), req.getDescription(), req.getSortOrder()));
    }

    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public ApiResponse<UserGroup> update(@PathVariable UUID id, @RequestBody UserGroupRequest req) {
        return ApiResponse.success(userGroupService.update(id, req.getName(), req.getDescription(), req.getSortOrder()));
    }

    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userGroupService.delete(id);
        return ApiResponse.success(null);
    }

    public static class UserGroupRequest {
        private String name;
        private String description;
        private Integer sortOrder;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
