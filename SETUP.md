# 环境安装指南

## 重要提示

以下安装任务需要管理员权限或用户交互，需要手动完成：

### 1. Java 17 安装

由于需要管理员权限，请手动执行以下命令之一：

**方式一：使用 Homebrew Cask（推荐）**
```bash
brew install --cask temurin@17
```

安装完成后，设置环境变量：
```bash
echo 'export PATH="/usr/local/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME="/usr/local/opt/openjdk@17"' >> ~/.zshrc
source ~/.zshrc
```

**方式二：使用 Docker（如果已安装 Docker）**
可以使用 Docker 运行 Java 环境，但开发时仍建议本地安装。

验证安装：
```bash
java -version
# 预期输出：openjdk version "17.x.x"
```

### 2. Maven 安装

```bash
brew install maven
mvn -version
```

### 3. Docker 安装（使用 Colima）

由于 macOS 12.7.4 不支持 Docker Desktop，我们使用 Colima + Docker CLI：

```bash
# 安装 Colima 和 Docker CLI
brew install colima docker docker-compose

# 启动 Colima
colima start --cpu 2 --memory 4

# 验证安装
docker --version
docker compose version
docker ps
```

详细说明请参考 [COLIMA_SETUP.md](./COLIMA_SETUP.md)

### 4. Node.js 18+ 安装

**推荐使用 nvm 管理 Node.js 版本：**

```bash
# 安装 nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash

# 重新加载 shell
source ~/.zshrc

# 安装 Node.js 18 LTS
nvm install 18
nvm use 18
nvm alias default 18

# 验证
node -v
npm -v
```

## 使用 Docker Compose 启动数据库服务

如果已安装 Docker，可以使用项目根目录的 `docker-compose.yml` 启动所有数据库服务：

```bash
# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

这将启动：
- PostgreSQL (端口 5432)
- Redis (端口 6379)
- Elasticsearch (端口 9200)
- RabbitMQ (端口 5672, 管理界面 15672)

## 验证环境

完成所有安装后，运行以下命令验证：

```bash
# Java
java -version

# Maven
mvn -version

# Docker
docker --version
docker compose version

# Node.js
node -v
npm -v
```

## 下一步

环境安装完成后，可以继续执行开发计划中的其他任务。
