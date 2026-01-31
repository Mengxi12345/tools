#!/bin/bash

# 组件检查和测试脚本

set -e

echo "=========================================="
echo "组件可用性检查"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查结果统计
PASSED=0
FAILED=0
WARNINGS=0

# 检查函数
check_component() {
    local name=$1
    local check_command=$2
    local expected_output=$3
    
    echo -n "检查 $name... "
    
    if eval "$check_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 可用${NC}"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗ 不可用${NC}"
        ((FAILED++))
        return 1
    fi
}

check_component_with_output() {
    local name=$1
    local check_command=$2
    local expected_pattern=$3
    
    echo -n "检查 $name... "
    
    local output=$(eval "$check_command" 2>&1)
    if echo "$output" | grep -q "$expected_pattern"; then
        echo -e "${GREEN}✓ 可用${NC}"
        echo "  版本信息: $(echo "$output" | head -1)"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗ 不可用${NC}"
        ((FAILED++))
        return 1
    fi
}

# 1. 检查 PostgreSQL
echo "1. PostgreSQL 数据库"
if command -v psql &> /dev/null; then
    if psql -h localhost -U caat_user -d caat_db -c "SELECT version();" > /dev/null 2>&1; then
        VERSION=$(psql -h localhost -U caat_user -d caat_db -t -c "SELECT version();" | head -1)
        echo -e "${GREEN}✓ 可用${NC}"
        echo "  版本: $VERSION"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠ 已安装但无法连接（可能需要密码或服务未启动）${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "${RED}✗ psql 命令未找到${NC}"
    ((FAILED++))
fi

# 检查 Docker 中的 PostgreSQL
if docker ps | grep -q postgres; then
    echo "  Docker 容器: $(docker ps --format 'table {{.Names}}\t{{.Status}}' | grep postgres)"
fi
echo ""

# 2. 检查 Redis
echo "2. Redis 缓存"
if command -v redis-cli &> /dev/null; then
    if redis-cli -h localhost -p 6379 ping > /dev/null 2>&1; then
        VERSION=$(redis-cli -h localhost -p 6379 INFO server | grep redis_version | cut -d: -f2 | tr -d '\r')
        echo -e "${GREEN}✓ 可用${NC}"
        echo "  版本: $VERSION"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠ 已安装但无法连接（服务可能未启动）${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "${RED}✗ redis-cli 命令未找到${NC}"
    ((FAILED++))
fi

# 检查 Docker 中的 Redis
if docker ps | grep -q redis; then
    echo "  Docker 容器: $(docker ps --format 'table {{.Names}}\t{{.Status}}' | grep redis)"
fi
echo ""

# 3. 检查 Elasticsearch
echo "3. Elasticsearch 搜索引擎"
if curl -s http://localhost:9200 > /dev/null 2>&1; then
    VERSION=$(curl -s http://localhost:9200 | grep -o '"number":"[^"]*' | cut -d'"' -f4)
    CLUSTER=$(curl -s http://localhost:9200 | grep -o '"cluster_name":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  版本: $VERSION"
    echo "  集群: $CLUSTER"
    ((PASSED++))
else
    echo -e "${RED}✗ 不可用（服务可能未启动）${NC}"
    ((FAILED++))
fi

# 检查 Docker 中的 Elasticsearch
if docker ps | grep -q elasticsearch; then
    echo "  Docker 容器: $(docker ps --format 'table {{.Names}}\t{{.Status}}' | grep elasticsearch)"
fi
echo ""

# 4. 检查 RabbitMQ
echo "4. RabbitMQ 消息队列"
if curl -s -u admin:admin http://localhost:15672/api/overview > /dev/null 2>&1; then
    VERSION=$(curl -s -u admin:admin http://localhost:15672/api/overview | grep -o '"rabbitmq_version":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  版本: $VERSION"
    echo "  管理界面: http://localhost:15672"
    ((PASSED++))
elif command -v rabbitmqctl &> /dev/null; then
    if rabbitmqctl status > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 可用（本地安装）${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠ 已安装但服务可能未启动${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "${RED}✗ 不可用（服务可能未启动）${NC}"
    ((FAILED++))
fi

# 检查 Docker 中的 RabbitMQ
if docker ps | grep -q rabbitmq; then
    echo "  Docker 容器: $(docker ps --format 'table {{.Names}}\t{{.Status}}' | grep rabbitmq)"
fi
echo ""

# 5. 检查 Java 和 Maven
echo "5. Java 开发环境"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  版本: $JAVA_VERSION"
    ((PASSED++))
else
    echo -e "${RED}✗ Java 未安装${NC}"
    ((FAILED++))
fi

if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -1)
    echo "  Maven: $MVN_VERSION"
else
    echo -e "${YELLOW}⚠ Maven 未安装${NC}"
    ((WARNINGS++))
fi
echo ""

# 6. 检查 Node.js
echo "6. Node.js 前端环境"
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version)
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  版本: $NODE_VERSION"
    ((PASSED++))
else
    echo -e "${RED}✗ Node.js 未安装${NC}"
    ((FAILED++))
fi

if command -v npm &> /dev/null; then
    NPM_VERSION=$(npm --version)
    echo "  npm: $NPM_VERSION"
fi
echo ""

# 7. 检查 Docker
echo "7. Docker 容器化"
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  版本: $DOCKER_VERSION"
    ((PASSED++))
    
    if command -v docker-compose &> /dev/null || docker compose version &> /dev/null; then
        echo "  Docker Compose: 可用"
    else
        echo -e "${YELLOW}⚠ Docker Compose 未安装${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "${RED}✗ Docker 未安装${NC}"
    ((FAILED++))
fi
echo ""

# 8. 检查 Prometheus（可选）
echo "8. Prometheus 监控（可选）"
if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  地址: http://localhost:9090"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠ 未运行（可选组件）${NC}"
    ((WARNINGS++))
fi
echo ""

# 9. 检查 Grafana（可选）
echo "9. Grafana 监控可视化（可选）"
if curl -s http://localhost:3000/api/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 可用${NC}"
    echo "  地址: http://localhost:3000"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠ 未运行（可选组件）${NC}"
    ((WARNINGS++))
fi
echo ""

# 总结
echo "=========================================="
echo "检查总结"
echo "=========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${YELLOW}警告: $WARNINGS${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有必需组件可用！${NC}"
    exit 0
else
    echo -e "${RED}✗ 部分组件不可用，请检查上述错误${NC}"
    echo ""
    echo "提示："
    echo "1. 使用 Docker Compose 启动所有服务: docker compose up -d"
    echo "2. 检查服务状态: docker compose ps"
    echo "3. 查看日志: docker compose logs [service_name]"
    exit 1
fi
