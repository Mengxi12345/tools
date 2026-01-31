-- Quartz 数据库表初始化脚本
-- 如果使用 JDBC 存储，Quartz 会自动创建表
-- 此脚本用于手动创建（如果需要）

-- 注意：Quartz 2.x 会自动创建表（spring.quartz.jdbc.initialize-schema=always）
-- 如果需要手动创建，请参考 Quartz 官方文档中的 tables_postgres.sql

-- 主要表：
-- - qrtz_job_details: 任务详情
-- - qrtz_triggers: 触发器
-- - qrtz_simple_triggers: 简单触发器
-- - qrtz_cron_triggers: Cron 触发器
-- - qrtz_scheduler_state: 调度器状态
-- - qrtz_locks: 锁表

-- 检查表是否存在
SELECT EXISTS (
    SELECT FROM information_schema.tables 
    WHERE table_schema = 'public' 
    AND table_name LIKE 'qrtz_%'
);

-- 如果表不存在，Quartz 会在启动时自动创建
-- 确保 application.yml 中配置了：
-- spring:
--   quartz:
--     jdbc:
--       initialize-schema: always
