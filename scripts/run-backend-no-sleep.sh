#!/bin/bash

# 防止系统休眠启动后端（macOS 使用 caffeinate，避免定时任务因休眠而停止）
# 用法: ./scripts/run-backend-no-sleep.sh [profile]
# 示例: ./scripts/run-backend-no-sleep.sh dev
#
# caffeinate 参数说明：
#   -i: 防止系统空闲休眠（idle sleep）
#   -d: 防止显示器休眠（display sleep）
#   -m: 防止磁盘休眠（disk sleep）
#   -s: 防止系统休眠（system sleep），但允许显示器休眠
#   组合使用 -dim 可以防止所有类型的休眠

set -e

PROFILE="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../backend" && pwd)"

cd "$BACKEND_DIR"

if [ "$(uname)" = "Darwin" ]; then
    echo "macOS 检测到，使用 caffeinate 防止系统休眠..."
    echo "后端将以 $PROFILE profile 启动，定时任务将按配置的间隔执行"
    echo "防休眠模式：防止系统、显示器和磁盘休眠（-dim）"
    echo "按 Ctrl+C 停止服务"
    echo ""
    # 使用 -dim 组合：防止系统空闲休眠、显示器休眠和磁盘休眠
    exec caffeinate -dim ./mvnw spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
else
    echo "当前系统: $(uname)，直接启动后端（非 macOS 无 caffeinate）"
    echo "后端将以 $PROFILE profile 启动"
    echo "按 Ctrl+C 停止服务"
    echo ""
    exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
fi
