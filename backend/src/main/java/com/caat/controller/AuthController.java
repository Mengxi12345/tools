package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.LoginRequest;
import com.caat.dto.LoginResponse;
import com.caat.dto.RegisterRequest;
import com.caat.entity.User;
import com.caat.service.UserService;
import com.caat.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证", description = "用户认证接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT Token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.getUsername());
        
        if (!userService.validatePassword(user, request.getPassword())) {
            throw new com.caat.exception.BusinessException(com.caat.exception.ErrorCode.LOGIN_FAILED);
        }
        
        if (!user.getIsEnabled()) {
            throw new com.caat.exception.BusinessException(com.caat.exception.ErrorCode.UNAUTHORIZED);
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        return ApiResponse.success(new LoginResponse(
                token,
                user.getUsername(),
                user.getId().toString()
        ));
    }
    
    @Operation(summary = "用户注册", description = "注册新用户，注册成功后返回 Token 自动登录")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        String email = (request.getEmail() != null && !request.getEmail().trim().isEmpty())
                ? request.getEmail().trim()
                : request.getUsername() + "@example.com";
        User user = userService.createUser(
                request.getUsername(),
                request.getPassword(),
                email
        );
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        return ApiResponse.success(new LoginResponse(
                token,
                user.getUsername(),
                user.getId().toString()
        ));
    }
}
