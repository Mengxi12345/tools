#!/bin/bash

# Gradle 安装脚本（可选）

echo "=== Gradle 安装 ==="

# 检查是否已安装
if command -v gradle &> /dev/null; then
    echo "Gradle 已安装"
    gradle --version
else
    echo "安装 Gradle..."
    
    # macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install gradle
    fi
    
    # Linux
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        sudo apt-get update
        sudo apt-get install -y gradle
    fi
fi

echo "=== Gradle 安装完成 ==="
