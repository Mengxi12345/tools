# 内容聚合与归档工具 (CAAT)

## 项目简介

一个智能化的内容聚合平台，通过 HTTP 接口从多个社交媒体和内容平台自动拉取指定用户发布的内容，进行统一归档、整理和展示。

## 项目结构

```
tools/
├── backend/          # Spring Boot 后端项目
├── frontend/          # React + TypeScript 前端项目
├── PROJECT_DESIGN.md # 产品设计文档
└── plan.md           # 开发计划
```

## 技术栈

### 后端
- Java 17
- Spring Boot 3.2.0
- PostgreSQL 15
- Redis 7
- Quartz (任务调度)

### 前端
- React 18
- TypeScript
- Vite
- Ant Design

## 开发环境要求

- macOS 12.7.4+ (Monterey)
- Java 17+
- Node.js 18+
- Docker Desktop (可选，用于数据库)

## 快速开始

### 后端

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 开发计划

详细开发计划请参考 [plan.md](./plan.md)
