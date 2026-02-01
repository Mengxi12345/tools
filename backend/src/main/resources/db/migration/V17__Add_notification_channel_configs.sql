-- 通知通道配置表：存储 QQ 群/飞书等配置，可复用、可选共享
CREATE TABLE notification_channel_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config JSONB NOT NULL,
    created_by UUID NULL,
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_channel_configs_channel_type ON notification_channel_configs(channel_type);
CREATE INDEX idx_notification_channel_configs_is_shared ON notification_channel_configs(is_shared);
