# 部署文件清单

本文档列出所有与 Docker 部署相关的文件和脚本。

## 核心文件

### Dockerfile

| 文件 | 位置 | 说明 |
|------|------|------|
| 后端 Dockerfile | `backend/Dockerfile` | 多阶段构建，包含 Maven 构建和 JRE 运行环境 |
| 前端 Dockerfile | `frontend/Dockerfile` | 多阶段构建，包含 Node.js 构建和 Nginx 运行环境 |

### Docker Compose 配置

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | 开发环境配置（核心服务） |
| `docker-compose.prod.yml` | 生产环境配置（支持环境变量） |
| `docker-compose.full.yml` | 完整服务配置（包含 Elasticsearch、RabbitMQ 等） |
| `docker-compose.monitoring.yml` | 监控服务配置（Prometheus、Grafana） |

### 部署脚本

| 脚本 | 位置 | 功能 |
|------|------|------|
| `build-images.sh` | `scripts/build-images.sh` | 构建前后端镜像 |
| `push-images.sh` | `scripts/push-images.sh` | 推送镜像到远程仓库（通用） |
| `push-images-aliyun.sh` | `scripts/push-images-aliyun.sh` | 推送镜像到阿里云容器镜像服务 |
| `deploy-aliyun.sh` | `scripts/deploy-aliyun.sh` | 阿里云 ECS 一键部署脚本 |
| `deploy-docker.sh` | `scripts/deploy-docker.sh` | 在服务器上部署服务 |

### 配置文件

| 文件 | 说明 |
|------|------|
| `.env.example` | 环境变量配置示例 |
| `.dockerignore` | Docker 构建忽略文件 |
| `backend/.dockerignore` | 后端构建忽略文件 |
| `frontend/.dockerignore` | 前端构建忽略文件 |
| `frontend/nginx.conf` | Nginx 配置文件 |

## 文档

| 文档 | 位置 | 说明 |
|------|------|------|
| 阿里云完整部署 | `docs/ALIYUN_FULL_DEPLOYMENT.md` | 阿里云 ECS 从零到上线的完整流程 |
| Docker 部署完整指南 | `docs/DOCKER_DEPLOYMENT.md` | 详细的部署文档 |
| Docker 快速参考 | `docs/DOCKER_QUICK_START.md` | 快速命令参考 |
| 部署文件清单 | `docs/DEPLOYMENT_FILES.md` | 本文档 |

## 使用流程

### 1. 本地开发

```bash
# 使用开发环境配置
docker compose up -d
```

### 2. 构建镜像

```bash
# 构建本地镜像
./scripts/build-images.sh 1.0.0

# 构建并指定仓库
./scripts/build-images.sh 1.0.0 docker.io/your-username
```

### 3. 推送镜像

```bash
# 推送到远程仓库
./scripts/push-images.sh 1.0.0 docker.io/your-username
```

### 4. 服务器部署

```bash
# 配置环境变量
cp .env.example .env
vim .env

# 部署
./scripts/deploy-docker.sh production 1.0.0 docker.io/your-username
```

## 组件说明

### 核心服务

- **后端** (`content-aggregator-backend`)
  - 端口: 8080
  - 健康检查: `/actuator/health`
  - 镜像: `eclipse-temurin:17-jre-alpine`

- **前端** (`content-aggregator-frontend`)
  - 端口: 3000 (映射到容器 80)
  - 镜像: `nginx:alpine`

### 依赖服务

- **PostgreSQL** (`postgres:15-alpine`)
  - 端口: 5432
  - 数据卷: `postgres_data`

- **Redis** (`redis:7-alpine`)
  - 端口: 6379
  - 数据卷: `redis_data`

### 可选服务

- **Elasticsearch** (`elasticsearch:7.17.4`)
  - 端口: 9200
  - 数据卷: `elasticsearch_data`
  - Profile: `full`

- **RabbitMQ** (`rabbitmq:3-management-alpine`)
  - 端口: 5672 (AMQP), 15672 (Management)
  - 数据卷: `rabbitmq_data`
  - Profile: `full`

## 环境变量

主要环境变量（完整列表见 `.env.example`）：

### 必需配置

- `DB_USER`: 数据库用户名
- `DB_PASSWORD`: 数据库密码
- `DB_NAME`: 数据库名称
- `JWT_SECRET`: JWT 密钥

### 可选配置

- `BACKEND_IMAGE`: 后端镜像地址
- `FRONTEND_IMAGE`: 前端镜像地址
- `ELASTICSEARCH_URIS`: Elasticsearch 地址
- `RABBITMQ_HOST`: RabbitMQ 地址
- `SPRING_PROFILES_ACTIVE`: Spring Profile

## 数据卷

| 卷名 | 说明 |
|------|------|
| `postgres_data` | PostgreSQL 数据 |
| `redis_data` | Redis 数据 |
| `elasticsearch_data` | Elasticsearch 数据 |
| `rabbitmq_data` | RabbitMQ 数据 |
| `backend_uploads` | 后端上传文件 |
| `backend_logs` | 后端日志文件 |

## 网络

所有服务连接到 `caat-network` 网络，使用 bridge 驱动。

## 健康检查

所有服务都配置了健康检查：

- **后端**: HTTP GET `/actuator/health`
- **前端**: HTTP GET `/`
- **数据库**: `pg_isready`
- **Redis**: `redis-cli ping`
- **Elasticsearch**: `curl /_cluster/health`
- **RabbitMQ**: `rabbitmq-diagnostics ping`

## 安全建议

1. **修改默认密码**
   - 数据库密码
   - Redis 密码（如启用）
   - RabbitMQ 密码

2. **生成安全密钥**
   ```bash
   # JWT Secret
   openssl rand -base64 32
   ```

3. **配置防火墙**
   - 只开放必要端口
   - 使用 HTTPS（通过 Nginx 反向代理）

4. **使用非 root 用户**
   - 后端容器已配置非 root 用户运行

5. **定期更新镜像**
   - 关注安全更新
   - 定期重建和部署镜像

## 备份和恢复

### 数据库备份

```bash
# 备份
docker exec caat-db pg_dump -U caat_user caat_db > backup.sql

# 恢复
docker exec -i caat-db psql -U caat_user caat_db < backup.sql
```

### 卷备份

```bash
# 备份数据卷
docker run --rm -v postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/postgres_data.tar.gz /data
```

## 监控

### 健康检查端点

- 后端: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### 日志查看

```bash
# 查看所有服务日志
docker compose -f docker-compose.prod.yml logs -f

# 查看特定服务日志
docker compose -f docker-compose.prod.yml logs -f backend
```

## 更新和回滚

### 更新

```bash
# 1. 构建新版本
./scripts/build-images.sh 1.0.1 docker.io/your-username

# 2. 推送镜像
./scripts/push-images.sh 1.0.1 docker.io/your-username

# 3. 更新部署
export BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.1
export FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.1
docker compose -f docker-compose.prod.yml up -d --pull always
```

### 回滚

```bash
# 回滚到之前版本
export BACKEND_IMAGE=docker.io/your-username/content-aggregator-backend:1.0.0
export FRONTEND_IMAGE=docker.io/your-username/content-aggregator-frontend:1.0.0
docker compose -f docker-compose.prod.yml up -d
```

## 故障排查

### 查看容器状态

```bash
docker compose -f docker-compose.prod.yml ps
```

### 查看日志

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

### 进入容器

```bash
docker exec -it caat-backend sh
docker exec -it caat-frontend sh
```

### 检查网络

```bash
docker network inspect tools_caat-network
```

## 相关文档

- [Docker 部署完整指南](./DOCKER_DEPLOYMENT.md)
- [Docker 快速参考](./DOCKER_QUICK_START.md)
- [开发指南](./DEVELOPMENT_GUIDE.md)
- [API 文档](./API_DOCUMENTATION.md)
