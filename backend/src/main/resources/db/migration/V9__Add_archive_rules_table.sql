-- 归档规则表
CREATE TABLE IF NOT EXISTS archive_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(64),
    conditions JSONB,
    target_category VARCHAR(100),
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    auto_execute BOOLEAN NOT NULL DEFAULT false,
    last_executed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 归档规则索引
CREATE INDEX IF NOT EXISTS idx_archive_rule_is_enabled ON archive_rules(is_enabled);
CREATE INDEX IF NOT EXISTS idx_archive_rule_auto_execute ON archive_rules(auto_execute);
