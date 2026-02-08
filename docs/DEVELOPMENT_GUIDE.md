# 开发指南

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+
- Docker (可选)

## 项目结构

```
tools/
├── backend/                 # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/       # Java 源代码
│   │   │   └── resources/  # 配置文件
│   │   └── test/           # 测试代码
│   └── pom.xml             # Maven 配置
├── frontend/                # 前端项目
│   ├── src/
│   │   ├── components/     # React 组件
│   │   ├── pages/          # 页面组件
│   │   ├── services/       # API 服务
│   │   └── utils/          # 工具函数
│   └── package.json        # npm 配置
└── docs/                   # 文档
```

## 开发环境搭建

### 1. 克隆项目

```bash
git clone <repository-url>
cd tools
```

### 2. 启动数据库服务

使用 Docker Compose:

```bash
docker compose up -d postgres redis
```

或手动启动 PostgreSQL 和 Redis。

### 3. 配置数据库

创建数据库和用户（如果尚未创建）:

```sql
CREATE DATABASE caat_db;
CREATE USER caat_user WITH PASSWORD 'caat_password';
GRANT ALL PRIVILEGES ON DATABASE caat_db TO caat_user;
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端将在 `http://localhost:8080` 启动。

**上传目录**：从 `backend` 目录启动时，`uploads` 解析为 `backend/uploads`。若从项目根目录启动（如 IDE），会自动回退到 `backend/uploads`，确保图片正确加载。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将在 `http://localhost:3000` 启动（开发环境，Vite）。

也可以使用一键启动脚本（后端 + 前端）：

```bash
./scripts/start-all.sh
```

### 6. 本地开发：内容图片不显示（404）

前端（localhost:3000）与后端（localhost:8080）不同端口时，若内容详情中的图片返回 404，可创建 `frontend/.env.local`：

```bash
# 强制图片/附件直接请求后端
VITE_UPLOADS_BASE_URL=http://localhost:8080
```

然后重启前端 `npm run dev`。

## 代码规范

### Java 代码规范

- 使用 4 个空格缩进
- 类名使用 PascalCase
- 方法名和变量名使用 camelCase
- 常量使用 UPPER_SNAKE_CASE
- 每个类都要有 JavaDoc 注释

### TypeScript/React 代码规范

- 使用 2 个空格缩进
- 组件名使用 PascalCase
- 函数名和变量名使用 camelCase
- 使用函数式组件和 Hooks
- 每个组件都要有类型定义

## 测试

### 运行单元测试

```bash
cd backend
mvn test
```

### 运行集成测试

```bash
cd backend
mvn verify
```

### 前端测试

```bash
cd frontend
npm test
```

## 数据库迁移

项目使用 Flyway 进行数据库版本管理。迁移脚本位于 `backend/src/main/resources/db/migration/`。

迁移会在应用启动时自动执行。

## 构建和部署

### 构建后端

```bash
cd backend
mvn clean package
```

### 构建前端

```bash
cd frontend
npm run build
```

### Docker 部署

```bash
docker compose up -d
```

## 常见问题

### 1. 数据库连接失败

检查 PostgreSQL 是否正在运行，以及 `application.yml` 中的数据库配置是否正确。

### 2. Redis 连接失败

检查 Redis 是否正在运行，以及 `application.yml` 中的 Redis 配置是否正确。

### 3. 前端 API 调用失败

检查后端是否正在运行，以及 `frontend/src/services/api.ts` 中的 `API_BASE_URL` 配置是否正确。

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request
