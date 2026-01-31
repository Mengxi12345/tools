package com.caat.adapter;

import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.adapter.model.PlatformUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台适配器接口
 * 所有平台适配器必须实现此接口
 */
public interface PlatformAdapter {
    
    /**
     * 获取平台类型
     */
    String getPlatformType();
    
    /**
     * 测试平台连接
     * @param config 平台配置（包含认证信息等）
     * @return 是否连接成功
     */
    boolean testConnection(Map<String, Object> config) throws BusinessException;
    
    /**
     * 获取用户信息
     * @param userId 平台用户 ID 或用户名
     * @param config 平台配置
     * @return 用户信息
     */
    PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException;
    
    /**
     * 获取用户内容
     * @param userId 平台用户 ID
     * @param config 平台配置
     * @param startTime 起始时间（可选，用于增量拉取）
     * @param endTime 结束时间（可选，默认为当前时间）
     * @param cursor 分页游标（可选，用于获取下一页）
     * @param limit 每页数量限制
     * @return 拉取结果
     */
    FetchResult getUserContents(
        String userId,
        Map<String, Object> config,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String cursor,
        Integer limit
    ) throws BusinessException;
    
    /**
     * 验证用户 ID 或用户名是否有效
     * @param userId 用户 ID 或用户名
     * @param config 平台配置
     * @return 是否有效
     */
    boolean validateUserId(String userId, Map<String, Object> config) throws BusinessException;

    /**
     * 拉取平台用户资料（头像、简介等），仅部分平台支持（如 TimeStore profile/detail）
     * @param userId 平台用户 ID
     * @param config 平台配置
     * @return 若有资料则返回 map，key 为 userAvatar、selfIntroduction 等；不支持则 empty
     */
    default Optional<Map<String, String>> getProfileDetail(String userId, Map<String, Object> config) throws BusinessException {
        return Optional.empty();
    }
}
