package com.caat.security;

import com.caat.entity.User;
import com.caat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户详情服务实现
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        
        // 构建权限列表：角色和权限
        List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
        
        // 添加角色
        user.getRoles().forEach(role -> {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.getName()));
            // 添加权限
            role.getPermissions().forEach(permission -> {
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(permission));
            });
        });
        
        // 如果没有角色，默认添加 ROLE_USER
        if (authorities.isEmpty()) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
        }
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.getIsEnabled())
                .authorities(authorities)
                .build();
    }
}
