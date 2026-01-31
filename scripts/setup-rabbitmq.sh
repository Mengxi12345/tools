#!/bin/bash

# RabbitMQ 安装和配置脚本

echo "=== RabbitMQ 安装和配置 ==="

# 检查是否已安装
if command -v rabbitmq-server &> /dev/null; then
    echo "RabbitMQ 已安装"
    rabbitmq-server --version
else
    echo "安装 RabbitMQ..."
    
    # macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install rabbitmq
    fi
    
    # Linux
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        sudo apt-get update
        sudo apt-get install -y rabbitmq-server
    fi
fi

# 启动 RabbitMQ
echo "启动 RabbitMQ 服务..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    brew services start rabbitmq
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    sudo systemctl start rabbitmq-server
    sudo systemctl enable rabbitmq-server
fi

# 启用管理插件
echo "启用管理插件..."
rabbitmq-plugins enable rabbitmq_management

# 创建用户（如果不存在）
echo "创建用户..."
rabbitmqctl add_user admin admin 2>/dev/null || echo "用户已存在"
rabbitmqctl set_user_tags admin administrator
rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"

echo "=== RabbitMQ 配置完成 ==="
echo "管理界面: http://localhost:15672"
echo "用户名: admin"
echo "密码: admin"
