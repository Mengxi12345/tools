-- 初始化数据库表结构
-- 使用 Flyway 进行数据库版本管理

-- 平台表
CREATE TABLE IF NOT EXISTS platforms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(100) NOT NULL,
    api_base_url VARCHAR(500),
    auth_type VARCHAR(50),
    config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 追踪用户表
CREATE TABLE IF NOT EXISTS tracked_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_id UUID NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    username VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    group_id UUID,
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_fetched_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(platform_id, user_id)
);

-- 用户标签关联表
CREATE TABLE IF NOT EXISTS tracked_user_tags (
    user_id UUID NOT NULL REFERENCES tracked_users(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, tag)
);

-- 内容表
CREATE TABLE IF NOT EXISTS contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_id UUID NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES tracked_users(id) ON DELETE CASCADE,
    content_id VARCHAR(255) NOT NULL,
    title VARCHAR(500),
    body TEXT,
    url VARCHAR(1000) NOT NULL,
    content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    published_at TIMESTAMP NOT NULL,
    metadata JSONB,
    hash VARCHAR(255) NOT NULL UNIQUE,
    is_read BOOLEAN NOT NULL DEFAULT false,
    is_favorite BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 内容索引
CREATE INDEX IF NOT EXISTS idx_user_id ON contents(user_id);
CREATE INDEX IF NOT EXISTS idx_published_at ON contents(published_at);
CREATE INDEX IF NOT EXISTS idx_hash ON contents(hash);

-- 内容媒体 URL 表
CREATE TABLE IF NOT EXISTS content_media_urls (
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    media_url VARCHAR(1000) NOT NULL,
    PRIMARY KEY (content_id, media_url)
);

-- 标签表
CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    color VARCHAR(20),
    category VARCHAR(100),
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 内容标签关联表
CREATE TABLE IF NOT EXISTS content_tags (
    content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (content_id, tag_id)
);

-- 定时任务配置表
CREATE TABLE IF NOT EXISTS schedule_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    is_global_enabled BOOLEAN NOT NULL DEFAULT true,
    default_cron VARCHAR(100) NOT NULL DEFAULT '0 0 */6 * * ?',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户定时任务表
CREATE TABLE IF NOT EXISTS user_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES tracked_users(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL DEFAULT '0 0 */6 * * ?',
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 刷新任务表
CREATE TABLE IF NOT EXISTS fetch_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES tracked_users(id) ON DELETE CASCADE,
    task_type VARCHAR(20) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INTEGER NOT NULL DEFAULT 0,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    total_count INTEGER,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 刷新任务索引
CREATE INDEX IF NOT EXISTS idx_fetch_task_user_id ON fetch_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_fetch_task_status ON fetch_tasks(status);
