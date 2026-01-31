#!/bin/bash

# 启动后端服务脚本

set -e

echo "=========================================="
echo "启动后端服务"
echo "=========================================="

cd backend

# 检查后端是否已运行
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "后端服务已在运行"
    exit 0
fi

echo "启动后端服务..."
nohup mvn spring-boot:run > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "后端 PID: $BACKEND_PID"
echo $BACKEND_PID > ../logs/backend.pid

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
