-- 为通知规则表添加免打扰时段字段
ALTER TABLE notification_rules ADD COLUMN IF NOT EXISTS quiet_hours JSONB;
