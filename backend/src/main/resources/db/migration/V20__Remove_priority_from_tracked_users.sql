-- 移除 tracked_users 表中的 priority 字段和索引

-- 删除索引
DROP INDEX IF EXISTS idx_tracked_users_priority;

-- 删除字段
ALTER TABLE tracked_users DROP COLUMN IF EXISTS priority;
