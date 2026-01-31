#!/bin/bash

# 完整组件测试脚本

set -e

echo "=========================================="
echo "组件连接测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TEST_PASSED=0
TEST_FAILED=0

# 测试函数
test_component() {
    local name=$1
    local test_command=$2
    
    echo -n "测试 $name 连接... "
    
    if eval "$test_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 成功${NC}"
        ((TEST_PASSED++))
        return 0
    else
        echo -e "${RED}✗ 失败${NC}"
        ((TEST_FAILED++))
        return 1
    fi
}

# 1. 测试 PostgreSQL
echo "1. PostgreSQL"
test_component "PostgreSQL" "PGPASSWORD=caat_password psql -h localhost -U caat_user -d caat_db -c 'SELECT 1;'"
echo ""

# 2. 测试 Redis
echo "2. Redis"
test_component "Redis" "redis-cli -h localhost -p 6379 ping"
# 测试读写
if redis-cli -h localhost -p 6379 ping > /dev/null 2>&1; then
    redis-cli -h localhost -p 6379 set test_key "test_value" > /dev/null 2>&1
    VALUE=$(redis-cli -h localhost -p 6379 get test_key)
    if [ "$VALUE" = "test_value" ]; then
        echo -e "  读写测试: ${GREEN}✓ 成功${NC}"
        redis-cli -h localhost -p 6379 del test_key > /dev/null 2>&1
    else
        echo -e "  读写测试: ${RED}✗ 失败${NC}"
    fi
fi
echo ""

# 3. 测试 Elasticsearch
echo "3. Elasticsearch"
test_component "Elasticsearch" "curl -s http://localhost:9200/_cluster/health"
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    HEALTH=$(curl -s http://localhost:9200/_cluster/health | grep -o '"status":"[^"]*' | cut -d'"' -f4)
    echo "  集群状态: $HEALTH"
fi
echo ""

# 4. 测试 RabbitMQ
echo "4. RabbitMQ"
test_component "RabbitMQ" "curl -s -u admin:admin http://localhost:15672/api/overview"
if curl -s -u admin:admin http://localhost:15672/api/overview > /dev/null 2>&1; then
    echo "  管理界面: http://localhost:15672"
    echo "  用户名: admin"
    echo "  密码: admin"
fi
echo ""

# 5. 测试后端 API（如果运行）
echo "5. 后端 API"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    HEALTH=$(curl -s http://localhost:8080/actuator/health | grep -o '"status":"[^"]*' | cut -d'"' -f4)
    echo -e "  健康检查: ${GREEN}✓ $HEALTH${NC}"
    ((TEST_PASSED++))
else
    echo -e "  后端服务: ${YELLOW}⚠ 未运行${NC}"
fi
echo ""

# 6. 测试 Prometheus（如果运行）
echo "6. Prometheus"
if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
    echo -e "  Prometheus: ${GREEN}✓ 可用${NC}"
    echo "  地址: http://localhost:9090"
    ((TEST_PASSED++))
else
    echo -e "  Prometheus: ${YELLOW}⚠ 未运行（可选）${NC}"
fi
echo ""

# 7. 测试 Grafana（如果运行）
echo "7. Grafana"
if curl -s http://localhost:3000/api/health > /dev/null 2>&1; then
    echo -e "  Grafana: ${GREEN}✓ 可用${NC}"
    echo "  地址: http://localhost:3000"
    echo "  用户名: admin"
    echo "  密码: admin"
    ((TEST_PASSED++))
else
    echo -e "  Grafana: ${YELLOW}⚠ 未运行（可选）${NC}"
fi
echo ""

# 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo -e "${GREEN}通过: $TEST_PASSED${NC}"
echo -e "${RED}失败: $TEST_FAILED${NC}"
echo ""

if [ $TEST_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有组件连接测试通过！${NC}"
    exit 0
else
    echo -e "${RED}✗ 部分组件连接失败${NC}"
    exit 1
fi
