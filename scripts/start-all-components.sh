#!/bin/bash

# 启动所有组件的便捷脚本

set -e

echo "=========================================="
echo "启动所有组件"
echo "=========================================="
echo ""

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "错误: Docker 未运行，请先启动 Docker"
    exit 1
fi

# 选择启动方式
echo "请选择启动方式："
echo "1. 完整模式（包含所有组件和监控）"
echo "2. 基础模式（只包含核心组件）"
read -p "请输入选择 (1/2，默认 1): " choice

case "${choice:-1}" in
    1)
        echo ""
        echo "启动完整模式..."
        docker compose -f docker-compose.full.yml up -d
        ;;
    2)
        echo ""
        echo "启动基础模式..."
        docker compose up -d
        ;;
    *)
        echo "无效选择，使用默认：完整模式"
        docker compose -f docker-compose.full.yml up -d
        ;;
esac

echo ""
echo "等待服务启动..."
sleep 5

echo ""
echo "服务状态："
docker compose ps

echo ""
echo "=========================================="
echo "组件访问地址"
echo "=========================================="
echo "PostgreSQL: localhost:5432"
echo "Redis: localhost:6379"
echo "Elasticsearch: http://localhost:9200"
echo "RabbitMQ 管理: http://localhost:15672 (admin/admin)"
echo "Prometheus: http://localhost:9090"
echo "Grafana: http://localhost:3000 (admin/admin)"
echo "后端 API: http://localhost:8080"
echo "API 文档: http://localhost:8080/swagger-ui/index.html"
echo ""

echo "运行组件检查："
./scripts/check-components.sh

echo ""
echo "运行连接测试："
./scripts/test-all-components.sh
