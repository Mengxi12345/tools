-- 添加索引以优化查询性能

-- 追踪用户表索引
CREATE INDEX IF NOT EXISTS idx_tracked_users_platform_id ON tracked_users(platform_id);
CREATE INDEX IF NOT EXISTS idx_tracked_users_group_id ON tracked_users(group_id);
CREATE INDEX IF NOT EXISTS idx_tracked_users_is_active ON tracked_users(is_active);
CREATE INDEX IF NOT EXISTS idx_tracked_users_priority ON tracked_users(priority);
CREATE INDEX IF NOT EXISTS idx_tracked_users_last_fetched_at ON tracked_users(last_fetched_at);

-- 用户标签关联表索引（用于标签查询）
CREATE INDEX IF NOT EXISTS idx_tracked_user_tags_tag ON tracked_user_tags(tag);
-- 注意：tracked_user_tags 表的主键是 (user_id, tag)，user_id 字段已存在，不需要单独创建索引

-- 内容表索引
CREATE INDEX IF NOT EXISTS idx_contents_platform_id ON contents(platform_id);
CREATE INDEX IF NOT EXISTS idx_contents_is_read ON contents(is_read);
CREATE INDEX IF NOT EXISTS idx_contents_is_favorite ON contents(is_favorite);
CREATE INDEX IF NOT EXISTS idx_contents_content_type ON contents(content_type);
CREATE INDEX IF NOT EXISTS idx_contents_created_at ON contents(created_at);

-- 复合索引（用于常见查询组合）
CREATE INDEX IF NOT EXISTS idx_contents_user_published ON contents(user_id, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_contents_platform_published ON contents(platform_id, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_contents_user_read ON contents(user_id, is_read);

-- 标签表索引
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
CREATE INDEX IF NOT EXISTS idx_tags_category ON tags(category);
CREATE INDEX IF NOT EXISTS idx_tags_usage_count ON tags(usage_count DESC);

-- 内容标签关联表索引
CREATE INDEX IF NOT EXISTS idx_content_tags_tag_id ON content_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_content_tags_content_id ON content_tags(content_id);

-- 刷新任务表索引（已有部分，补充）
CREATE INDEX IF NOT EXISTS idx_fetch_task_task_type ON fetch_tasks(task_type);
CREATE INDEX IF NOT EXISTS idx_fetch_task_created_at ON fetch_tasks(created_at DESC);

-- 用户定时任务表索引
CREATE INDEX IF NOT EXISTS idx_user_schedules_user_id ON user_schedules(user_id);
CREATE INDEX IF NOT EXISTS idx_user_schedules_is_enabled ON user_schedules(is_enabled);
CREATE INDEX IF NOT EXISTS idx_user_schedules_next_run_at ON user_schedules(next_run_at);

-- 通知规则表索引（如果存在）
-- 注意：notification_rules 表使用 rule_type 字段，不是 type
CREATE INDEX IF NOT EXISTS idx_notification_rules_is_enabled ON notification_rules(is_enabled);
CREATE INDEX IF NOT EXISTS idx_notification_rules_rule_type ON notification_rules(rule_type);
