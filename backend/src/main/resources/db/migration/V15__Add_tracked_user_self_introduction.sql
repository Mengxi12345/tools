-- 追踪用户表增加简介字段，用于 TimeStore 等平台拉取 profile 后保存
ALTER TABLE tracked_users ADD COLUMN IF NOT EXISTS self_introduction VARCHAR(2000);
