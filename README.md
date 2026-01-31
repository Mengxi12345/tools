# 内容聚合与归档工具

一个强大的内容聚合、管理和分析平台，支持从多个平台（GitHub、Medium、Reddit、知乎、掘金、CSDN）聚合内容，并提供搜索、分析、推荐等功能。

## 功能特性

### 核心功能
- ✅ **多平台支持**：GitHub、Medium、Reddit、知乎、掘金、CSDN
- ✅ **内容管理**：自动拉取、去重、分类、归档
- ✅ **搜索功能**：全文搜索、正则表达式、高级搜索
- ✅ **数据分析**：统计、可视化、趋势分析
- ✅ **通知系统**：邮件、Webhook、桌面通知
- ✅ **数据导出**：JSON、Markdown、CSV、HTML

### 高级功能
- ✅ **AI 功能**：内容摘要、情感分析、话题分析、推荐系统
- ✅ **权限控制**：RBAC 权限管理
- ✅ **监控运维**：Prometheus 监控、性能监控、安全审计
- ✅ **数据备份**：自动备份、增量备份

## 技术栈

### 后端
- Spring Boot 3.2.0
- PostgreSQL 15
- Redis 7
- Elasticsearch 8
- RabbitMQ 3
- Quartz（定时任务）
- JWT（认证）
- Micrometer + Prometheus（监控）

### 前端
- React 18 + TypeScript
- Ant Design 5
- Vite
- ECharts（图表）

## 快速开始

### 使用 Docker Compose（推荐）

```bash
# 启动所有服务（包括监控）
docker compose -f docker-compose.full.yml up -d

# 或只启动核心服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f backend

# 检查所有组件
./scripts/check-components.sh

# 测试组件连接
./scripts/test-all-components.sh
```

### 手动启动

#### 后端
```bash
cd backend
mvn spring-boot:run
```

#### 前端
```bash
cd frontend
npm install
npm run dev
```

## 访问地址

- **前端**：http://localhost:5173
- **后端 API**：http://localhost:8080/api/v1
- **API 文档**：http://localhost:8080/swagger-ui/index.html
- **监控指标**：http://localhost:8080/actuator/prometheus

## 文档

- [API 文档](docs/API_DOCUMENTATION.md)
- [开发指南](docs/DEVELOPMENT_GUIDE.md)
- [用户手册](docs/USER_GUIDE.md)
- [部署指南](docs/DEPLOYMENT_GUIDE.md)
- [完成总结](FINAL_SUMMARY.md)

## 项目结构

```
tools/
├── backend/              # Spring Boot 后端
│   ├── src/main/java/   # Java 源代码
│   └── src/main/resources/  # 配置文件
├── frontend/            # React 前端
│   ├── src/            # 源代码
│   └── dist/           # 构建输出
├── docs/               # 文档
└── docker-compose.yml  # Docker Compose 配置
```

## 开发

### 环境要求
- JDK 17+
- Maven 3.9+
- Node.js 18+
- Docker（可选）

### 运行测试
```bash
# 后端测试
cd backend
mvn test

# 前端测试
cd frontend
npm test
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
