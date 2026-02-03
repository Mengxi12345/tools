#!/bin/bash

# 防止系统休眠启动后端（macOS 使用 caffeinate，避免定时任务因休眠而停止）
# 用法: ./scripts/run-backend-no-sleep.sh [profile]
# 示例: ./scripts/run-backend-no-sleep.sh dev

set -e

PROFILE="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../backend" && pwd)"

cd "$BACKEND_DIR"

if [ "$(uname)" = "Darwin" ]; then
    echo "macOS 检测到，使用 caffeinate 防止系统休眠..."
    echo "后端将以 dev profile 启动，定时任务将按 10 分钟间隔执行"
    echo "按 Ctrl+C 停止服务"
    echo ""
    exec caffeinate -i ./mvnw spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
else
    echo "当前系统: $(uname)，直接启动后端（非 macOS 无 caffeinate）"
    echo "后端将以 $PROFILE profile 启动"
    echo "按 Ctrl+C 停止服务"
    echo ""
    exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
fi
