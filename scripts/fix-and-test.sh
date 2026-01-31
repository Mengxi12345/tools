#!/bin/bash

# 修复问题并测试脚本

set -e

echo "=========================================="
echo "问题修复和测试"
echo "=========================================="
echo ""

# 1. 确保所有服务运行
echo "1. 检查并启动服务"
echo "----------------------------------------"

if ! docker info > /dev/null 2>&1; then
    echo "错误: Docker 未运行"
    exit 1
fi

# 启动所有服务
docker compose -f docker-compose.full.yml up -d

echo "等待服务启动..."
sleep 15

# 检查服务状态
docker compose -f docker-compose.full.yml ps
echo ""

# 2. 检查数据库连接
echo "2. 检查数据库连接"
echo "----------------------------------------"
if docker compose -f docker-compose.full.yml exec -T db psql -U caat_user -d caat_db -c "SELECT version();" > /dev/null 2>&1; then
    echo "✓ 数据库连接正常"
else
    echo "✗ 数据库连接失败"
    exit 1
fi
echo ""

# 3. 检查 Redis 连接
echo "3. 检查 Redis 连接"
echo "----------------------------------------"
if docker compose -f docker-compose.full.yml exec -T redis redis-cli ping | grep -q PONG; then
    echo "✓ Redis 连接正常"
else
    echo "✗ Redis 连接失败"
fi
echo ""

# 4. 检查 Elasticsearch
echo "4. 检查 Elasticsearch"
echo "----------------------------------------"
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo "✓ Elasticsearch 连接正常"
    curl -s http://localhost:9200/_cluster/health | grep -o '"status":"[^"]*' | cut -d'"' -f4
else
    echo "✗ Elasticsearch 连接失败"
fi
echo ""

# 5. 启动后端服务
echo "5. 启动后端服务"
echo "----------------------------------------"

# 检查后端是否已运行
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✓ 后端服务已运行"
else
    echo "启动后端服务..."
    cd backend
    
    # 后台启动
    nohup mvn spring-boot:run > ../logs/backend.log 2>&1 &
    BACKEND_PID=$!
    echo "后端 PID: $BACKEND_PID"
    echo $BACKEND_PID > ../logs/backend.pid
    
    # 等待启动
    echo "等待后端启动..."
    for i in {1..60}; do
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo "✓ 后端服务启动成功"
            break
        fi
        if [ $i -eq 60 ]; then
            echo "✗ 后端服务启动超时"
            cat ../logs/backend.log | tail -30
            exit 1
        fi
        sleep 2
    fi
    
    cd ..
fi
echo ""

# 6. 运行 API 测试
echo "6. 运行 API 测试"
echo "----------------------------------------"

test_api() {
    local name=$1
    local url=$2
    
    echo -n "测试 $name... "
    if curl -s "$url" | grep -q '"code"'; then
        echo "✓ 成功"
        return 0
    else
        echo "✗ 失败"
        return 1
    fi
}

test_api "平台列表" "http://localhost:8080/api/v1/platforms"
test_api "用户列表" "http://localhost:8080/api/v1/users"
test_api "内容列表" "http://localhost:8080/api/v1/contents?page=0&size=10"
test_api "统计信息" "http://localhost:8080/api/v1/stats/overview"
echo ""

# 7. 运行集成测试
echo "7. 运行集成测试"
echo "----------------------------------------"
cd backend
if mvn test -Dtest=*IntegrationTest 2>&1 | tee ../logs/integration_test.log | tail -20; then
    echo "✓ 集成测试完成"
else
    echo "⚠ 部分集成测试失败（查看 logs/integration_test.log）"
fi
cd ..
echo ""

echo "=========================================="
echo "测试完成"
echo "=========================================="
echo "后端日志: logs/backend.log"
echo "集成测试日志: logs/integration_test.log"
echo ""
