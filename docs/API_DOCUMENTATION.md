# API 文档

## 概述

本文档描述了内容聚合与归档工具的所有 API 接口。

## 基础信息

- **Base URL**: `http://localhost:8080/api/v1`
- **认证方式**: JWT Token
- **数据格式**: JSON

## 认证接口

### 用户登录

```
POST /auth/login
```

**请求体**:
```json
{
  "username": "string",
  "password": "string"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "string",
    "username": "string",
    "userId": "string"
  }
}
```

### 用户注册

```
POST /auth/register
```

**请求体**:
```json
{
  "username": "string",
  "password": "string",
  "email": "string"
}
```

## 平台管理接口

### 获取平台列表

```
GET /platforms
```

### 创建平台

```
POST /platforms
```

**请求体**:
```json
{
  "name": "string",
  "type": "string",
  "config": {}
}
```

### 测试平台连接

```
POST /platforms/{id}/test
```

## 用户管理接口

### 获取用户列表

```
GET /users?page=0&size=20&sortBy=priority&sortDir=DESC
```

### 创建用户

```
POST /users
```

**请求体**:
```json
{
  "platformId": "uuid",
  "username": "string",
  "userId": "string",
  "tags": ["string"],
  "priority": 0
}
```

## 内容管理接口

### 获取内容列表

```
GET /contents?page=0&size=20&platformId=uuid&userId=uuid&keyword=string
```

### 搜索内容

```
GET /contents/search?query=string&page=0&size=20
```

### 正则表达式搜索

```
GET /contents/search/regex?pattern=string&page=0&size=20
```

### 高级搜索

```
GET /contents/search/advanced?query=string&contentType=TEXT&page=0&size=20
```

### 获取搜索历史

```
GET /contents/search/history?limit=10
```

### 获取热门搜索

```
GET /contents/search/popular?limit=10
```

## 统计接口

### 内容统计

```
GET /stats/content?userId=uuid
```

### 平台分布

```
GET /stats/platform-distribution
```

### 用户统计

```
GET /stats/users
```

### 标签统计

```
GET /stats/tags
```

### 内容时间分布

```
GET /stats/content-time-distribution?days=30
```

### 内容类型分布

```
GET /stats/content-type-distribution
```

### 活跃用户排行

```
GET /stats/active-users-ranking?limit=10
```

### 内容增长趋势

```
GET /stats/content-growth-trend?days=30
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 2001 | 平台不存在 |
| 2002 | 平台已存在 |
| 2003 | 用户不存在 |
| 2004 | 用户已存在 |
| 2005 | 内容不存在 |
| 2008 | 平台连接失败 |
| 2009 | 平台用户不存在 |
| 3001 | 无效的 Token |
| 3002 | Token 已过期 |
| 3003 | 登录失败 |

## 使用示例

### 使用 curl

```bash
# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# 获取内容列表（需要 Token）
curl -X GET http://localhost:8080/api/v1/contents \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 使用 JavaScript

```javascript
// 登录
const response = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'password' })
});

const { data } = await response.json();
const token = data.token;

// 获取内容列表
const contentsResponse = await fetch('http://localhost:8080/api/v1/contents', {
  headers: { 'Authorization': `Bearer ${token}` }
});
```
