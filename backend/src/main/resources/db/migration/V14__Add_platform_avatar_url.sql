-- 平台表增加头像 URL 字段，用于导入并展示平台 Logo
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);
