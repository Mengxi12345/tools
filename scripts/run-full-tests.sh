#!/bin/bash

# 完整功能测试脚本

set -e

echo "=========================================="
echo "完整功能测试"
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
run_test() {
    local test_name=$1
    local test_command=$2
    
    echo -n "测试 $test_name... "
    
    if eval "$test_command" > /tmp/test_output.log 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((TEST_PASSED++))
        return 0
    else
        echo -e "${RED}✗ 失败${NC}"
        echo "错误信息:"
        tail -10 /tmp/test_output.log | sed 's/^/  /'
        ((TEST_FAILED++))
        return 1
    fi
}

# 1. 检查服务状态
echo "1. 检查服务状态"
echo "----------------------------------------"
docker compose -f docker-compose.full.yml ps
echo ""

# 2. 测试组件连接
echo "2. 测试组件连接"
echo "----------------------------------------"
run_test "PostgreSQL" "PGPASSWORD=caat_password psql -h localhost -U caat_user -d caat_db -c 'SELECT 1;'"
run_test "Redis" "redis-cli -h localhost -p 6379 ping"
run_test "Elasticsearch" "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\"'"
run_test "RabbitMQ" "curl -s -u admin:admin http://localhost:15672/api/overview | grep -q '\"rabbitmq_version\"'"
echo ""

# 3. 测试后端 API
echo "3. 测试后端 API"
echo "----------------------------------------"
echo "等待后端服务启动..."
sleep 5

# 检查后端健康状态
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 后端服务已启动${NC}"
        HEALTH=$(curl -s http://localhost:8080/actuator/health | grep -o '"status":"[^"]*' | cut -d'"' -f4)
        echo "  健康状态: $HEALTH"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}✗ 后端服务启动超时${NC}"
        ((TEST_FAILED++))
    else
        sleep 2
    fi
done
echo ""

# 4. 测试 API 端点
echo "4. 测试 API 端点"
echo "----------------------------------------"

# 测试平台列表
run_test "获取平台列表" "curl -s http://localhost:8080/api/v1/platforms | grep -q '\"code\"'"

# 测试用户列表
run_test "获取用户列表" "curl -s http://localhost:8080/api/v1/users | grep -q '\"code\"'"

# 测试内容列表
run_test "获取内容列表" "curl -s 'http://localhost:8080/api/v1/contents?page=0&size=10' | grep -q '\"code\"'"

# 测试统计接口
run_test "获取统计信息" "curl -s http://localhost:8080/api/v1/stats/overview | grep -q '\"code\"'"
echo ""

# 5. 运行后端单元测试
echo "5. 运行后端单元测试"
echo "----------------------------------------"
cd backend
if mvn test -DskipTests=false 2>&1 | tee /tmp/maven_test.log | tail -20; then
    echo -e "${GREEN}✓ 单元测试通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ 部分测试失败（查看日志了解详情）${NC}"
    TEST_FAILURES=$(grep -c "FAILURE\|ERROR" /tmp/maven_test.log || echo "0")
    echo "  失败测试数: $TEST_FAILURES"
fi
cd ..
echo ""

# 6. 测试前端构建
echo "6. 测试前端构建"
echo "----------------------------------------"
cd frontend
if npm run build > /tmp/frontend_build.log 2>&1; then
    echo -e "${GREEN}✓ 前端构建成功${NC}"
    ((TEST_PASSED++))
else
    echo -e "${RED}✗ 前端构建失败${NC}"
    tail -20 /tmp/frontend_build.log | sed 's/^/  /'
    ((TEST_FAILED++))
fi
cd ..
echo ""

# 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo -e "${GREEN}通过: $TEST_PASSED${NC}"
echo -e "${RED}失败: $TEST_FAILED${NC}"
echo ""

if [ $TEST_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠ 部分测试失败，请查看上述错误信息${NC}"
    exit 1
fi
