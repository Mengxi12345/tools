-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES sys_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 角色表索引
CREATE INDEX IF NOT EXISTS idx_role_name ON roles(name);

-- 用户角色关联表索引
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- 插入默认角色
INSERT INTO roles (id, name, description, created_at) VALUES
    (gen_random_uuid(), 'ADMIN', '管理员角色，拥有所有权限', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'USER', '普通用户角色，拥有基本权限', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'VIEWER', '只读用户角色，只能查看', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
