#!/bin/bash

# 安装所有本地服务脚本

set -e

echo "=========================================="
echo "安装所有本地服务"
echo "=========================================="
echo ""

# 检查 Homebrew
if ! command -v brew &> /dev/null; then
    echo "错误: 需要 Homebrew 才能安装服务"
    echo "请先安装 Homebrew: https://brew.sh"
    exit 1
fi

# 安装 Redis
echo "1. 安装 Redis"
echo "----------------------------------------"
./scripts/install-redis-local.sh
echo ""

# 安装 Elasticsearch
echo "2. 安装 Elasticsearch"
echo "----------------------------------------"
./scripts/install-elasticsearch-local.sh
echo ""

# 安装 RabbitMQ
echo "3. 安装 RabbitMQ"
echo "----------------------------------------"
./scripts/install-rabbitmq-local.sh
echo ""

echo "=========================================="
echo "所有服务安装完成"
echo "=========================================="
echo ""
echo "服务状态："
echo "  Redis: localhost:6379"
echo "  Elasticsearch: http://localhost:9200"
echo "  RabbitMQ: localhost:5672"
echo "  RabbitMQ 管理: http://localhost:15672 (admin/admin)"
echo ""
echo "下一步："
echo "  1. 确保 PostgreSQL 已运行"
echo "  2. 启动后端服务: cd backend && mvn spring-boot:run"
echo "  3. 运行测试: ./scripts/test-all-services.sh"
