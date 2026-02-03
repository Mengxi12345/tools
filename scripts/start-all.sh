#!/bin/bash

# 启动所有服务脚本

set -e

echo "=========================================="
echo "启动所有服务"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 检查依赖服务
echo -e "${YELLOW}检查依赖服务...${NC}"
echo ""

# PostgreSQL
if psql -U postgres -d content_aggregator -c 'SELECT 1' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ PostgreSQL 运行中${NC}"
else
    echo -e "${YELLOW}⚠️  PostgreSQL 检查跳过${NC}"
fi

# Redis
if redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Redis 运行中${NC}"
else
    echo -e "${RED}❌ Redis 未运行${NC}"
fi

# Elasticsearch
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Elasticsearch 运行中${NC}"
else
    echo -e "${RED}❌ Elasticsearch 未运行${NC}"
fi

echo ""

# 启动后端服务（使用 run-backend-no-sleep.sh 防止休眠影响定时任务）
echo -e "${YELLOW}启动后端服务...${NC}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
mkdir -p "$PROJECT_ROOT/logs"

# 检查是否已运行
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 后端服务已在运行${NC}"
else
    echo "正在启动后端服务（防休眠模式）..."
    nohup "$SCRIPT_DIR/run-backend-no-sleep.sh" dev >> "$PROJECT_ROOT/logs/backend-start.log" 2>&1 &
    BACKEND_PID=$!
    echo "后端进程 PID: $BACKEND_PID"
    
    # 等待后端启动
    echo "等待后端启动（最多 60 秒）..."
    for i in {1..12}; do
        sleep 5
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 后端服务已启动${NC}"
            break
        fi
        echo "等待中... ($i/12)"
    done
    
    if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${RED}❌ 后端服务启动超时，请查看 logs/backend-start.log${NC}"
    fi
fi

echo ""

# 启动前端服务
echo -e "${YELLOW}启动前端服务...${NC}"
cd "$(dirname "$0")/../frontend"

if [ ! -f "package.json" ]; then
    echo -e "${RED}❌ 未找到 package.json 文件${NC}"
    exit 1
fi

# 检查是否已运行
if curl -s http://localhost:5173 > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 前端服务已在运行${NC}"
else
    echo "正在启动前端服务..."
    nohup npm run dev > ../logs/frontend-start.log 2>&1 &
    FRONTEND_PID=$!
    echo "前端进程 PID: $FRONTEND_PID"
    
    # 等待前端启动
    echo "等待前端启动（最多 30 秒）..."
    for i in {1..6}; do
        sleep 5
        if curl -s http://localhost:5173 > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 前端服务已启动${NC}"
            break
        fi
        echo "等待中... ($i/6)"
    done
    
    if ! curl -s http://localhost:5173 > /dev/null 2>&1; then
        echo -e "${RED}❌ 前端服务启动超时，请查看 logs/frontend-start.log${NC}"
    fi
fi

echo ""
echo "=========================================="
echo "服务启动完成"
echo "=========================================="
echo ""
echo "后端服务："
echo "  - 地址: http://localhost:8080"
echo "  - 健康检查: http://localhost:8080/actuator/health"
echo "  - API 文档: http://localhost:8080/swagger-ui/index.html"
echo ""
echo "前端服务："
echo "  - 地址: http://localhost:5173"
echo ""
echo "日志文件："
echo "  - 后端: logs/backend-start.log"
echo "  - 前端: logs/frontend-start.log"
echo ""
