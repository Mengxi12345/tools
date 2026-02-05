#!/bin/bash

# 部署脚本

set -e

echo "=== 内容聚合工具部署脚本 ==="

# 检查环境
echo "检查环境..."
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "错误: Docker Compose 未安装"
    exit 1
fi

# 构建后端
echo "构建后端..."
cd backend
mvn clean package -DskipTests
cd ..

# 构建前端
echo "构建前端..."
cd frontend
npm install
npm run build
cd ..

# 停止旧容器
echo "停止旧容器..."
docker compose down

# 启动新容器
echo "启动容器..."
docker compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo "检查服务状态..."
docker compose ps

# 健康检查
echo "健康检查..."
sleep 5
curl -f http://localhost:8080/actuator/health || echo "健康检查失败，请检查日志"

echo "=== 部署完成 ==="
echo "前端: http://localhost:3000"
echo "后端: http://localhost:8080"
echo "API 文档: http://localhost:8080/swagger-ui.html"
