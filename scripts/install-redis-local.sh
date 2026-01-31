#!/bin/bash

# Redis 本地安装脚本（macOS）

set -e

echo "=========================================="
echo "安装 Redis（本地）"
echo "=========================================="

# 检查是否已安装
if command -v redis-server &> /dev/null; then
    echo "✓ Redis 已安装"
    redis-server --version
else
    echo "安装 Redis..."
    brew install redis
fi

# 启动 Redis
echo ""
echo "启动 Redis 服务..."
brew services start redis

# 等待服务启动
sleep 3

# 测试连接
echo ""
echo "测试 Redis 连接..."
if redis-cli ping | grep -q PONG; then
    echo "✓ Redis 连接成功"
    redis-cli info server | grep redis_version
else
    echo "✗ Redis 连接失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "Redis 安装完成"
echo "=========================================="
echo "Redis 运行在: localhost:6379"
echo "停止服务: brew services stop redis"
echo "查看日志: tail -f /usr/local/var/log/redis.log"
