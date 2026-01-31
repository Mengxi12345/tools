#!/bin/bash

# 备份恢复测试脚本

set -e

echo "=== 备份恢复测试 ==="

BACKUP_DIR="./backups"
API_BASE_URL="http://localhost:8080/api/v1"

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# 1. 执行数据库备份
echo "1. 执行数据库备份..."
BACKUP_RESPONSE=$(curl -s -X POST "$API_BASE_URL/backup/database" \
  -H "Content-Type: application/json" \
  -w "\n%{http_code}")

HTTP_CODE=$(echo "$BACKUP_RESPONSE" | tail -n1)
BACKUP_BODY=$(echo "$BACKUP_RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ 备份成功"
    BACKUP_FILE=$(echo "$BACKUP_BODY" | grep -o '"backupFile":"[^"]*' | cut -d'"' -f4)
    echo "备份文件: $BACKUP_FILE"
else
    echo "❌ 备份失败 (HTTP $HTTP_CODE)"
    echo "$BACKUP_BODY"
    exit 1
fi

# 2. 列出备份文件
echo ""
echo "2. 列出备份文件..."
LIST_RESPONSE=$(curl -s -X GET "$API_BASE_URL/backup/list" \
  -H "Content-Type: application/json" \
  -w "\n%{http_code}")

HTTP_CODE=$(echo "$LIST_RESPONSE" | tail -n1)
LIST_BODY=$(echo "$LIST_RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ 备份列表获取成功"
    echo "$LIST_BODY" | jq '.' 2>/dev/null || echo "$LIST_BODY"
else
    echo "❌ 获取备份列表失败 (HTTP $HTTP_CODE)"
    echo "$LIST_BODY"
fi

# 3. 验证备份文件存在
if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ]; then
    echo ""
    echo "3. 验证备份文件..."
    BACKUP_SIZE=$(stat -f%z "$BACKUP_FILE" 2>/dev/null || stat -c%s "$BACKUP_FILE" 2>/dev/null)
    echo "✅ 备份文件存在"
    echo "文件大小: $BACKUP_SIZE 字节"
    
    # 检查备份文件是否包含 SQL 数据
    if grep -q "PostgreSQL database dump" "$BACKUP_FILE" 2>/dev/null || \
       grep -q "COPY\|INSERT\|CREATE TABLE" "$BACKUP_FILE" 2>/dev/null; then
        echo "✅ 备份文件格式正确（包含 SQL 数据）"
    else
        echo "⚠️  备份文件格式可能不正确"
    fi
else
    echo "⚠️  备份文件不存在或路径不正确: $BACKUP_FILE"
fi

# 4. 测试增量备份
echo ""
echo "4. 测试增量备份..."
INCREMENTAL_RESPONSE=$(curl -s -X POST "$API_BASE_URL/backup/incremental" \
  -H "Content-Type: application/json" \
  -w "\n%{http_code}")

HTTP_CODE=$(echo "$INCREMENTAL_RESPONSE" | tail -n1)
INCREMENTAL_BODY=$(echo "$INCREMENTAL_RESPONSE" | head -n-1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ 增量备份成功"
    echo "$INCREMENTAL_BODY"
else
    echo "⚠️  增量备份失败或未实现 (HTTP $HTTP_CODE)"
    echo "$INCREMENTAL_BODY"
fi

echo ""
echo "=== 备份恢复测试完成 ==="
echo ""
echo "注意：实际恢复测试需要："
echo "1. 停止应用"
echo "2. 使用 pg_restore 或 psql 恢复备份文件"
echo "3. 重启应用"
echo "4. 验证数据完整性"
