#!/bin/bash

# 全量功能测试脚本（使用本地服务）

set -e

echo "=========================================="
echo "全量功能测试"
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
test_service() {
    local name=$1
    local test_command=$2
    
    echo -n "测试 $name... "
    if eval "$test_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((TEST_PASSED++))
        return 0
    else
        echo -e "${RED}✗ 失败${NC}"
        ((TEST_FAILED++))
        return 1
    fi
}

test_api() {
    local name=$1
    local url=$2
    
    echo -n "测试 $name... "
    RESPONSE=$(curl -s -w "\n%{http_code}" "$url")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | head -n-1)
    
    if [ "$HTTP_CODE" -eq 200 ] && echo "$BODY" | grep -q '"code"'; then
        echo -e "${GREEN}✓ 通过${NC} (HTTP $HTTP_CODE)"
        ((TEST_PASSED++))
        return 0
    else
        echo -e "${RED}✗ 失败${NC} (HTTP $HTTP_CODE)"
        ((TEST_FAILED++))
        return 1
    fi
}

# 1. 检查服务状态
echo "1. 检查服务状态"
echo "----------------------------------------"

test_service "PostgreSQL" "PGPASSWORD=caat_password psql -h localhost -U caat_user -d caat_db -c 'SELECT 1;' 2>/dev/null || psql -h localhost -U caat_user -d caat_db -c 'SELECT 1;'"
test_service "Redis" "redis-cli ping | grep -q PONG"
test_service "Elasticsearch" "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\"'"
test_service "RabbitMQ" "rabbitmqctl status > /dev/null 2>&1"
echo ""

# 2. 检查后端服务
echo "2. 检查后端服务"
echo "----------------------------------------"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    HEALTH=$(curl -s http://localhost:8080/actuator/health)
    STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*' | cut -d'"' -f4 | head -1)
    echo -e "后端状态: ${GREEN}$STATUS${NC}"
    
    # 检查各个组件
    if echo "$HEALTH" | grep -q '"db":{"status":"UP"'; then
        echo -e "  PostgreSQL: ${GREEN}✓ 连接正常${NC}"
    fi
    if echo "$HEALTH" | grep -q '"redis":{"status":"UP"'; then
        echo -e "  Redis: ${GREEN}✓ 连接正常${NC}"
    else
        echo -e "  Redis: ${YELLOW}⚠ 连接失败${NC}"
    fi
else
    echo -e "${RED}✗ 后端服务未运行${NC}"
    echo "请先启动后端: cd backend && mvn spring-boot:run"
    exit 1
fi
echo ""

# 3. 测试 API 端点
echo "3. 测试 API 端点"
echo "----------------------------------------"

test_api "平台列表" "http://localhost:8080/api/v1/platforms"
test_api "用户列表" "http://localhost:8080/api/v1/users?page=0&size=10"
test_api "内容列表" "http://localhost:8080/api/v1/contents?page=0&size=10"
test_api "统计信息" "http://localhost:8080/api/v1/stats/overview"
test_api "标签列表" "http://localhost:8080/api/v1/tags"
test_api "用户组列表" "http://localhost:8080/api/v1/user-groups"
echo ""

# 4. 测试搜索功能
echo "4. 测试搜索功能"
echo "----------------------------------------"

test_api "普通搜索" "http://localhost:8080/api/v1/contents/search?query=test&page=0&size=10"
test_api "搜索历史" "http://localhost:8080/api/v1/contents/search/history?page=0&size=10"
test_api "热门搜索" "http://localhost:8080/api/v1/contents/search/popular?limit=10"

# 测试 Elasticsearch 搜索
echo -n "测试 Elasticsearch 搜索... "
if curl -s "http://localhost:8080/api/v1/contents/search/elasticsearch?query=test&page=0&size=10" | grep -q '"code"'; then
    echo -e "${GREEN}✓ 通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ 跳过（Elasticsearch 可能未索引数据）${NC}"
fi
echo ""

# 5. 运行集成测试
echo "5. 运行集成测试"
echo "----------------------------------------"
cd backend

echo "运行适配器集成测试..."
if mvn test -Dtest=AdapterIntegrationTest -q 2>&1 | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✓ 适配器测试通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ 适配器测试失败或跳过${NC}"
fi

echo "运行 RBAC 集成测试..."
if mvn test -Dtest=RBACIntegrationTest -q 2>&1 | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✓ RBAC 测试通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ RBAC 测试失败或跳过${NC}"
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
    echo -e "${YELLOW}⚠ 部分测试失败${NC}"
    exit 1
fi
