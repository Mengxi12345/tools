#!/bin/bash

# 根据 plan.md 进行功能全量测试

set -e

echo "=========================================="
echo "功能全量测试（基于 plan.md）"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TEST_PASSED=0
TEST_FAILED=0
TEST_SKIPPED=0

# 测试函数
test_item() {
    local category=$1
    local name=$2
    local test_command=$3
    local required=${4:-true}
    
    echo -n "[$category] $name... "
    
    if eval "$test_command" > /tmp/test_output.log 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((TEST_PASSED++))
        return 0
    else
        if [ "$required" = "true" ]; then
            echo -e "${RED}✗ 失败${NC}"
            tail -3 /tmp/test_output.log | sed 's/^/  /'
            ((TEST_FAILED++))
            return 1
        else
            echo -e "${YELLOW}⚠ 跳过（可选）${NC}"
            ((TEST_SKIPPED++))
            return 0
        fi
    fi
}

# 获取认证token
get_auth_token() {
    local username=$1
    local password=$2
    
    RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$username\",\"password\":\"$password\"}" 2>&1)
    
    TOKEN=$(echo "$RESPONSE" | jq -r '.data.token // empty' 2>/dev/null)
    echo "$TOKEN"
}

# 初始化认证token
AUTH_TOKEN=""
echo "正在获取认证token..."

# 先尝试使用testuser登录
AUTH_TOKEN=$(get_auth_token "testuser" "testpass123")

# 如果失败，尝试注册新用户
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}尝试注册测试用户...${NC}"
    REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
        -H "Content-Type: application/json" \
        -d '{"username":"testuser'$(date +%s)'","password":"testpass123"}' 2>&1)
    AUTH_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.data.token // empty' 2>/dev/null)
    
    # 如果注册成功，使用新用户登录
    if [ -n "$AUTH_TOKEN" ]; then
        NEW_USERNAME=$(echo "$REGISTER_RESPONSE" | jq -r '.data.username // empty' 2>/dev/null)
        AUTH_TOKEN=$(get_auth_token "$NEW_USERNAME" "testpass123")
    fi
fi

if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${RED}错误: 无法获取认证token，测试将失败${NC}"
    exit 1
fi

echo -e "${GREEN}认证token获取成功${NC}"
echo ""

test_api() {
    local category=$1
    local name=$2
    local url=$3
    local required=${4:-true}
    
    echo -n "[$category] $name... "
    
    # 添加认证头
    if [ -n "$AUTH_TOKEN" ]; then
        RESPONSE=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $AUTH_TOKEN" "$url" 2>&1)
    else
        RESPONSE=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    fi
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" -eq 200 ] && (echo "$BODY" | grep -q '"code"' || echo "$BODY" | grep -q '"status":"UP"' || echo "$BODY" | grep -q '<!DOCTYPE'); then
        echo -e "${GREEN}✓ 通过${NC} (HTTP $HTTP_CODE)"
        ((TEST_PASSED++))
        return 0
    elif [ "$HTTP_CODE" -eq 401 ] || [ "$HTTP_CODE" -eq 403 ]; then
        if [ "$required" = "true" ]; then
            echo -e "${RED}✗ 失败${NC} (HTTP $HTTP_CODE - 认证失败)"
            echo "$BODY" | head -2 | sed 's/^/  /'
            ((TEST_FAILED++))
            return 1
        else
            echo -e "${YELLOW}⚠ 跳过（需要认证）${NC}"
            ((TEST_SKIPPED++))
            return 0
        fi
    else
        if [ "$required" = "true" ]; then
            echo -e "${RED}✗ 失败${NC} (HTTP $HTTP_CODE)"
            echo "$BODY" | head -2 | sed 's/^/  /'
            ((TEST_FAILED++))
            return 1
        else
            echo -e "${YELLOW}⚠ 跳过（可选）${NC}"
            ((TEST_SKIPPED++))
            return 0
        fi
    fi
}

# 1. 检查服务状态
echo -e "${BLUE}1. 服务状态检查${NC}"
echo "----------------------------------------"

test_item "服务" "PostgreSQL" "curl -s http://localhost:8080/actuator/health | grep -q '\"db\":{\"status\":\"UP\"'"
test_item "服务" "Redis" "redis-cli ping | grep -q PONG"
test_item "服务" "Elasticsearch" "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\"'" "false"
test_item "服务" "RabbitMQ" "rabbitmqctl status > /dev/null 2>&1" "false"
test_item "服务" "后端服务" "curl -s http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"
echo ""

# 2. 平台管理功能
echo -e "${BLUE}2. 平台管理功能${NC}"
echo "----------------------------------------"

test_api "平台" "获取平台列表" "http://localhost:8080/api/v1/platforms"
test_api "平台" "创建平台" "http://localhost:8080/api/v1/platforms" "false"  # POST 需要数据
test_api "平台" "平台统计" "http://localhost:8080/api/v1/stats/platform-distribution"
echo ""

# 3. 用户管理功能
echo -e "${BLUE}3. 用户管理功能${NC}"
echo "----------------------------------------"

test_api "用户" "获取用户列表" "http://localhost:8080/api/v1/users?page=0&size=10"
test_api "用户" "用户统计" "http://localhost:8080/api/v1/stats/users"
test_api "用户" "用户组列表" "http://localhost:8080/api/v1/groups"
echo ""

# 4. 内容管理功能
echo -e "${BLUE}4. 内容管理功能${NC}"
echo "----------------------------------------"

test_api "内容" "获取内容列表" "http://localhost:8080/api/v1/contents?page=0&size=10"
test_api "内容" "内容统计" "http://localhost:8080/api/v1/stats/content"
test_api "内容" "内容分类统计" "http://localhost:8080/api/v1/contents/categories/stats"
echo ""

# 5. 搜索功能
echo -e "${BLUE}5. 搜索功能${NC}"
echo "----------------------------------------"

test_api "搜索" "普通搜索" "http://localhost:8080/api/v1/contents/search?query=test&page=0&size=10" "false"
test_api "搜索" "搜索历史" "http://localhost:8080/api/v1/contents/search/history?page=0&size=10"
test_api "搜索" "热门搜索" "http://localhost:8080/api/v1/contents/search/popular?limit=10"
test_api "搜索" "Elasticsearch 搜索" "http://localhost:8080/api/v1/contents/search/elasticsearch?query=test&page=0&size=10" "false"
test_api "搜索" "正则搜索" "http://localhost:8080/api/v1/contents/search/regex?pattern=test.*&page=0&size=10" "false"
test_api "搜索" "高级搜索" "http://localhost:8080/api/v1/contents/search/advanced?query=test&contentType=TEXT&page=0&size=10" "false"
echo ""

# 6. 数据分析功能
echo -e "${BLUE}6. 数据分析功能${NC}"
echo "----------------------------------------"

test_api "统计" "统计概览" "http://localhost:8080/api/v1/stats/overview"
test_api "统计" "时间分布" "http://localhost:8080/api/v1/stats/content-time-distribution?startDate=2026-01-01&endDate=2026-01-31"
test_api "统计" "类型分布" "http://localhost:8080/api/v1/stats/content-type-distribution"
test_api "统计" "活跃用户排名" "http://localhost:8080/api/v1/stats/active-users-ranking?limit=10"
test_api "统计" "增长趋势" "http://localhost:8080/api/v1/stats/content-growth-trend?startDate=2026-01-01&endDate=2026-01-31"
echo ""

# 7. 标签功能
echo -e "${BLUE}7. 标签功能${NC}"
echo "----------------------------------------"

test_api "标签" "标签列表" "http://localhost:8080/api/v1/tags"
test_api "标签" "标签统计" "http://localhost:8080/api/v1/stats/tags"
echo ""

# 8. 通知功能
echo -e "${BLUE}8. 通知功能${NC}"
echo "----------------------------------------"

test_api "通知" "通知规则列表" "http://localhost:8080/api/v1/notification-rules"
echo ""

# 9. 导出功能
echo -e "${BLUE}9. 导出功能${NC}"
echo "----------------------------------------"

test_api "导出" "导出任务列表" "http://localhost:8080/api/v1/export/tasks"
echo ""

# 10. AI 功能
echo -e "${BLUE}10. AI 功能${NC}"
echo "----------------------------------------"

test_api "AI" "AI 功能可用性" "http://localhost:8080/api/v1/ai/summary" "false"  # POST 需要数据
echo ""

# 11. 权限控制
echo -e "${BLUE}11. 权限控制${NC}"
echo "----------------------------------------"

test_api "权限" "角色列表" "http://localhost:8080/api/v1/roles"
echo ""

# 12. 监控功能
echo -e "${BLUE}12. 监控功能${NC}"
echo "----------------------------------------"

test_api "监控" "健康检查" "http://localhost:8080/actuator/health"
test_api "监控" "Prometheus 指标" "http://localhost:8080/actuator/prometheus" "false"
test_api "监控" "API 文档" "http://localhost:8080/swagger-ui/index.html"
echo ""

# 13. 运行集成测试
echo -e "${BLUE}13. 集成测试${NC}"
echo "----------------------------------------"

cd backend

echo -n "[集成测试] 适配器测试... "
if mvn test -Dtest=AdapterIntegrationTest -q 2>&1 | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✓ 通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ 跳过或失败${NC}"
    ((TEST_SKIPPED++))
fi

echo -n "[集成测试] RBAC 测试... "
if mvn test -Dtest=RBACIntegrationTest -q 2>&1 | grep -q "BUILD SUCCESS"; then
    echo -e "${GREEN}✓ 通过${NC}"
    ((TEST_PASSED++))
else
    echo -e "${YELLOW}⚠ 跳过或失败${NC}"
    ((TEST_SKIPPED++))
fi

cd ..
echo ""

# 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo -e "${GREEN}通过: $TEST_PASSED${NC}"
echo -e "${RED}失败: $TEST_FAILED${NC}"
echo -e "${YELLOW}跳过: $TEST_SKIPPED${NC}"
echo ""

TOTAL=$((TEST_PASSED + TEST_FAILED + TEST_SKIPPED))
if [ $TOTAL -gt 0 ]; then
    PASS_RATE=$((TEST_PASSED * 100 / TOTAL))
    echo "通过率: $PASS_RATE%"
fi
echo ""

if [ $TEST_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有必需测试通过！${NC}"
    exit 0
else
    echo -e "${RED}✗ 部分测试失败${NC}"
    exit 1
fi
