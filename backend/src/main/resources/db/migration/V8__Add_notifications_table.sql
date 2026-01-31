-- 通知记录表
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID REFERENCES notification_rules(id) ON DELETE SET NULL,
    content_id UUID REFERENCES contents(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    message TEXT,
    notification_type VARCHAR(20) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 通知表索引
CREATE INDEX IF NOT EXISTS idx_notification_is_read ON notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_content_id ON notifications(content_id);
