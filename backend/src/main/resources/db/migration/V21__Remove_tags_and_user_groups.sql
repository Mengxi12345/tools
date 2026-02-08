-- 移除标签管理和用户分组功能
-- 1. 删除 content_tags 关联表
DROP TABLE IF EXISTS content_tags;
-- 2. 删除 tags 表
DROP TABLE IF EXISTS tags;
-- 3. 清除 tracked_users 的 group_id 引用后删除 user_groups 表
UPDATE tracked_users SET group_id = NULL WHERE group_id IS NOT NULL;
ALTER TABLE tracked_users DROP COLUMN IF EXISTS group_id;
DROP TABLE IF EXISTS user_groups;
