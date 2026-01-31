-- 测试数据库初始化脚本（简化版，用于H2内存数据库）

CREATE TABLE IF NOT EXISTS platforms (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(100) NOT NULL,
    api_base_url VARCHAR(500),
    auth_type VARCHAR(50),
    config TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tracked_users (
    id UUID PRIMARY KEY,
    platform_id UUID NOT NULL,
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

CREATE TABLE IF NOT EXISTS contents (
    id UUID PRIMARY KEY,
    platform_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content_id VARCHAR(255) NOT NULL,
    title VARCHAR(500),
    body TEXT,
    url VARCHAR(1000) NOT NULL,
    content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    published_at TIMESTAMP NOT NULL,
    metadata TEXT,
    hash VARCHAR(255) NOT NULL UNIQUE,
    is_read BOOLEAN NOT NULL DEFAULT false,
    is_favorite BOOLEAN NOT NULL DEFAULT false,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_id ON contents(user_id);
CREATE INDEX IF NOT EXISTS idx_published_at ON contents(published_at);
CREATE INDEX IF NOT EXISTS idx_hash ON contents(hash);
