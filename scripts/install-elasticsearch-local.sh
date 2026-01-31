#!/bin/bash

# Elasticsearch 本地安装脚本（macOS）

set -e

echo "=========================================="
echo "安装 Elasticsearch（本地）"
echo "=========================================="

# 检查是否已安装
if command -v elasticsearch &> /dev/null; then
    echo "✓ Elasticsearch 已安装"
    elasticsearch --version 2>/dev/null || echo "版本信息不可用"
else
    echo "安装 Elasticsearch..."
    # Elasticsearch 需要通过 tap 安装
    brew tap elastic/tap
    brew install elastic/tap/elasticsearch-full
fi

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "错误: 需要 Java 17+ 才能运行 Elasticsearch"
    echo "请先安装 Java: brew install openjdk@17"
    exit 1
fi

# 配置 Elasticsearch（禁用安全功能，开发环境）
ES_CONFIG="/usr/local/etc/elasticsearch/elasticsearch.yml"
if [ -f "$ES_CONFIG" ]; then
    echo ""
    echo "配置 Elasticsearch..."
    
    # 备份原配置
    cp "$ES_CONFIG" "${ES_CONFIG}.backup"
    
    # 添加开发环境配置
    if ! grep -q "xpack.security.enabled" "$ES_CONFIG"; then
        echo "" >> "$ES_CONFIG"
        echo "# 开发环境配置" >> "$ES_CONFIG"
        echo "xpack.security.enabled: false" >> "$ES_CONFIG"
        echo "discovery.type: single-node" >> "$ES_CONFIG"
    fi
fi

# 启动 Elasticsearch
echo ""
echo "启动 Elasticsearch 服务..."
brew services start elasticsearch

# 等待服务启动
echo "等待 Elasticsearch 启动（最多 30 秒）..."
for i in {1..30}; do
    if curl -s http://localhost:9200 > /dev/null 2>&1; then
        echo "✓ Elasticsearch 启动成功"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Elasticsearch 启动超时"
        exit 1
    fi
    sleep 1
done

# 测试连接
echo ""
echo "测试 Elasticsearch 连接..."
HEALTH=$(curl -s http://localhost:9200/_cluster/health)
STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
VERSION=$(curl -s http://localhost:9200 | grep -o '"number":"[^"]*' | cut -d'"' -f4)

echo "✓ Elasticsearch 连接成功"
echo "  版本: $VERSION"
echo "  集群状态: $STATUS"

echo ""
echo "=========================================="
echo "Elasticsearch 安装完成"
echo "=========================================="
echo "Elasticsearch 运行在: http://localhost:9200"
echo "停止服务: brew services stop elasticsearch"
echo "查看日志: tail -f /usr/local/var/log/elasticsearch.log"
