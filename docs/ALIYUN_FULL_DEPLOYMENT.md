# 阿里云全组件服务部署文档

本文档提供 CAAT 在阿里云 ECS 上通过 Docker 部署**全部服务组件**的完整指南，涵盖从创建云资源到服务上线的全流程。

## 目录

- [架构概览](#架构概览)
- [组件清单](#组件清单)
- [前置要求](#前置要求)
- [部署流程](#部署流程)
- [环境配置](#环境配置)
- [可选监控组件](#可选监控组件)
- [安全建议](#安全建议)
- [运维与维护](#运维与维护)
- [故障排查](#故障排查)

---

## 架构概览

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    阿里云 ECS 实例                        │
                    │                                                         │
  Internet          │  ┌─────────────┐     ┌─────────────────────────────────┐
  ───────────────►  │  │  Frontend   │────►│  Backend (Spring Boot)           │
  :80 / :8080      │  │  (Nginx)     │     │  - API / 业务逻辑                 │
                    │  │  :80        │     │  - 定时任务 / 导出 / 通知          │
                    │  └─────────────┘     └──────────┬────────────────────────┘
                    │           │                     │
                    │           │                     │  ┌──────────────┐
                    │           │                     ├─►│  PostgreSQL  │ :5432
                    │           │                     │  └──────────────┘
                    │           │                     │  ┌──────────────┐
                    │           │                     ├─►│  Redis       │ :6379
                    │           │                     │  └──────────────┘
                    │           │                     │  ┌──────────────┐
                    │           │                     ├─►│ Elasticsearch │ :9200
                    │           │                     │  └──────────────┘
                    │           │                     │  ┌──────────────┐
                    │           │                     └─►│  RabbitMQ    │ :5672/15672
                    │           │                        └──────────────┘
                    └─────────────────────────────────────────────────────────┘
```

---

## 组件清单

| 组件 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| **PostgreSQL** | postgres:15-alpine | 5432 | 主数据库，存储业务数据 |
| **Redis** | redis:7-alpine | 6379 | 缓存与会话 |
| **Elasticsearch** | elasticsearch:8.11.0 | 9200, 9300 | 全文搜索 |
| **RabbitMQ** | rabbitmq:3-management-alpine | 5672, 15672 | 消息队列（通知等） |
| **Backend** | 阿里云 ACR 镜像 | 8080 | Spring Boot 后端服务 |
| **Frontend** | 阿里云 ACR 镜像 | 80 | Nginx + 静态前端 |

**依赖关系**：Backend 依赖 PostgreSQL、Redis、Elasticsearch、RabbitMQ；Frontend 通过 Nginx 将 `/api` 代理到 Backend。

---

## 前置要求

### 1. 阿里云资源

- **ECS 实例**：建议 4 核 8GB 及以上（Elasticsearch 需约 2GB 内存）
- **系统盘**：至少 40GB
- **操作系统**：CentOS 7+、Ubuntu 20.04+、Alibaba Cloud Linux 3
- **网络**：公网 IP 或通过 SLB 暴露服务

### 2. 阿里云容器镜像服务（ACR）

- 已开通个人版或企业版实例
- 已创建命名空间与仓库
- 已设置访问凭证密码

### 3. 安全组规则

在 ECS 安全组中放行以下端口：

| 端口 | 协议 | 说明 |
|------|------|------|
| 22 | TCP | SSH（管理用） |
| 80 | TCP | 前端 Web 访问 |
| 8080 | TCP | 后端 API（可选，若通过 Nginx 统一入口可仅放行 80） |
| 5432 | TCP | PostgreSQL（建议仅内网访问，不对外） |
| 6379 | TCP | Redis（建议仅内网访问） |
| 9200 | TCP | Elasticsearch（建议仅内网访问） |
| 15672 | TCP | RabbitMQ 管理界面（建议仅内网或按需放行） |

---

## 部署流程

### 阶段一：本地构建与推送镜像

在本地或 CI 环境执行：

```bash
# 1. 构建镜像
./scripts/build-images.sh 1.0.0

# 2. 登录阿里云镜像仓库
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com

# 3. 推送镜像到阿里云
./scripts/push-images-aliyun.sh 1.0.0 梦溪mengxi
```

推送成功后，`.docker-images-aliyun.txt` 中会记录镜像地址。

### 阶段二：ECS 服务器准备

```bash
# 1. 安装 Docker
curl -fsSL https://get.docker.com | sh
sudo systemctl enable docker
sudo systemctl start docker

# 2. 安装 Docker Compose（若未预装）
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 阶段三：部署项目到 ECS

```bash
# 1. 克隆项目或上传部署文件
git clone <repository-url> /opt/caat
cd /opt/caat

# 2. 配置环境变量
cp .env.example .env
vim .env   # 修改 DB_PASSWORD、JWT_SECRET、镜像地址等

# 3. 登录阿里云镜像仓库
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com

# 4. 一键部署
chmod +x scripts/*.sh
./scripts/deploy-aliyun.sh 1.0.0
```

### 阶段四：验证部署

```bash
# 检查容器状态
docker compose -f docker-compose.prod.yml ps

# 后端健康检查
curl http://localhost:8080/actuator/health

# 前端访问
curl -I http://localhost:80
```

---

## 环境配置

### .env 配置说明

复制 `.env.example` 为 `.env` 后，需修改以下**必填项**：

| 变量 | 说明 | 示例 |
|------|------|------|
| `BACKEND_IMAGE` | 后端镜像地址 | `crpi-xxx.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0` |
| `FRONTEND_IMAGE` | 前端镜像地址 | `crpi-xxx.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0` |
| `DB_PASSWORD` | 数据库密码 | 使用 `openssl rand -base64 24` 生成 |
| `JWT_SECRET` | JWT 密钥 | 使用 `openssl rand -base64 48` 生成 |

### 可选配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_USER` | caat_user | 数据库用户名 |
| `DB_NAME` | caat_db | 数据库名 |
| `DB_PORT` | 5432 | PostgreSQL 端口 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `RABBITMQ_USER` | admin | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | admin | RabbitMQ 密码 |
| `BACKEND_PORT` | 8080 | 后端端口 |
| `FRONTEND_PORT` | 80 | 前端端口 |

### VPC 内网拉取镜像（推荐）

若 ECS 与镜像仓库同地域，可使用 VPC 内网地址，不消耗公网流量且速度更快：

```bash
# 在 .env 中修改镜像地址
BACKEND_IMAGE=crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0
FRONTEND_IMAGE=crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0

# 使用 VPC 地址登录
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com
```

---

## 可选监控组件

如需 Prometheus + Grafana 监控，可参考 `docker-compose.full.yml` 或单独启动：

```bash
# 启动监控（端口 9090、3000，注意 Grafana 与前端若同机部署需调整端口）
docker compose -f docker-compose.monitoring.yml up -d
```

- **Prometheus**：9090，采集后端 `/actuator/prometheus` 指标
- **Grafana**：3000，可视化仪表盘（默认 admin/admin）

若监控与业务服务分离部署，需在 Prometheus 配置中将 `backend:8080` 改为 ECS 内网 IP 或主机名。

---

## 安全建议

1. **强密码**：`DB_PASSWORD`、`JWT_SECRET`、`RABBITMQ_PASSWORD` 使用随机强密码
2. **最小放行**：安全组仅放行必要端口，数据库、Redis、ES、RabbitMQ 建议仅内网访问
3. **HTTPS**：生产环境建议在 ECS 前增加 SLB + 证书，或使用 Nginx 反向代理配置 SSL
4. **定期备份**：使用 `scripts/restore-backup.sh` 或 `pg_dump` 定期备份数据库
5. **镜像仓库**：ACR 访问凭证勿提交到代码库，仅在服务器 `.env` 中配置

---

## 运维与维护

### 查看服务状态

```bash
docker compose -f docker-compose.prod.yml ps
docker stats  # 资源使用
```

### 查看日志

```bash
# 全部服务
docker compose -f docker-compose.prod.yml logs -f

# 指定服务
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
```

### 重启服务

```bash
docker compose -f docker-compose.prod.yml restart backend
docker compose -f docker-compose.prod.yml restart
```

### 更新版本

```bash
# 1. 本地构建并推送新版本
./scripts/build-images.sh 1.0.1
./scripts/push-images-aliyun.sh 1.0.1 梦溪mengxi

# 2. 在服务器上更新 .env 中的镜像版本，然后
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### 回滚

```bash
# 修改 .env 中的镜像版本为旧版本，然后
docker compose -f docker-compose.prod.yml up -d
```

### 数据库备份

```bash
docker exec caat-postgres pg_dump -U caat_user caat_db > backup_$(date +%Y%m%d).sql
```

---

## 故障排查

### 容器无法启动

```bash
docker compose -f docker-compose.prod.yml logs backend
docker compose -f docker-compose.prod.yml logs elasticsearch
```

常见原因：Elasticsearch 内存不足（需至少 2GB）、数据库连接失败、环境变量未正确配置。

### 镜像拉取失败

```bash
# 检查登录状态
docker info | grep Username

# 重新登录
docker logout crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
```

### 后端健康检查失败

```bash
curl http://localhost:8080/actuator/health
docker compose -f docker-compose.prod.yml logs backend
```

检查：数据库、Redis、Elasticsearch、RabbitMQ 是否均已就绪（`depends_on` 会等待健康检查通过）。

### Elasticsearch 启动慢

首次启动约 1–2 分钟，部署脚本已包含 45 秒等待。若仍超时，可适当增加 ECS 内存或检查磁盘空间。

### 前端无法访问 API

确认 Nginx 配置中 `proxy_pass http://backend:8080` 正确，且 backend 与 frontend 在同一 Docker 网络中。

---

## 相关文档

- [阿里云容器镜像服务部署指南](./ALIYUN_DEPLOYMENT.md) - 镜像构建与推送
- [Docker 部署完整指南](./DOCKER_DEPLOYMENT.md) - 通用 Docker 部署
- [组件设置指南](./COMPONENTS_SETUP_GUIDE.md) - 各组件说明与配置
- [备份与恢复指南](./BACKUP_RESTORE_GUIDE.md) - 数据备份与恢复
