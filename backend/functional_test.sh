#!/bin/bash

# 功能测试脚本
# 测试所有核心功能的可用性

BASE_URL="http://localhost:8080/api/v1"
TEST_RESULTS="/tmp/functional_test_results.txt"

echo "=========================================" > $TEST_RESULTS
echo "功能测试报告 - $(date)" >> $TEST_RESULTS
echo "=========================================" >> $TEST_RESULTS
echo "" >> $TEST_RESULTS

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数器
PASSED=0
FAILED=0

# 获取认证 token
get_auth_token() {
    local resp
    resp=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"testuser","password":"testpass123"}' 2>/dev/null)
    echo "$resp" | jq -r '.data.token // empty' 2>/dev/null
}

# 测试函数（带认证）
test_api() {
    local name=$1
    local method=$2
    local url=$3
    local data=$4
    local expected_code=${5:-200}
    local curl_opts=(-s -w "\n%{http_code}")
    [ -n "$AUTH_TOKEN" ] && curl_opts+=(-H "Authorization: Bearer $AUTH_TOKEN")
    
    echo -n "测试: $name ... " >&2
    
    if [ "$method" = "GET" ]; then
        response=$(curl "${curl_opts[@]}" "$url")
    elif [ "$method" = "POST" ]; then
        response=$(curl "${curl_opts[@]}" -X POST -H "Content-Type: application/json" -d "$data" "$url")
    elif [ "$method" = "PUT" ]; then
        response=$(curl "${curl_opts[@]}" -X PUT -H "Content-Type: application/json" -d "$data" "$url")
    elif [ "$method" = "DELETE" ]; then
        response=$(curl "${curl_opts[@]}" -X DELETE "$url")
    fi
    
    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "$expected_code" ]; then
        echo -e "${GREEN}✓ 通过${NC}" >&2
        echo "[PASS] $name - HTTP $http_code" >> $TEST_RESULTS
        ((PASSED++))
        echo "$body" | jq -r '.data.id // .data // empty' 2>/dev/null | head -1
        return 0
    else
        echo -e "${RED}✗ 失败 (HTTP $http_code)${NC}" >&2
        echo "[FAIL] $name - HTTP $http_code (期望 $expected_code)" >> $TEST_RESULTS
        echo "响应: $body" >> $TEST_RESULTS
        ((FAILED++))
        return 1
    fi
}

# 等待应用启动（使用 actuator，无需认证）
echo "等待应用启动..."
for i in {1..30}; do
    if curl -s "http://localhost:8080/actuator/health" | grep -q '"status":"UP"'; then
        echo -e "${GREEN}应用已启动${NC}"
        break
    fi
    sleep 1
done

if ! curl -s "http://localhost:8080/actuator/health" | grep -q '"status":"UP"'; then
    echo -e "${RED}应用启动失败，请检查日志${NC}"
    exit 1
fi

# 获取认证 token（testuser/testpass123，需先注册或使用已有用户）
AUTH_TOKEN=$(get_auth_token)
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${YELLOW}无法获取 token，尝试注册测试用户...${NC}"
    curl -s -X POST http://localhost:8080/api/v1/auth/register \
        -H "Content-Type: application/json" \
        -d '{"username":"testuser","password":"testpass123"}' > /dev/null
    AUTH_TOKEN=$(get_auth_token)
fi
if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${RED}认证失败，请确保已有用户 testuser/testpass123 或可注册${NC}"
    exit 1
fi
echo -e "${GREEN}认证成功${NC}"
echo ""

echo "========================================="
echo "开始功能测试"
echo "========================================="
echo ""

# 1. 健康检查
echo "1. 健康检查测试"
test_api "健康检查" "GET" "$BASE_URL/health" "" 200
echo ""

# 2. 平台管理功能测试
echo "2. 平台管理功能测试"
PLATFORM_ID=$(test_api "创建平台" "POST" "$BASE_URL/platforms" '{"name":"Test Platform","type":"GITHUB","apiBaseUrl":"https://api.github.com","config":"{\"rateLimit\": 5000}","status":"ACTIVE"}')
if [ -n "$PLATFORM_ID" ]; then
    test_api "查询平台列表" "GET" "$BASE_URL/platforms" "" 200
    test_api "查询平台详情" "GET" "$BASE_URL/platforms/$PLATFORM_ID" "" 200
    test_api "更新平台" "PUT" "$BASE_URL/platforms/$PLATFORM_ID" "{\"name\":\"Updated Platform\",\"type\":\"GITHUB\",\"apiBaseUrl\":\"https://api.github.com\",\"config\":\"{\\\"rateLimit\\\": 5000}\",\"status\":\"ACTIVE\"}" 200
    test_api "测试平台连接" "POST" "$BASE_URL/platforms/$PLATFORM_ID/test" "" 200
    # 注意：删除测试放在最后，避免影响后续测试
    # test_api "删除平台" "DELETE" "$BASE_URL/platforms/$PLATFORM_ID" "" 200
fi
echo ""

# 3. 用户管理功能测试
echo "3. 用户管理功能测试"
if [ -n "$PLATFORM_ID" ]; then
    USER_ID=$(test_api "创建用户" "POST" "$BASE_URL/users" "{\"platformId\":\"$PLATFORM_ID\",\"username\":\"testuser\",\"userId\":\"testuser\",\"displayName\":\"Test User\",\"isActive\":true}")
    if [ -n "$USER_ID" ]; then
        test_api "查询用户列表" "GET" "$BASE_URL/users?page=0&size=10" "" 200
        test_api "查询用户详情" "GET" "$BASE_URL/users/$USER_ID" "" 200
        test_api "更新用户" "PUT" "$BASE_URL/users/$USER_ID" "{\"displayName\":\"Updated User\",\"isActive\":true}" 200
        test_api "启用/禁用用户" "PUT" "$BASE_URL/users/$USER_ID/toggle?isActive=false" "" 200
        test_api "查询用户统计" "GET" "$BASE_URL/users/$USER_ID/stats" "" 200
    fi
fi
echo ""

# 4. 内容拉取功能测试
echo "4. 内容拉取功能测试"
if [ -n "$USER_ID" ]; then
    TASK_ID=$(test_api "手动刷新内容" "POST" "$BASE_URL/users/$USER_ID/fetch" "{}" 200)
    if [ -n "$TASK_ID" ]; then
        echo "  任务已创建: $TASK_ID"
        echo "  等待10秒让异步任务执行..."
        sleep 10
        test_api "查询刷新历史" "GET" "$BASE_URL/users/$USER_ID/fetch-history?page=0&size=10" "" 200
    fi
fi
echo ""

# 5. 内容管理功能测试
echo "5. 内容管理功能测试"
test_api "查询内容列表" "GET" "$BASE_URL/contents?page=0&size=10" "" 200
test_api "查询内容统计" "GET" "$BASE_URL/contents/stats" "" 200
echo ""

# 6. 任务管理功能测试
echo "6. 任务管理功能测试"
test_api "查询定时任务状态" "GET" "$BASE_URL/tasks/schedule/status" "" 200
test_api "启用全局定时任务" "PUT" "$BASE_URL/tasks/schedule/enable" "" 200
test_api "禁用全局定时任务" "PUT" "$BASE_URL/tasks/schedule/disable" "" 200
if [ -n "$USER_ID" ]; then
    test_api "启用用户定时任务" "PUT" "$BASE_URL/tasks/schedule/users/$USER_ID/enable" "" 200
    test_api "禁用用户定时任务" "PUT" "$BASE_URL/tasks/schedule/users/$USER_ID/disable" "" 200
fi
test_api "查询刷新任务队列" "GET" "$BASE_URL/tasks/fetch/queue" "" 200
if [ -n "$TASK_ID" ]; then
    test_api "查询刷新任务详情" "GET" "$BASE_URL/tasks/fetch/$TASK_ID" "" 200
fi
echo ""

# 7. API文档测试
echo "7. API文档测试"
test_api "查询API文档" "GET" "http://localhost:8080/api-docs" "" 200
echo ""

# 输出测试结果
echo "========================================="
echo "测试结果汇总"
echo "========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""
echo "详细结果已保存到: $TEST_RESULTS"
echo ""

# 输出测试报告
cat $TEST_RESULTS

exit $FAILED
