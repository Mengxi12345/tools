# 备份恢复指南

## 概述

本指南介绍如何备份和恢复内容聚合工具的数据库。

## 备份功能

### 自动备份

系统提供了两种备份方式：

1. **完整备份**：备份整个数据库
2. **增量备份**：备份自上次备份以来的变更（元数据）

### API 接口

#### 1. 执行数据库备份

```bash
POST /api/v1/backup/database
```

**响应示例**：
```json
{
  "code": 200,
  "message": "备份成功",
  "data": {
    "backupFile": "/path/to/backup_20260129_120000.sql",
    "backupTime": "2026-01-29T12:00:00",
    "fileSize": 1048576
  }
}
```

#### 2. 执行增量备份

```bash
POST /api/v1/backup/incremental
```

#### 3. 列出所有备份

```bash
GET /api/v1/backup/list
```

## 手动备份

### 使用 pg_dump

```bash
# 完整备份（SQL 格式）
pg_dump -h localhost -U postgres -d content_aggregator > backup_$(date +%Y%m%d_%H%M%S).sql

# 自定义格式（压缩）
pg_dump -h localhost -U postgres -d content_aggregator -Fc > backup_$(date +%Y%m%d_%H%M%S).dump

# 仅数据（不含结构）
pg_dump -h localhost -U postgres -d content_aggregator --data-only > data_backup.sql

# 仅结构（不含数据）
pg_dump -h localhost -U postgres -d content_aggregator --schema-only > schema_backup.sql
```

### 使用 Docker

```bash
# 从 Docker 容器中备份
docker exec -t postgres_container pg_dump -U postgres content_aggregator > backup.sql

# 或使用 docker-compose
docker compose exec postgres pg_dump -U postgres content_aggregator > backup.sql
```

## 恢复功能

### 使用 API 恢复（待实现）

目前恢复功能需要通过命令行手动执行。

### 手动恢复

#### 使用 psql（SQL 格式）

```bash
# 恢复 SQL 备份文件
psql -h localhost -U postgres -d content_aggregator < backup_20260129_120000.sql

# 或使用 Docker
docker compose exec -T postgres psql -U postgres content_aggregator < backup.sql
```

#### 使用 pg_restore（自定义格式）

```bash
# 恢复自定义格式备份
pg_restore -h localhost -U postgres -d content_aggregator --clean --if-exists backup.dump

# 或使用 Docker
docker compose exec -T postgres pg_restore -U postgres -d content_aggregator --clean --if-exists < backup.dump
```

### 使用恢复脚本

我们提供了自动化恢复脚本：

```bash
# 设置环境变量
export DB_NAME=content_aggregator
export DB_USER=postgres
export DB_HOST=localhost
export DB_PORT=5432
export DB_PASSWORD=your_password

# 执行恢复
./scripts/restore-backup.sh backup_20260129_120000.sql
```

## 备份策略

### 推荐策略

1. **每日完整备份**：每天凌晨执行一次完整备份
2. **每小时增量备份**：每小时执行一次增量备份
3. **保留策略**：
   - 最近 7 天的每日备份
   - 最近 30 天的每周备份
   - 最近 1 年的每月备份

### 自动化备份

#### 使用 Cron（Linux/macOS）

```bash
# 编辑 crontab
crontab -e

# 添加每日备份任务（每天凌晨 2 点）
0 2 * * * curl -X POST http://localhost:8080/api/v1/backup/database
```

#### 使用系统定时任务

```bash
# 创建备份脚本
cat > /usr/local/bin/backup-content-aggregator.sh << 'EOF'
#!/bin/bash
curl -X POST http://localhost:8080/api/v1/backup/database
EOF

chmod +x /usr/local/bin/backup-content-aggregator.sh

# 添加到系统定时任务
```

## 备份测试

### 使用测试脚本

```bash
# 执行备份恢复测试
./scripts/test-backup-restore.sh
```

### 手动测试步骤

1. **执行备份**
   ```bash
   curl -X POST http://localhost:8080/api/v1/backup/database
   ```

2. **验证备份文件**
   ```bash
   ls -lh backups/
   ```

3. **测试恢复**（在测试环境）
   ```bash
   ./scripts/restore-backup.sh backups/backup_20260129_120000.sql
   ```

4. **验证数据完整性**
   - 检查记录数量
   - 检查关键数据
   - 检查关联关系

## 注意事项

### 备份前

- ✅ 确保有足够的磁盘空间
- ✅ 检查数据库连接正常
- ✅ 确认备份目录权限

### 恢复前

- ⚠️ **备份当前数据**（防止恢复失败）
- ⚠️ **停止应用服务**（避免数据不一致）
- ⚠️ **验证备份文件完整性**
- ⚠️ **在测试环境先验证**

### 恢复后

- ✅ 验证数据完整性
- ✅ 检查应用功能正常
- ✅ 更新应用配置（如需要）

## 故障排查

### 问题：备份失败

**可能原因**：
- 数据库连接失败
- 磁盘空间不足
- 权限不足

**解决方案**：
```bash
# 检查数据库连接
docker compose ps postgres

# 检查磁盘空间
df -h

# 检查备份目录权限
ls -ld backups/
```

### 问题：恢复失败

**可能原因**：
- 备份文件损坏
- 数据库版本不兼容
- 权限不足

**解决方案**：
```bash
# 验证备份文件
file backup.sql

# 检查数据库版本
psql --version

# 检查权限
ls -l backup.sql
```

## 最佳实践

1. **定期测试恢复**：每月至少测试一次恢复流程
2. **异地备份**：将备份文件复制到其他位置
3. **加密备份**：对敏感数据进行加密备份
4. **监控备份**：监控备份任务执行情况
5. **文档记录**：记录备份和恢复操作日志

## 相关文件

- `BackupService.java` - 备份服务实现
- `BackupController.java` - 备份 API 控制器
- `scripts/test-backup-restore.sh` - 备份测试脚本
- `scripts/restore-backup.sh` - 恢复脚本
