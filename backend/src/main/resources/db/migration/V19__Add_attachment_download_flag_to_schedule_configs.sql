-- 为 schedule_configs 增加全局附件下载开关字段

ALTER TABLE schedule_configs
    ADD COLUMN IF NOT EXISTS enable_attachment_download BOOLEAN;

