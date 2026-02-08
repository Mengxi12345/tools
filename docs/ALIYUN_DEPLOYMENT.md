# 阿里云容器镜像服务部署指南

本文档提供使用阿里云容器镜像服务（ACR）部署 CAAT 的详细指南。

## 前置要求

- 已开通阿里云容器镜像服务
- 已创建个人版实例
- 已设置访问凭证密码

## 镜像仓库信息

- **Registry 地址**: `crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com`
- **VPC 内网地址**: `crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com`
- **命名空间**: `mengxi666`
- **仓库名称**: `mengxi666`
- **用户名**: 阿里云账号全名（如：梦溪mengxi）

## 快速开始

### 方式一：一键构建并推送（推荐）

```bash
# 构建镜像并推送到阿里云（会提示登录）
./scripts/build-images-aliyun.sh 1.0.0 梦溪mengxi

# 若 ECS 与镜像仓库同 VPC，使用内网地址（更快、不耗公网流量）
./scripts/build-images-aliyun.sh 1.0.0 梦溪mengxi --vpc
```

脚本会自动：构建后端和前端镜像 → 登录阿里云 ACR → 标记并推送。

### 方式二：分步执行

#### 1. 构建本地镜像

```bash
./scripts/build-images.sh 1.0.0
```

#### 2. 登录阿里云镜像仓库

```bash
# 使用公网地址登录
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com

# 如果使用 VPC 网络，使用内网地址（更快且不消耗公网流量）
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com
```

**注意：**
- 用户名为阿里云账号全名
- 密码为开通服务时设置的密码，可在[访问凭证页面](https://cr.console.aliyun.com/cn-beijing/instances/personal)修改
- 使用 RAM 用户（子账号）登录时，不支持企业别名带有英文半角句号（.）

#### 3. 推送镜像

```bash
./scripts/push-images-aliyun.sh 1.0.0 梦溪mengxi
```

脚本会自动：读取本地构建的镜像 → 标记为阿里云镜像标签 → 推送到阿里云仓库

#### 手动推送

```bash
# 标记后端镜像
docker tag content-aggregator-backend:1.0.0 \
  crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0

# 标记前端镜像
docker tag content-aggregator-frontend:1.0.0 \
  crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0

# 推送后端镜像
docker push crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0

# 推送前端镜像
docker push crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0
```

## 镜像标签说明

推送脚本会自动生成以下镜像标签：

- **后端**: `crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0`
- **前端**: `crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0`

## 在服务器上部署

### 1. 配置环境变量

复制 `.env.example` 为 `.env` 并修改：

```bash
cp .env.example .env
vim .env  # 或使用其他编辑器
```

**可选（前端构建时传入）：**
- `VITE_API_BASE_URL`：后端 API 地址，当前端与后端不同域名时设置为完整 URL（如 `https://api.example.com/api/v1`）
- `VITE_UPLOADS_BASE_URL`：图片/附件 base URL，当前端与后端不同域名时设置（如 `https://api.example.com`），图片会从该地址加载

**必填项：**
- `BACKEND_IMAGE`、`FRONTEND_IMAGE`：阿里云镜像地址（推送后从 `.docker-images-aliyun.txt` 获取）
- `DB_PASSWORD`：数据库密码（生产环境务必使用强密码）
- `JWT_SECRET`：JWT 密钥（至少 32 字符，建议 `openssl rand -base64 48` 生成）

### 2. 在服务器上登录

```bash
# 登录阿里云镜像仓库
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
```

### 3. 部署服务

```bash
# 使用一键部署脚本（推荐）
./scripts/deploy-aliyun.sh 1.0.0

# 资源受限（如 2GB 内存）时，可不启用 Elasticsearch
./scripts/deploy-aliyun.sh 1.0.0 --no-es

# 或手动部署
docker compose -f docker-compose.prod.yml up -d
# 不启用 ES: docker compose -f docker-compose.prod-no-es.yml up -d
```

部署脚本会自动拉取镜像并启动全部服务（PostgreSQL、Redis、Elasticsearch、RabbitMQ、后端、前端）。

## VPC 网络优化

如果服务器位于阿里云 VPC 网络内，使用内网地址可以：

- **提升推送速度**
- **不消耗公网流量**
- **更稳定可靠**

### 使用 VPC 内网地址

```bash
# 登录（使用 VPC 地址）
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com

# 修改脚本中的 REGISTRY 地址
# 或手动使用 VPC 地址标记和推送镜像
docker tag content-aggregator-backend:1.0.0 \
  crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0
docker push crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0
```

## 从 Registry 拉取镜像

```bash
# 拉取后端镜像
docker pull crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0

# 拉取前端镜像
docker pull crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.0
```

## 更新镜像

### 1. 构建新版本

```bash
./scripts/build-images.sh 1.0.1
```

### 2. 推送新版本

```bash
./scripts/push-images-aliyun.sh 1.0.1 梦溪mengxi
```

### 3. 在服务器上更新

```bash
# 拉取新版本
docker pull crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.1
docker pull crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.1

# 更新部署
export BACKEND_IMAGE=crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.1
export FRONTEND_IMAGE=crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-frontend:1.0.1
docker compose -f docker-compose.prod.yml up -d --pull always
```

## 故障排查

### 登录失败

```bash
# 检查用户名是否正确（使用账号全名）
# 检查密码是否正确（可在访问凭证页面重置）

# 重新登录
docker logout crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
docker login --username=梦溪mengxi crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
```

### 推送失败

```bash
# 检查镜像是否存在
docker images | grep content-aggregator

# 检查镜像标签是否正确
docker tag content-aggregator-backend:1.0.0 \
  crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com/mengxi666/mengxi666-backend:1.0.0

# 检查网络连接
ping crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com
```

### 拉取失败

```bash
# 检查是否已登录
docker info | grep Username

# 检查镜像地址是否正确
# 检查网络连接
```

## 相关文档

- [阿里云完整部署流程](./ALIYUN_FULL_DEPLOYMENT.md) - 从创建 ECS 到服务上线的完整指南
- [Docker 部署完整指南](./DOCKER_DEPLOYMENT.md)
- [Docker 快速参考](./DOCKER_QUICK_START.md)
- [阿里云容器镜像服务文档](https://help.aliyun.com/product/60716.html)
