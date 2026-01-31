#!/bin/bash

# 数据库恢复脚本

set -e

BACKUP_FILE="${1:-}"
DB_NAME="${DB_NAME:-content_aggregator}"
DB_USER="${DB_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

if [ -z "$BACKUP_FILE" ]; then
    echo "用法: $0 <备份文件路径>"
    echo "环境变量:"
    echo "  DB_NAME - 数据库名（默认: content_aggregator）"
    echo "  DB_USER - 数据库用户（默认: postgres）"
    echo "  DB_HOST - 数据库主机（默认: localhost）"
    echo "  DB_PORT - 数据库端口（默认: 5432）"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "错误: 备份文件不存在: $BACKUP_FILE"
    exit 1
fi

echo "=== 数据库恢复 ==="
echo "备份文件: $BACKUP_FILE"
echo "数据库: $DB_NAME"
echo "主机: $DB_HOST:$DB_PORT"
echo ""

# 确认操作
read -p "警告: 这将覆盖现有数据库。是否继续? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "操作已取消"
    exit 0
fi

# 检查备份文件类型
if file "$BACKUP_FILE" | grep -q "PostgreSQL custom\|compressed"; then
    echo "检测到 PostgreSQL 自定义格式备份，使用 pg_restore..."
    export PGPASSWORD="${DB_PASSWORD:-postgres}"
    
    # 删除现有数据库（可选，谨慎使用）
    # dropdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME" || true
    
    # 恢复备份
    pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --clean --if-exists --verbose "$BACKUP_FILE"
    
    if [ $? -eq 0 ]; then
        echo "✅ 数据库恢复成功"
    else
        echo "❌ 数据库恢复失败"
        exit 1
    fi
else
    echo "检测到 SQL 文本格式备份，使用 psql..."
    export PGPASSWORD="${DB_PASSWORD:-postgres}"
    
    # 恢复 SQL 文件
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$BACKUP_FILE"
    
    if [ $? -eq 0 ]; then
        echo "✅ 数据库恢复成功"
    else
        echo "❌ 数据库恢复失败"
        exit 1
    fi
fi

echo ""
echo "=== 恢复完成 ==="
echo "请验证数据完整性并重启应用"
