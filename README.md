# 内容聚合与归档工具（CAAT）

一个内容聚合、管理和分析平台，支持从多平台拉取内容，并提供搜索、分析、通知、导出等功能。

## 功能特性

### 核心功能
- **多平台支持**：GitHub、知乎、掘金、CSDN、Medium、Reddit、知识星球（ZSXQ）、TimeStore、微博、Twitter 等
- **内容管理**：自动拉取、去重、分类、归档；支持按用户/平台查看
- **定时拉取**：Quartz 定时任务（每 10 分钟，可开关）；调度状态 JDBC 持久化，重启不丢失
- **搜索**：全文搜索（Elasticsearch）、关键词与高级筛选
- **通知**：邮件、Webhook、QQ 群（go-cqhttp / Mirai）
- **导出**：JSON、Markdown、CSV 等

### 其他功能
- **统计与分析**：内容统计、可视化
- **权限与用户**：JWT 登录、用户组、RBAC
- **备份与恢复**：数据备份、恢复脚本
- **监控**：Prometheus 指标、健康检查（可选 Grafana）

## 技术栈

| 类别   | 技术 |
|--------|------|
| 后端   | Spring Boot 3.2、Java 17、PostgreSQL 15、Redis 7、Flyway |
| 调度   | Quartz（JDBC 存储，与主库共用 PostgreSQL） |
| 搜索   | Elasticsearch 8（可选） |
| 消息   | RabbitMQ 3（可选） |
| 认证   | Spring Security、JWT |
| 监控   | Micrometer、Prometheus、Actuator |
| 前端   | React 18、TypeScript、Vite、Ant Design 5 |

## 快速开始

### 方式一：Docker Compose（推荐）

**仅核心服务（PostgreSQL + Redis + 后端 + 前端）：**

```bash
# 启动
docker compose up -d

# 查看状态
docker compose ps

# 查看后端日志
docker compose logs -f backend
```

前端需先构建后再用上述命令挂载；开发时也可本地跑前端（见下方「手动启动」）。

**含 Elasticsearch、RabbitMQ、监控等全量服务：**

```bash
docker compose -f docker-compose.full.yml up -d
```

### 方式二：手动启动

**1. 启动依赖（PostgreSQL、Redis）**

```bash
docker compose up -d db redis
```

**2. 启动后端**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

默认连接 `localhost:5432`（caat_db）、`localhost:6379`（Redis）。首次启动会执行 Flyway 迁移（含 Quartz 表）。

**3. 启动前端**

```bash
cd frontend
npm install
npm run dev
```

开发服务器端口 3000，API 请求通过 Vite 代理到 `http://localhost:8080`。

## 访问地址

| 用途     | 地址 |
|----------|------|
| 前端     | http://localhost:3000 |
| 后端 API | http://localhost:8080/api/v1 |
| API 文档 | http://localhost:8080/swagger-ui.html |
| 健康检查 | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:8080/actuator/prometheus |

## 项目结构

```
tools/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/       # 业务与适配器
│   ├── src/main/resources/
│   │   ├── application.yml  # 主配置
│   │   ├── application-dev.yml
│   │   └── db/migration/    # Flyway 迁移（含 V16 Quartz 表）
│   └── pom.xml
├── frontend/                # React + Vite 前端
│   ├── src/
│   └── dist/                # 生产构建（供 nginx 挂载）
├── docs/                    # 文档
├── scripts/                 # 运维与测试脚本
├── monitoring/              # Prometheus / Grafana 配置（可选）
├── docker-compose.yml       # 核心服务
├── docker-compose.full.yml # 全量服务
└── docker-compose.monitoring.yml
```

## 文档

- [API 文档](docs/API_DOCUMENTATION.md)
- [开发指南](docs/DEVELOPMENT_GUIDE.md)
- [部署指南](docs/DEPLOYMENT_GUIDE.md)
- [用户手册](docs/USER_GUIDE.md)
- [组件与本地搭建](docs/COMPONENTS_SETUP_GUIDE.md)
- [备份与恢复](docs/BACKUP_RESTORE_GUIDE.md)
- [TimeStore 配置](docs/TIMESTORE_SETUP.md)

## 开发

### 环境要求
- **JDK 17+**
- **Maven 3.9+**（或使用 `backend./mvnw`）
- **Node.js 18+**
- **Docker**（用于 PostgreSQL、Redis，可选 Elasticsearch、RabbitMQ）

### 运行与检查
```bash
# 后端测试
cd backend && mvn test

# 前端校验与构建
cd frontend && npm run lint && npm run build
```

### 可选组件
- **Elasticsearch**：全文搜索；未配置时相关功能不可用。
- **RabbitMQ**：消息队列；未配置时应用仍可启动（健康检查可关闭）。
- **QQ 群通知**：在「通知规则」中配置 go-cqhttp 或 Mirai API 地址与 Session Key。

## 许可证

MIT License

## 贡献

欢迎提交 Issue 与 Pull Request。
