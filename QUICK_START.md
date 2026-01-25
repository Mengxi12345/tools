# 快速开始指南

## 前提条件

确保已安装以下工具：
- ✅ Java 17
- ✅ Maven 3.9+
- ✅ Node.js 18+
- ✅ Colima + Docker CLI

## 启动开发环境

### 1. 启动 Colima（如果未运行）

```bash
colima start --cpu 2 --memory 4 --vm-type vz
```

### 2. 启动数据库服务

```bash
cd /Users/a17575/project/tools
docker compose up -d
```

等待所有服务启动（约 30-60 秒），然后验证：

```bash
# 查看服务状态
docker compose ps

# 所有服务应该显示为 "healthy" 或 "running"
```

### 3. 验证数据库连接

```bash
# PostgreSQL
docker compose exec postgres psql -U caat_user -d caat_db -c "SELECT version();"

# Redis
docker compose exec redis redis-cli PING

# Elasticsearch
curl http://localhost:9200

# RabbitMQ 管理界面
open http://localhost:15672
# 用户名：admin，密码：admin
```

### 4. 启动后端服务

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_HOME=/usr/local/maven
export PATH="$MAVEN_HOME/bin:$PATH"
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

### 5. 启动前端服务

```bash
cd frontend
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
npm run dev
```

前端服务将在 http://localhost:3000 启动

## 常用命令

### 停止服务

```bash
# 停止 Docker 服务
docker compose down

# 停止 Colima
colima stop
```

### 查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f postgres
```

### 重启服务

```bash
# 重启特定服务
docker compose restart postgres

# 重启所有服务
docker compose restart
```

## 故障排除

### Colima 无法启动

```bash
# 查看状态
colima status

# 查看日志
colima logs

# 重启
colima stop
colima start --cpu 2 --memory 4 --vm-type vz
```

### 端口被占用

检查端口占用：
```bash
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9200  # Elasticsearch
```

修改 `docker-compose.yml` 中的端口映射。

### 数据库连接失败

确保：
1. Colima 正在运行：`colima status`
2. Docker 服务已启动：`docker compose ps`
3. 服务健康：所有服务显示为 "healthy"

## 下一步

环境就绪后，可以继续执行开发计划中的任务：
- 数据模型设计与实现
- 平台管理模块开发
- 用户管理模块开发
- 等等...

参考 `plan.md` 了解详细开发计划。
