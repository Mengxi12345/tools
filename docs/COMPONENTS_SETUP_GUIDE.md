# 组件安装和配置指南

本文档介绍如何安装和配置内容聚合工具所需的所有组件。

## 必需组件列表

### 核心组件（必需）
1. **PostgreSQL 15** - 主数据库
2. **Redis 7** - 缓存
3. **Elasticsearch 8** - 全文搜索
4. **RabbitMQ 3** - 消息队列
5. **Java 17+** - 后端运行环境
6. **Node.js 18+** - 前端构建环境

### 可选组件
7. **Prometheus** - 监控指标收集
8. **Grafana** - 监控可视化

## 快速安装（推荐）

### 使用 Docker Compose（最简单）

```bash
# 启动所有组件（包括监控）
docker compose -f docker-compose.full.yml up -d

# 或只启动核心组件
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f [service_name]
```

### 验证安装

```bash
# 运行组件检查脚本
./scripts/check-components.sh

# 运行连接测试脚本
./scripts/test-all-components.sh
```

## 手动安装

### 1. PostgreSQL

#### macOS (Homebrew)
```bash
brew install postgresql@15
brew services start postgresql@15

# 创建数据库和用户
createdb caat_db
createuser caat_user
psql -d caat_db -c "ALTER USER caat_user WITH PASSWORD 'caat_password';"
psql -d caat_db -c "GRANT ALL PRIVILEGES ON DATABASE caat_db TO caat_user;"
```

#### Docker
```bash
docker run --name caat-postgres \
  -e POSTGRES_USER=caat_user \
  -e POSTGRES_PASSWORD=caat_password \
  -e POSTGRES_DB=caat_db \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  -d postgres:15-alpine
```

### 2. Redis

#### macOS (Homebrew)
```bash
brew install redis
brew services start redis

# 测试连接
redis-cli ping
```

#### Docker
```bash
docker run --name caat-redis \
  -p 6379:6379 \
  -v redis_data:/data \
  -d redis:7-alpine redis-server --appendonly yes
```

### 3. Elasticsearch

#### macOS (Homebrew)
```bash
brew install elasticsearch
brew services start elasticsearch

# 测试连接
curl http://localhost:9200
```

#### Docker
```bash
docker run --name caat-elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -v elasticsearch_data:/usr/share/elasticsearch/data \
  -d docker.elastic.co/elasticsearch/elasticsearch:8.11.0
```

### 4. RabbitMQ

#### macOS (Homebrew)
```bash
brew install rabbitmq
brew services start rabbitmq
rabbitmq-plugins enable rabbitmq_management

# 访问管理界面
# http://localhost:15672
# 默认用户名/密码: guest/guest
```

#### Docker
```bash
docker run --name caat-rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin \
  -v rabbitmq_data:/var/lib/rabbitmq \
  -d rabbitmq:3-management-alpine
```

### 5. Prometheus（可选）

#### Docker
```bash
docker run --name caat-prometheus \
  -p 9090:9090 \
  -v $(pwd)/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro \
  -v prometheus_data:/prometheus \
  -d prom/prometheus:latest
```

### 6. Grafana（可选）

#### Docker
```bash
docker run --name caat-grafana \
  -p 3000:3000 \
  -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -v grafana_data:/var/lib/grafana \
  -d grafana/grafana:latest
```

## 配置验证

### 1. 检查组件状态

运行检查脚本：
```bash
./scripts/check-components.sh
```

### 2. 测试连接

运行测试脚本：
```bash
./scripts/test-all-components.sh
```

### 3. 手动验证

#### PostgreSQL
```bash
psql -h localhost -U caat_user -d caat_db -c "SELECT version();"
```

#### Redis
```bash
redis-cli -h localhost -p 6379 ping
redis-cli -h localhost -p 6379 set test_key "test_value"
redis-cli -h localhost -p 6379 get test_key
```

#### Elasticsearch
```bash
curl http://localhost:9200
curl http://localhost:9200/_cluster/health
```

#### RabbitMQ
```bash
curl -u admin:admin http://localhost:15672/api/overview
```

#### Prometheus
```bash
curl http://localhost:9090/-/healthy
```

#### Grafana
```bash
curl http://localhost:3000/api/health
```

## 应用配置

确保 `backend/src/main/resources/application.yml` 中的配置正确：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/caat_db
    username: caat_user
    password: caat_password
  
  data:
    redis:
      host: localhost
      port: 6379
  
  elasticsearch:
    uris: http://localhost:9200
  
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin
```

## 故障排查

### PostgreSQL 连接失败
```bash
# 检查服务状态
brew services list | grep postgresql
# 或
docker ps | grep postgres

# 检查端口
lsof -i :5432

# 测试连接
psql -h localhost -U caat_user -d caat_db
```

### Redis 连接失败
```bash
# 检查服务状态
brew services list | grep redis
# 或
docker ps | grep redis

# 测试连接
redis-cli -h localhost -p 6379 ping
```

### Elasticsearch 连接失败
```bash
# 检查服务状态
curl http://localhost:9200

# 查看日志
docker logs caat-elasticsearch
```

### RabbitMQ 连接失败
```bash
# 检查服务状态
rabbitmqctl status
# 或
docker ps | grep rabbitmq

# 测试管理界面
curl -u admin:admin http://localhost:15672/api/overview
```

## 访问地址

- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **Elasticsearch**: http://localhost:9200
- **RabbitMQ 管理界面**: http://localhost:15672 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **后端 API**: http://localhost:8080
- **API 文档**: http://localhost:8080/swagger-ui/index.html

## 下一步

组件安装完成后：
1. 运行 `./scripts/check-components.sh` 验证所有组件
2. 启动后端应用：`cd backend && mvn spring-boot:run`
3. 启动前端应用：`cd frontend && npm run dev`
4. 访问应用并测试功能

## 相关文档

- [部署指南](DEPLOYMENT_GUIDE.md)
- [开发指南](DEVELOPMENT_GUIDE.md)
- [快速开始](../QUICK_START.md)
