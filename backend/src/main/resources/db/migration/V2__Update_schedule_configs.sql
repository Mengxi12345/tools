-- 更新 schedule_configs 表结构以匹配新的实体设计
-- 从旧的 is_global_enabled/default_cron 结构迁移到新的 type/user_id/is_enabled/cron_expression 结构

-- 删除旧表（如果存在数据，先备份）
DROP TABLE IF EXISTS schedule_configs CASCADE;

-- 创建新的 schedule_configs 表
CREATE TABLE schedule_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,
    user_id UUID REFERENCES tracked_users(id) ON DELETE CASCADE,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    cron_expression VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 确保 GLOBAL 类型只有一条记录
    CONSTRAINT unique_global_config CHECK (
        (type = 'GLOBAL' AND user_id IS NULL) OR 
        (type = 'USER' AND user_id IS NOT NULL)
    )
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_schedule_config_type ON schedule_configs(type);
CREATE INDEX IF NOT EXISTS idx_schedule_config_user_id ON schedule_configs(user_id);
CREATE INDEX IF NOT EXISTS idx_schedule_config_type_user ON schedule_configs(type, user_id);

-- 插入默认的全局配置
INSERT INTO schedule_configs (type, is_enabled, cron_expression, created_at, updated_at)
VALUES ('GLOBAL', true, '0 0 */6 * * ?', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
