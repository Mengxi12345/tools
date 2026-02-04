-- 导出任务增加 sort_order、log_messages（进度日志，JSON 数组）
ALTER TABLE export_tasks ADD COLUMN IF NOT EXISTS sort_order VARCHAR(10);
ALTER TABLE export_tasks ADD COLUMN IF NOT EXISTS log_messages TEXT;
