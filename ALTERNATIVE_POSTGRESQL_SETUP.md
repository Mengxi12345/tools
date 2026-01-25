# PostgreSQL 替代安装方案

## 问题
QEMU 安装非常慢（需要 20-40 分钟），阻塞了 Colima 和 Docker 的启动。

## 替代方案

### 方案 1：直接安装 PostgreSQL（推荐）✅

**优点**：
- 安装快速（5-10 分钟）
- 不需要 Docker/Colima
- 性能更好（本地运行）
- 配置简单

**步骤**：
```bash
# 安装 PostgreSQL
brew install postgresql@15

# 启动 PostgreSQL 服务
brew services start postgresql@15

# 创建数据库和用户
createdb caat_db
createuser caat_user
psql -d caat_db -c "ALTER USER caat_user WITH PASSWORD 'caat_password';"
psql -d caat_db -c "GRANT ALL PRIVILEGES ON DATABASE caat_db TO caat_user;"
```

**修改应用配置**：
`application-dev.yml` 中的数据库连接已经配置为 `localhost:5432`，无需修改。

### 方案 2：使用 vz 虚拟化类型（如果支持）

**检查是否支持**：
```bash
colima start --vm-type vz --cpu 2 --memory 4
```

**注意**：vz 需要 macOS 13+ (Ventura)，您的系统是 macOS 12.7.4，可能不支持。

### 方案 3：使用 Lima（Colima 的底层工具）

**安装**：
```bash
brew install lima
limactl start template://docker
```

**优点**：
- 可能比 Colima 更快
- 更轻量级

**缺点**：
- 需要手动配置
- 可能也需要 QEMU

### 方案 4：继续等待 QEMU（不推荐）

**时间**：20-40 分钟

**优点**：
- 保持 Docker 环境一致性

**缺点**：
- 等待时间长
- 阻塞开发进度

## 推荐方案：直接安装 PostgreSQL

### 为什么推荐这个方案？

1. **快速**：安装只需 5-10 分钟
2. **简单**：配置简单，无需 Docker
3. **性能**：本地运行，性能更好
4. **开发友好**：适合开发环境
5. **不影响生产**：生产环境仍可使用 Docker

### 实施步骤

1. **安装 PostgreSQL**：
   ```bash
   brew install postgresql@15
   ```

2. **启动服务**：
   ```bash
   brew services start postgresql@15
   ```

3. **创建数据库**：
   ```bash
   createdb caat_db
   createuser caat_user
   psql -d caat_db -c "ALTER USER caat_user WITH PASSWORD 'caat_password';"
   psql -d caat_db -c "GRANT ALL PRIVILEGES ON DATABASE caat_db TO caat_user;"
   ```

4. **运行 Flyway 迁移**：
   Spring Boot 启动时会自动运行 Flyway 迁移，创建所有表结构。

5. **测试连接**：
   ```bash
   psql -U caat_user -d caat_db -c "SELECT version();"
   ```

6. **启动应用测试**：
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

## 对比

| 方案 | 安装时间 | 复杂度 | 性能 | 推荐度 |
|------|---------|--------|------|--------|
| 直接安装 PostgreSQL | 5-10 分钟 | 低 | 高 | ⭐⭐⭐⭐⭐ |
| vz 虚拟化 | 2-5 分钟 | 中 | 中 | ⭐⭐⭐（可能不支持） |
| Lima | 10-20 分钟 | 高 | 中 | ⭐⭐ |
| 等待 QEMU | 20-40 分钟 | 低 | 中 | ⭐ |

## 建议

**立即采用方案 1：直接安装 PostgreSQL**

这样可以：
- ✅ 快速开始 PostgreSQL 测试
- ✅ 不阻塞开发进度
- ✅ 后续仍可使用 Docker（QEMU 安装完成后）

## 更新时间
2026-01-25 20:45
