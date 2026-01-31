-- 导出任务表
CREATE TABLE IF NOT EXISTS export_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES tracked_users(id) ON DELETE CASCADE,
    export_format VARCHAR(20) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INTEGER NOT NULL DEFAULT 0,
    file_path VARCHAR(1000),
    file_size BIGINT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 导出任务索引
CREATE INDEX IF NOT EXISTS idx_export_task_user_id ON export_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_export_task_status ON export_tasks(status);
CREATE INDEX IF NOT EXISTS idx_export_task_created_at ON export_tasks(created_at DESC);
