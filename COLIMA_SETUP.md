# Colima + Docker CLI 安装指南

## 什么是 Colima？

Colima (Containers on Lima) 是一个在 macOS 和 Linux 上运行容器运行时的工具。它可以在较旧的 macOS 版本上运行，不需要 Docker Desktop。

## 安装步骤

### 1. 安装 Colima 和 Docker CLI

```bash
# 安装 Colima 和 Docker CLI
brew install colima docker docker-compose

# 安装 QEMU（Colima 的依赖）
brew install qemu
```

### 2. 启动 Colima

```bash
# 启动 Colima（使用 VZ 虚拟化，不需要 QEMU）
colima start --cpu 2 --memory 4 --vm-type vz

# 或使用 QEMU（需要先安装：brew install qemu）
# colima start --cpu 2 --memory 4

# 或使用默认配置
# colima start

# 查看状态
colima status

# 注意：首次启动可能需要几分钟时间来下载和设置虚拟机
```

### 3. 验证安装

```bash
# 检查 Docker 版本
docker --version
docker compose version

# 测试 Docker
docker ps
docker run hello-world
```

## 常用命令

### Colima 管理

```bash
# 启动 Colima
colima start

# 停止 Colima
colima stop

# 重启 Colima
colima restart

# 查看状态
colima status

# 查看日志
colima logs

# 删除 Colima（会删除所有容器和数据）
colima delete
```

### Docker 使用

```bash
# 查看运行中的容器
docker ps

# 查看所有容器
docker ps -a

# 查看镜像
docker images

# 查看日志
docker logs <container_name>
```

### Docker Compose 使用

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务
docker compose down

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 重启特定服务
docker compose restart postgres
```

## 项目配置

项目已包含 `docker-compose.yml` 文件，包含以下服务：

- **PostgreSQL** (端口 5432)
- **Redis** (端口 6379)
- **Elasticsearch** (端口 9200, 9300)
- **RabbitMQ** (端口 5672, 管理界面 15672)

### 启动项目服务

```bash
cd /Users/a17575/project/tools
docker compose up -d
```

### 验证服务

```bash
# 检查所有服务状态
docker compose ps

# 测试 PostgreSQL
docker compose exec postgres psql -U caat_user -d caat_db -c "SELECT version();"

# 测试 Redis
docker compose exec redis redis-cli PING

# 测试 Elasticsearch
curl http://localhost:9200

# 访问 RabbitMQ 管理界面
open http://localhost:15672
# 用户名：admin，密码：admin
```

## 配置优化

### 增加资源分配

如果需要更多资源，可以停止 Colima 后重新启动：

```bash
colima stop
colima start --cpu 4 --memory 8
```

### 设置环境变量

Colima 启动后，Docker 命令会自动配置。如果需要手动设置：

```bash
# 查看 Docker 上下文
docker context ls

# 切换到 colima 上下文（如果需要）
docker context use colima
```

## 故障排除

### Colima 无法启动

```bash
# 查看详细日志
colima logs

# 检查系统要求
colima status

# 重新安装（如果问题持续）
colima delete
colima start
```

### Docker 命令无法连接

```bash
# 确保 Colima 正在运行
colima status

# 检查 Docker 上下文
docker context ls

# 重启 Colima
colima restart
```

### 端口冲突

如果遇到端口冲突，可以修改 `docker-compose.yml` 中的端口映射。

## 与 Docker Desktop 的区别

| 特性 | Docker Desktop | Colima |
|------|---------------|--------|
| macOS 版本要求 | Sonoma (14.0+) | 支持较旧版本 |
| 资源占用 | 较高 | 较低 |
| GUI 界面 | 有 | 无（命令行） |
| 性能 | 优秀 | 良好 |
| 免费 | 个人免费 | 完全免费 |

## 优势

1. ✅ 支持较旧的 macOS 版本（如 Monterey）
2. ✅ 资源占用更少
3. ✅ 完全免费
4. ✅ 命令行操作，适合开发环境
5. ✅ 与 Docker CLI 完全兼容

## 注意事项

1. Colima 需要先启动才能使用 Docker 命令
2. 系统重启后需要手动启动 Colima：`colima start`
3. 可以将 `colima start` 添加到启动脚本中自动启动

## 自动启动脚本（可选）

创建 `~/.zshrc` 或 `~/.bash_profile` 中的启动脚本：

```bash
# 检查 Colima 是否运行，如果没有则启动
if ! colima status 2>&1 | grep -q "Running"; then
    echo "Starting Colima..."
    colima start
fi
```

## 更新时间

2026-01-25
