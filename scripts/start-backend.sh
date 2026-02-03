#!/bin/bash

# 启动后端服务脚本（使用 run-backend-no-sleep.sh 防止休眠影响定时任务）

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================="
echo "启动后端服务"
echo "=========================================="

# 检查后端是否已运行
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "后端服务已在运行"
    exit 0
fi

mkdir -p "$PROJECT_ROOT/logs"
echo "启动后端服务（防休眠模式）..."
nohup "$SCRIPT_DIR/run-backend-no-sleep.sh" dev >> "$PROJECT_ROOT/logs/backend.log" 2>&1 &
BACKEND_PID=$!
echo "后端 PID: $BACKEND_PID"
echo $BACKEND_PID > "$PROJECT_ROOT/logs/backend.pid"

echo "等待后端启动（最多 60 秒）..."
for i in {1..60}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✓ 后端服务启动成功"
        HEALTH=$(curl -s http://localhost:8080/actuator/health)
        STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*' | cut -d'"' -f4 | head -1)
        echo "  状态: $STATUS"
        exit 0
    fi
    sleep 1
done

echo "✗ 后端服务启动超时"
exit 1
