# 部署指南

## 环境要求

- Docker 和 Docker Compose
- PostgreSQL 15+
- Redis 7+
- Elasticsearch 8+（可选）
- RabbitMQ 3+（可选）

## 快速部署

### 1. 使用 Docker Compose（推荐）

```bash
# 克隆项目
git clone <repository-url>
cd tools

# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f backend
```

### 2. 手动部署

#### 后端部署

```bash
# 构建后端
cd backend
mvn clean package

# 运行 JAR
java -jar target/content-aggregator-1.0.0-SNAPSHOT.jar
```

#### 前端部署

```bash
# 构建前端
cd frontend
npm install
npm run build

# 使用 Nginx 部署
# 将 dist 目录内容复制到 Nginx 的 html 目录
cp -r dist/* /usr/share/nginx/html/
```

## 环境配置

### 开发环境

配置文件：`backend/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/caat_db
    username: caat_user
    password: caat_password
```

### 生产环境

配置文件：`backend/src/main/resources/application-prod.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
```

使用环境变量：

```bash
export DB_HOST=your-db-host
export DB_PORT=5432
export DB_NAME=caat_db
export DB_USERNAME=caat_user
export DB_PASSWORD=your-secure-password
```

## 监控配置

### Prometheus

1. 访问 `/actuator/prometheus` 获取指标
2. 配置 Prometheus 抓取：

```yaml
scrape_configs:
  - job_name: 'content-aggregator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Grafana

1. 添加 Prometheus 数据源
2. 导入预定义的仪表板（可选）
3. 配置告警规则

## 备份和恢复

### 自动备份

```bash
# 执行完整备份
curl -X POST http://localhost:8080/api/v1/backup/database

# 执行增量备份
curl -X POST http://localhost:8080/api/v1/backup/incremental?since=2026-01-01T00:00:00

# 列出所有备份
curl http://localhost:8080/api/v1/backup/list
```

### 手动备份

```bash
# PostgreSQL 备份
pg_dump -h localhost -U caat_user -d caat_db > backup.sql

# 恢复
psql -h localhost -U caat_user -d caat_db < backup.sql
```

## 健康检查

访问健康检查端点：

```bash
curl http://localhost:8080/actuator/health
```

## 故障排查

### 常见问题

1. **数据库连接失败**
   - 检查 PostgreSQL 是否运行
   - 检查连接配置是否正确
   - 检查防火墙设置

2. **Redis 连接失败**
   - 检查 Redis 是否运行
   - 检查连接配置

3. **前端 API 调用失败**
   - 检查后端是否运行
   - 检查 CORS 配置
   - 检查 API_BASE_URL 配置

## 性能优化

1. **数据库优化**
   - 定期执行 VACUUM
   - 监控慢查询
   - 调整连接池大小

2. **缓存优化**
   - 调整 Redis 内存限制
   - 配置合适的 TTL

3. **应用优化**
   - 调整 JVM 参数
   - 启用响应压缩
   - 配置合理的线程池大小
