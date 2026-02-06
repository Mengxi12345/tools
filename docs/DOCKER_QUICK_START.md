# Docker 部署快速参考

## 一、本地构建和测试

```bash
# 1. 构建镜像
./scripts/build-images.sh 1.0.0

# 2. 启动服务
docker compose up -d

# 3. 访问应用
# 前端: http://localhost:3000
# 后端: http://localhost:8080
```

## 二、构建并推送到镜像仓库

### Docker Hub

```bash
# 1. 构建镜像（指定仓库）
./scripts/build-images.sh 1.0.0 docker.io/mengxi

# 2. 登录镜像仓库
docker login docker.io

# 3. 推送镜像
./scripts/push-images.sh 1.0.0 docker.io/mengxi
```

### 阿里云容器镜像服务

```bash
# 1. 构建本地镜像
./scripts/build-images.sh 1.0.0

# 2. 登录阿里云镜像仓库
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com

# 3. 推送镜像（使用专用脚本）
./scripts/push-images-aliyun.sh 1.0.0 梦溪mengxi
```

## 三、服务器部署

```bash
# 1. 配置环境变量
cp .env.example .env
vim .env  # 修改密码和配置

# 2. 部署
./scripts/deploy-docker.sh production 1.0.0 docker.io/mengxi
```

## 四、常用命令

```bash
# 查看服务状态
docker compose -f docker-compose.prod.yml ps

# 查看日志
docker compose -f docker-compose.prod.yml logs -f backend

# 停止服务
docker compose -f docker-compose.prod.yml down

# 重启服务
docker compose -f docker-compose.prod.yml restart

# 更新部署
export BACKEND_IMAGE=docker.io/mengxi/content-aggregator-backend:1.0.1
export FRONTEND_IMAGE=docker.io/mengxi/content-aggregator-frontend:1.0.1
docker compose -f docker-compose.prod.yml up -d --pull always
```

## 五、组件说明

| 组件 | 镜像名称 | 端口 | 说明 |
|------|---------|------|------|
| 后端 | content-aggregator-backend | 8080 | Spring Boot 应用 |
| 前端 | content-aggregator-frontend | 3000 | Nginx + React 应用 |
| 数据库 | postgres:15-alpine | 5432 | PostgreSQL 数据库 |
| 缓存 | redis:7-alpine | 6379 | Redis 缓存 |
| 搜索 | elasticsearch:7.17.4 | 9200 | Elasticsearch（可选）|
| 消息队列 | rabbitmq:3-management-alpine | 5672, 15672 | RabbitMQ（可选）|

## 六、环境变量

主要环境变量（完整列表见 `.env.example`）：

- `DB_USER`: 数据库用户名
- `DB_PASSWORD`: 数据库密码
- `JWT_SECRET`: JWT 密钥（使用 `openssl rand -base64 32` 生成）
- `BACKEND_IMAGE`: 后端镜像地址
- `FRONTEND_IMAGE`: 前端镜像地址

## 七、故障排查

```bash
# 查看容器日志
docker logs caat-backend
docker logs caat-frontend

# 检查健康状态
curl http://localhost:8080/actuator/health

# 进入容器调试
docker exec -it caat-backend sh
```

详细文档请参考 [DOCKER_DEPLOYMENT.md](./DOCKER_DEPLOYMENT.md)
