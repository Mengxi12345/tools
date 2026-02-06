# Docker 部署完整指南

本文档提供使用 Docker 部署 CAAT（内容聚合与归档工具）的完整指南，包括镜像构建、推送和部署的详细步骤。

## 目录

- [前置要求](#前置要求)
- [快速开始](#快速开始)
- [镜像构建](#镜像构建)
- [镜像推送](#镜像推送)
- [服务器部署](#服务器部署)
- [环境配置](#环境配置)
- [监控和维护](#监控和维护)
- [故障排查](#故障排查)

## 前置要求

### 本地开发环境

- Docker 20.10+
- Docker Compose 2.0+ 或 docker-compose 1.29+
- Maven 3.6+（用于本地构建后端）
- Node.js 18+（用于本地构建前端）

### 服务器环境

- Docker 20.10+
- Docker Compose 2.0+ 或 docker-compose 1.29+
- 至少 4GB 可用内存
- 至少 20GB 可用磁盘空间

### 镜像仓库（可选）

- Docker Hub 账号
- 或私有镜像仓库（如 Harbor、Nexus、AWS ECR 等）

## 快速开始

### 1. 本地构建和测试

```bash
# 克隆项目
git clone <repository-url>
cd tools

# 构建镜像
./scripts/build-images.sh 1.0.0

# 使用 docker-compose 启动（开发环境）
docker compose up -d

# 访问应用
# 前端: http://localhost:3000
# 后端: http://localhost:8080
```

### 2. 构建并推送到镜像仓库

```bash
# 构建镜像
./scripts/build-images.sh 1.0.0 docker.io/your-username

# 登录镜像仓库
docker login docker.io

# 推送镜像
./scripts/push-images.sh 1.0.0 docker.io/your-username
```

### 3. 在服务器上部署

```bash
# 在服务器上克隆项目或下载部署文件
git clone <repository-url>
cd tools

# 配置环境变量（见下方环境配置章节）
cp .env.example .env
vim .env

# 部署
./scripts/deploy-docker.sh production 1.0.0 docker.io/your-username
```

## 镜像构建

### 手动构建

#### 构建后端镜像

```bash
cd backend
docker build -t content-aggregator-backend:1.0.0 .
```

#### 构建前端镜像

```bash
cd frontend
docker build -t content-aggregator-frontend:1.0.0 .
```

### 使用构建脚本

```bash
# 构建本地镜像
./scripts/build-images.sh 1.0.0

# 构建并指定镜像仓库
./scripts/build-images.sh 1.0.0 docker.io/your-username

# 构建并打标签为 latest
./scripts/build-images.sh latest docker.io/your-username
```

### 构建参数说明

- `VERSION`: 镜像版本标签（默认: latest）
- `REGISTRY`: 镜像仓库地址（可选，如: docker.io/your-username）

### 镜像结构

#### 后端镜像

- **基础镜像**: `eclipse-temurin:17-jre-alpine`
- **构建阶段**: 使用 Maven 构建 JAR 包
- **运行阶段**: 仅包含 JRE 和 JAR 文件
- **端口**: 8080
- **健康检查**: `/actuator/health`

#### 前端镜像

- **基础镜像**: `nginx:alpine`
- **构建阶段**: 使用 Node.js 构建静态文件
- **运行阶段**: 使用 Nginx 提供静态文件服务
- **端口**: 80
- **代理**: `/api` 路径代理到后端服务

## 镜像推送

### 使用推送脚本

```bash
# 推送镜像到 Docker Hub
./scripts/push-images.sh 1.0.0 docker.io/your-username

# 推送镜像到私有仓库
./scripts/push-images.sh 1.0.0 registry.example.com/caat
```

### 手动推送

```bash
# 登录镜像仓库
docker login docker.io

# 标记镜像
docker tag content-aggregator-backend:1.0.0 docker.io/your-username/content-aggregator-backend:1.0.0
docker tag content-aggregator-frontend:1.0.0 docker.io/your-username/content-aggregator-frontend:1.0.0

# 推送镜像
docker push docker.io/your-username/content-aggregator-backend:1.0.0
docker push docker.io/your-username/content-aggregator-frontend:1.0.0
```

### 镜像仓库配置

#### Docker Hub

```bash
export REGISTRY="docker.io/your-username"
docker login docker.io
```

#### 阿里云容器镜像服务（ACR）

```bash
# 登录阿里云镜像仓库
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com

# 使用专用脚本推送（推荐）
./scripts/push-images-aliyun.sh 1.0.0 梦溪mengxi

# 或手动推送
export REGISTRY="crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666"
./scripts/push-images.sh 1.0.0 $REGISTRY
```

**注意：**
- 用户名使用阿里云账号全名（如：梦溪mengxi）
- 密码为开通服务时设置的密码，可在访问凭证页面修改
- 如果使用 VPC 网络，可使用内网地址：`crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com`

#### 私有仓库（Harbor）

```bash
export REGISTRY="harbor.example.com/caat"
docker login harbor.example.com
```

#### AWS ECR

```bash
# 获取登录令牌
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

export REGISTRY="123456789012.dkr.ecr.us-east-1.amazonaws.com/caat"
```

## 服务器部署

### 部署前准备

1. **安装 Docker 和 Docker Compose**

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

2. **准备部署文件**

```bash
# 在服务器上创建项目目录
mkdir -p /opt/caat
cd /opt/caat

# 克隆项目或上传部署文件
git clone <repository-url> .
# 或使用 scp 上传文件
```

3. **配置环境变量**

创建 `.env` 文件：

```bash
# 数据库配置
DB_USER=caat_user
DB_PASSWORD=your_secure_password
DB_NAME=caat_db
DB_PORT=5432

# Redis 配置
REDIS_PASSWORD=your_redis_password
REDIS_PORT=6379

# 应用配置
BACKEND_PORT=8080
FRONTEND_PORT=3000

# JWT 配置
JWT_SECRET=your_jwt_secret_key_minimum_256_bits_long
JWT_EXPIRATION=86400000

# 镜像配置（如果使用远程镜像）
BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.0
FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.0

# Elasticsearch（可选）
ELASTICSEARCH_URIS=http://elasticsearch:9200
ELASTICSEARCH_PORT=9200

# RabbitMQ（可选）
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=admin_password
RABBITMQ_MANAGEMENT_PORT=15672

# Spring Profile
SPRING_PROFILES_ACTIVE=prod

# API 日志
API_LOGGING_ENABLED=true
```

### 使用部署脚本

```bash
# 给脚本添加执行权限
chmod +x scripts/*.sh

# 部署生产环境
./scripts/deploy-docker.sh production 1.0.0 docker.io/your-username

# 部署开发环境（使用本地镜像）
./scripts/deploy-docker.sh development latest
```

### 手动部署

```bash
# 拉取镜像（如果使用远程镜像）
docker pull docker.io/your-username/content-aggregator-backend:1.0.0
docker pull docker.io/your-username/content-aggregator-frontend:1.0.0

# 设置环境变量
export BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.0
export FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.0

# 启动服务
docker compose -f docker-compose.prod.yml up -d

# 查看日志
docker compose -f docker-compose.prod.yml logs -f
```

### 启动完整服务（包含 Elasticsearch 和 RabbitMQ）

```bash
# 使用 profile 启动完整服务
docker compose -f docker-compose.prod.yml --profile full up -d
```

## 环境配置

### 生产环境配置

创建 `backend/src/main/resources/application-prod.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: redis
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS}
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}

server:
  port: 8080

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION}

app:
  upload-dir: /app/uploads
  api-logging:
    enabled: ${API_LOGGING_ENABLED:true}

logging:
  level:
    root: INFO
    com.caat: INFO
  file:
    name: /app/logs/application.log
```

### 安全配置建议

1. **修改默认密码**

```bash
# 使用强密码生成器生成密码
openssl rand -base64 32
```

2. **配置 JWT Secret**

```bash
# 生成 256 位密钥
openssl rand -base64 32
```

3. **配置防火墙**

```bash
# 只开放必要端口
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 3000/tcp  # 前端
sudo ufw allow 8080/tcp  # 后端（如果直接暴露）
sudo ufw enable
```

4. **使用 HTTPS**

建议使用 Nginx 反向代理配置 HTTPS：

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 监控和维护

### 查看服务状态

```bash
# 查看所有容器状态
docker compose -f docker-compose.prod.yml ps

# 查看资源使用情况
docker stats

# 查看日志
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
```

### 健康检查

```bash
# 后端健康检查
curl http://localhost:8080/actuator/health

# 前端健康检查
curl http://localhost:3000
```

### 备份和恢复

#### 数据库备份

```bash
# 手动备份
docker exec caat-db pg_dump -U caat_user caat_db > backup_$(date +%Y%m%d_%H%M%S).sql

# 恢复
docker exec -i caat-db psql -U caat_user caat_db < backup_20260206_120000.sql
```

#### 使用应用备份接口

```bash
# 创建备份
curl -X POST http://localhost:8080/api/v1/backup/database \
  -H "Authorization: Bearer YOUR_TOKEN"

# 列出备份
curl http://localhost:8080/api/v1/backup/list \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 更新部署

```bash
# 1. 构建新版本镜像
./scripts/build-images.sh 1.0.1 docker.io/your-username

# 2. 推送镜像
./scripts/push-images.sh 1.0.1 docker.io/your-username

# 3. 在服务器上更新
export BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.1
export FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.1
docker compose -f docker-compose.prod.yml up -d --pull always
```

### 回滚

```bash
# 回滚到之前的版本
export BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.0
export FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.0
docker compose -f docker-compose.prod.yml up -d
```

## 故障排查

### 常见问题

#### 1. 容器无法启动

```bash
# 查看容器日志
docker logs caat-backend
docker logs caat-frontend

# 查看详细错误
docker compose -f docker-compose.prod.yml logs backend
```

#### 2. 数据库连接失败

```bash
# 检查数据库容器状态
docker ps | grep caat-db

# 检查数据库连接
docker exec -it caat-db psql -U caat_user -d caat_db

# 检查网络连接
docker network inspect tools_caat-network
```

#### 3. 前端无法访问后端 API

```bash
# 检查 nginx 配置
docker exec caat-frontend cat /etc/nginx/conf.d/default.conf

# 检查后端服务
curl http://localhost:8080/actuator/health

# 检查网络
docker network inspect tools_caat-network
```

#### 4. 内存不足

```bash
# 检查内存使用
docker stats

# 调整 JVM 参数（在 .env 文件中）
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
```

#### 5. 磁盘空间不足

```bash
# 清理未使用的镜像
docker image prune -a

# 清理未使用的卷
docker volume prune

# 清理未使用的容器
docker container prune
```

### 日志位置

- **后端日志**: `/app/logs/application.log`（容器内）或 `backend_logs` 卷
- **前端日志**: Nginx 访问日志在容器内 `/var/log/nginx/`
- **数据库日志**: PostgreSQL 日志在 `postgres_data` 卷

### 调试模式

```bash
# 以后台模式启动并查看日志
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f

# 进入容器调试
docker exec -it caat-backend sh
docker exec -it caat-frontend sh
```

## 性能优化

### 1. 数据库优化

```bash
# 调整 PostgreSQL 配置
docker exec -it caat-db psql -U caat_user -d caat_db -c "SHOW shared_buffers;"
```

### 2. Redis 优化

```bash
# 检查 Redis 内存使用
docker exec -it caat-redis redis-cli INFO memory
```

### 3. 应用优化

在 `.env` 文件中调整 JVM 参数：

```bash
JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## 附录

### 完整部署命令示例

```bash
# 1. 构建镜像
./scripts/build-images.sh 1.0.0 docker.io/your-username

# 2. 推送镜像
./scripts/push-images.sh 1.0.0 docker.io/your-username

# 3. 在服务器上部署
ssh user@server
cd /opt/caat
git pull
./scripts/deploy-docker.sh production 1.0.0 docker.io/your-username
```

### 环境变量参考

完整的环境变量列表请参考 `docker-compose.prod.yml` 文件中的 `environment` 部分。

### 相关文档

- [开发指南](./DEVELOPMENT_GUIDE.md)
- [API 文档](./API_DOCUMENTATION.md)
- [组件设置指南](./COMPONENTS_SETUP_GUIDE.md)
