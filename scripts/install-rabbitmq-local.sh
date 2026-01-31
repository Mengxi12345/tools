#!/bin/bash

# RabbitMQ 本地安装脚本（macOS）

set -e

echo "=========================================="
echo "安装 RabbitMQ（本地）"
echo "=========================================="

# 检查是否已安装
if command -v rabbitmq-server &> /dev/null; then
    echo "✓ RabbitMQ 已安装"
    rabbitmq-server --version 2>/dev/null || echo "版本信息不可用"
else
    echo "安装 RabbitMQ..."
    brew install rabbitmq
fi

# 添加到 PATH（如果还没有）
RABBITMQ_PATH="/usr/local/opt/rabbitmq/sbin"
if [[ ":$PATH:" != *":$RABBITMQ_PATH:"* ]]; then
    echo ""
    echo "添加 RabbitMQ 到 PATH..."
    echo 'export PATH="'$RABBITMQ_PATH':$PATH"' >> ~/.zshrc
    export PATH="$RABBITMQ_PATH:$PATH"
fi

# 启动 RabbitMQ
echo ""
echo "启动 RabbitMQ 服务..."
brew services start rabbitmq

# 等待服务启动
echo "等待 RabbitMQ 启动（最多 30 秒）..."
sleep 5

for i in {1..25}; do
    if rabbitmqctl status > /dev/null 2>&1; then
        echo "✓ RabbitMQ 启动成功"
        break
    fi
    if [ $i -eq 25 ]; then
        echo "✗ RabbitMQ 启动超时"
        exit 1
    fi
    sleep 1
done

# 启用管理插件
echo ""
echo "启用管理插件..."
rabbitmq-plugins enable rabbitmq_management

# 创建管理员用户（如果不存在）
echo ""
echo "配置管理员用户..."
if ! rabbitmqctl list_users | grep -q admin; then
    rabbitmqctl add_user admin admin
    rabbitmqctl set_user_tags admin administrator
    rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"
    echo "✓ 创建管理员用户: admin/admin"
else
    echo "✓ 管理员用户已存在"
fi

# 测试连接
echo ""
echo "测试 RabbitMQ 连接..."
if curl -s -u admin:admin http://localhost:15672/api/overview > /dev/null 2>&1; then
    VERSION=$(curl -s -u admin:admin http://localhost:15672/api/overview | grep -o '"rabbitmq_version":"[^"]*' | cut -d'"' -f4)
    echo "✓ RabbitMQ 连接成功"
    echo "  版本: $VERSION"
else
    echo "⚠ RabbitMQ 管理界面可能还未完全启动"
fi

echo ""
echo "=========================================="
echo "RabbitMQ 安装完成"
echo "=========================================="
echo "RabbitMQ 运行在: localhost:5672"
echo "管理界面: http://localhost:15672"
echo "用户名/密码: admin/admin"
echo "停止服务: brew services stop rabbitmq"
echo "查看日志: tail -f /usr/local/var/log/rabbitmq/rabbit@*.log"
