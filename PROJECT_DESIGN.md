# 内容聚合与归档工具 - 产品设计文档

## 1. 项目概述

### 1.1 项目名称
**Content Aggregator & Archive Tool (CAAT)** - 内容聚合与归档工具

### 1.2 项目定位
一个智能化的内容聚合平台，通过 HTTP 接口从多个社交媒体和内容平台自动拉取指定用户发布的内容，进行统一归档、整理和展示，为用户提供便捷的阅读体验。

### 1.3 核心价值
- **一站式阅读**：统一管理多个平台的内容，无需切换不同应用
- **智能归档**：自动分类、去重、标签化，提升内容管理效率
- **个性化定制**：灵活配置拉取规则，满足不同用户需求
- **数据持久化**：永久保存重要内容，避免平台内容删除风险

### 1.4 目标用户
- 内容创作者（追踪同行动态）
- 研究人员（收集特定领域信息）
- 个人用户（管理自己的多平台内容）
- 企业用户（监控品牌相关讨论）

---

## 2. 功能需求

### 2.1 核心功能模块

#### 2.1.1 平台管理模块
**功能描述**：管理支持的内容平台及其配置

**详细功能**：
- 平台列表管理
  - 支持平台：微博、Twitter/X、GitHub、Medium、Reddit、知乎、掘金、CSDN 等
  - 平台状态监控（可用性检测）
  - 平台模板配置（快速添加新平台）
  
- 认证管理
  - API Key/Token 安全存储（加密）
  - OAuth 2.0 认证流程支持
  - 认证状态检测与自动刷新
  
- 平台配置
  - 拉取频率设置（实时/定时/手动）
  - 数据范围限制（时间范围、数量限制）
  - 内容类型过滤（文本/图片/视频/链接）

#### 2.1.2 用户管理模块
**功能描述**：管理需要追踪的用户列表

**详细功能**：
- 用户添加
  - 支持用户名/ID/URL 多种方式添加
  - 自动识别平台类型
  - 用户信息验证
  
- 用户分组
  - 自定义分组（如：技术大牛、产品经理、设计师）
  - 标签系统
  - 优先级设置
  
- 用户管理
  - 启用/禁用追踪
  - 编辑用户信息
  - 批量操作
  - 手动刷新内容
    - 快速刷新按钮（默认从最后拉取时间至今）
    - 自定义时间范围刷新（弹窗选择起始时间）
    - 刷新状态显示（进行中/已完成/失败）
    - 最后刷新时间显示

#### 2.1.3 内容拉取模块
**功能描述**：从各平台拉取用户发布的内容

**详细功能**：
- 拉取策略
  - 增量拉取（基于时间戳/ID）
  - 全量拉取（首次或手动触发）
  - 定时任务（Cron 表达式配置）
  - 实时推送（Webhook 支持）
  
- 定时任务管理
  - 全局定时任务开关（启用/禁用所有定时拉取任务）
  - 单用户定时任务开关（针对特定用户的定时拉取控制）
  - 定时任务状态监控（运行中/已暂停/异常）
  - 定时任务执行历史记录
  - 定时任务执行日志查看
  
- 手动刷新功能
  - 单用户刷新（针对指定作者）
    - 默认模式：从该用户最后拉取的文章时间点至今
    - 自定义时间范围：用户可选择指定起始时间至今
    - 刷新进度实时显示
    - 刷新结果统计（成功/失败数量）
  - 批量用户刷新（支持多选用户同时刷新）
  - 刷新任务队列管理（查看进行中的刷新任务）
  - 刷新历史记录（查看历史刷新操作）
  
- 错误处理
  - 失败重试机制（指数退避算法）
  - 速率限制处理（Token Bucket）
  - 错误日志记录
  - 告警通知
  
- 数据获取
  - 支持拉取历史数据（指定时间范围）
  - 分页处理
  - 并发控制

#### 2.1.4 数据存储模块
**功能描述**：内容数据的存储与管理

**详细功能**：
- 数据模型
  - 内容基本信息（标题、正文、发布时间、作者等）
  - 元数据（平台、URL、互动数据等）
  - 媒体文件（图片、视频 URL）
  - 关联数据（评论、转发等）
  
- 数据去重
  - 基于内容哈希去重
  - 基于 URL 去重
  - 相似内容检测
  
- 数据更新
  - 内容编辑追踪
  - 版本管理
  - 更新通知

#### 2.1.5 内容归档模块
**功能描述**：内容的分类、标签和归档

**详细功能**：
- 自动分类
  - 基于平台分类
  - 基于作者分类
  - 基于关键词分类
  - AI 智能分类（可选）
  
- 标签系统
  - 自动标签生成（关键词提取）
  - 手动标签管理
  - 标签统计
  
- 归档规则
  - 自定义归档规则
  - 规则模板
  - 批量归档操作

#### 2.1.6 阅读界面模块
**功能描述**：内容展示与阅读体验

**详细功能**：
- 内容展示
  - 统一的内容卡片设计
  - 多视图模式（列表/卡片/时间线）
  - 内容预览
  - 全文展开/收起
  
- 过滤与搜索
  - 多条件过滤（平台、作者、时间、标签）
  - 全文搜索（Elasticsearch）
  - 高级搜索（正则表达式）
  - 搜索历史
  
- 排序功能
  - 按时间排序（最新/最旧）
  - 按互动数排序
  - 按相关性排序
  - 自定义排序规则
  
- 阅读功能
  - 阅读进度追踪
  - 已读/未读标记
  - 收藏功能
  - 笔记功能（添加个人备注）
  - 分享功能

#### 2.1.7 通知提醒模块
**功能描述**：新内容通知与提醒

**详细功能**：
- 通知方式
  - 邮件通知
  - 桌面通知（浏览器）
  - Webhook 推送
  - 移动端推送（未来支持）
  
- 通知规则
  - 实时通知
  - 定时摘要（每日/每周）
  - 关键词触发通知
  - 重要作者优先通知
  
- 通知管理
  - 通知偏好设置
  - 免打扰时段
  - 通知历史

### 2.2 高级功能模块

#### 2.2.1 智能分析模块
**功能描述**：基于 AI 的内容分析与洞察

**详细功能**：
- 内容摘要
  - 自动生成内容摘要
  - 关键信息提取
  
- 情感分析
  - 内容情感倾向分析
  - 主题情感趋势
  
- 话题分析
  - 热门话题识别
  - 话题演化追踪
  
- 推荐系统
  - 相似内容推荐
  - 相关作者推荐
  - 个性化推荐

#### 2.2.2 数据分析模块
**功能描述**：数据统计与可视化

**详细功能**：
- 发布统计
  - 发布频率统计
  - 发布时间分布
  - 平台分布统计
  
- 内容分析
  - 内容类型分布
  - 内容长度分析
  - 互动数据分析
  
- 可视化
  - 时间线图表
  - 词云图
  - 趋势图表
  - 热力图

#### 2.2.3 导出功能模块
**功能描述**：数据导出与备份

**详细功能**：
- 导出格式
  - JSON（完整数据）
  - Markdown（便于阅读）
  - PDF（归档文档）
  - CSV（数据分析）
  - HTML（网页格式）
  
- 导出选项
  - 按时间范围导出
  - 按作者导出
  - 按标签导出
  - 自定义字段选择
  
- 备份功能
  - 自动备份（定时）
  - 增量备份
  - 备份恢复

---

## 3. 技术架构设计

### 3.1 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端界面层                            │
│  (React/Vue + TypeScript)                               │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                    API 网关层                            │
│  (RESTful API + GraphQL)                                │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   业务逻辑层                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 平台管理 │  │ 内容拉取 │  │ 内容归档 │            │
│  └──────────┘  └──────────┘  └──────────┘            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 用户管理 │  │ 通知服务 │  │ 分析服务 │            │
│  └──────────┘  └──────────┘  └──────────┘            │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   数据存储层                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │PostgreSQL│  │  Redis   │  │Elasticsearch│         │
│  │ (主数据) │  │ (缓存)   │  │ (搜索)   │            │
│  └──────────┘  └──────────┘  └──────────┘            │
└─────────────────────────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                   任务调度层                             │
│  (Quartz / XXL-Job + Redis)                             │
└─────────────────────────────────────────────────────────┘
```

### 3.2 技术栈选型

#### 3.2.1 后端技术栈
- **语言**：Java 17+ / Java 21 (LTS)
- **Web 框架**：Spring Boot 3.x
- **任务调度**：Quartz / XXL-Job
- **消息队列**：RabbitMQ / Apache Kafka（可选，用于异步处理）
- **数据库**：
  - PostgreSQL（主数据库）
  - Redis（缓存、分布式锁）
  - Elasticsearch（全文搜索）
- **ORM**：Spring Data JPA (Hibernate) / MyBatis Plus
- **认证**：Spring Security + JWT Token
- **API 文档**：SpringDoc OpenAPI (Swagger)
- **构建工具**：Maven / Gradle
- **应用服务器**：内嵌 Tomcat（Spring Boot 默认）

#### 3.2.2 前端技术栈
- **框架**：React 18+ / Vue 3+
- **语言**：TypeScript
- **UI 库**：Ant Design / Material-UI / Tailwind CSS
- **状态管理**：Redux / Zustand / Pinia
- **路由**：React Router / Vue Router
- **HTTP 客户端**：Axios
- **构建工具**：Vite / Webpack

#### 3.2.3 基础设施
- **容器化**：Docker + Docker Compose
- **反向代理**：Nginx
- **监控**：Prometheus + Grafana
- **日志**：ELK Stack (Elasticsearch + Logstash + Kibana)
- **CI/CD**：GitHub Actions / GitLab CI

### 3.3 数据模型设计

#### 3.3.1 核心数据表

**平台表 (Platform)**
```sql
- id: UUID (主键)
- name: String (平台名称)
- type: String (平台类型)
- api_base_url: String (API 基础 URL)
- auth_type: String (认证类型: api_key/oauth)
- config: JSON (平台配置)
- status: Enum (状态: active/inactive)
- created_at: DateTime
- updated_at: DateTime
```

**用户表 (TrackedUser)**
```sql
- id: UUID (主键)
- platform_id: UUID (外键 -> Platform)
- username: String (用户名)
- user_id: String (平台用户 ID)
- display_name: String (显示名称)
- avatar_url: String (头像 URL)
- group_id: UUID (外键 -> UserGroup, 可选)
- tags: Array[String] (标签)
- priority: Integer (优先级)
- is_active: Boolean (是否启用)
- last_fetched_at: DateTime (最后拉取时间)
- created_at: DateTime
- updated_at: DateTime
```

**内容表 (Content)**
```sql
- id: UUID (主键)
- platform_id: UUID (外键 -> Platform)
- user_id: UUID (外键 -> TrackedUser)
- content_id: String (平台内容 ID)
- title: String (标题)
- body: Text (正文)
- url: String (原始 URL)
- content_type: Enum (类型: text/image/video/link)
- media_urls: Array[String] (媒体文件 URL)
- published_at: DateTime (发布时间)
- metadata: JSON (元数据: 点赞数、转发数等)
- hash: String (内容哈希，用于去重)
- is_read: Boolean (是否已读)
- is_favorite: Boolean (是否收藏)
- tags: Array[String] (标签)
- notes: Text (个人备注)
- created_at: DateTime
- updated_at: DateTime
```

**标签表 (Tag)**
```sql
- id: UUID (主键)
- name: String (标签名称)
- color: String (标签颜色)
- category: String (分类)
- usage_count: Integer (使用次数)
- created_at: DateTime
```

**通知规则表 (NotificationRule)**
```sql
- id: UUID (主键)
- user_id: UUID (外键 -> User, 系统用户)
- name: String (规则名称)
- trigger_type: Enum (触发类型)
- conditions: JSON (触发条件)
- notification_method: Array[String] (通知方式)
- is_active: Boolean
- created_at: DateTime
```

**定时任务配置表 (ScheduleConfig)**
```sql
- id: UUID (主键)
- is_global_enabled: Boolean (全局定时任务是否启用)
- default_cron: String (默认 Cron 表达式)
- created_at: DateTime
- updated_at: DateTime
```

**用户定时任务表 (UserSchedule)**
```sql
- id: UUID (主键)
- user_id: UUID (外键 -> TrackedUser)
- cron_expression: String (Cron 表达式)
- is_enabled: Boolean (是否启用)
- last_run_at: DateTime (最后执行时间)
- next_run_at: DateTime (下次执行时间)
- created_at: DateTime
- updated_at: DateTime
```

**刷新任务表 (FetchTask)**
```sql
- id: UUID (主键)
- user_id: UUID (外键 -> TrackedUser)
- task_type: Enum (任务类型: manual/scheduled)
- start_time: DateTime (起始时间，用于拉取指定时间范围)
- end_time: DateTime (结束时间，通常为当前时间)
- status: Enum (状态: pending/running/completed/failed/cancelled)
- progress: Integer (进度百分比 0-100)
- fetched_count: Integer (已拉取数量)
- total_count: Integer (总数量，可能为 null)
- error_message: Text (错误信息)
- started_at: DateTime (开始时间)
- completed_at: DateTime (完成时间)
- created_at: DateTime
```

**定时任务执行历史表 (ScheduleExecutionHistory)**
```sql
- id: UUID (主键)
- schedule_id: UUID (外键 -> UserSchedule, 可为空表示全局任务)
- execution_time: DateTime (执行时间)
- status: Enum (状态: success/failed)
- fetched_count: Integer (拉取的内容数量)
- duration: Integer (执行耗时，毫秒)
- error_message: Text (错误信息)
- created_at: DateTime
```

#### 3.3.2 关系设计
- Platform 1:N TrackedUser
- TrackedUser 1:N Content
- TrackedUser 1:1 UserSchedule (可选，每个用户可有一个定时任务配置)
- TrackedUser 1:N FetchTask (一个用户可以有多个刷新任务)
- Content N:M Tag (多对多)
- User 1:N NotificationRule
- UserSchedule 1:N ScheduleExecutionHistory

### 3.4 API 设计

#### 3.4.1 RESTful API 规范

**基础 URL**：`/api/v1`

**认证**：Bearer Token (JWT)

#### 3.4.2 主要 API 端点

**平台管理**
```
GET    /platforms              # 获取平台列表
POST   /platforms              # 创建平台配置
GET    /platforms/{id}         # 获取平台详情
PUT    /platforms/{id}         # 更新平台配置
DELETE /platforms/{id}         # 删除平台配置
POST   /platforms/{id}/test    # 测试平台连接
```

**用户管理**
```
GET    /users                  # 获取追踪用户列表
POST   /users                  # 添加追踪用户
GET    /users/{id}             # 获取用户详情
PUT    /users/{id}             # 更新用户信息
DELETE /users/{id}             # 删除追踪用户
POST   /users/{id}/fetch       # 手动触发拉取
                                  # 请求体可选：{"start_time": "2026-01-01T00:00:00Z"}
                                  # 不传 start_time 则默认从最后拉取时间至今
POST   /users/batch-fetch      # 批量刷新用户（请求体：{"user_ids": [...], "start_time": "..."}）
GET    /users/{id}/stats       # 获取用户统计
GET    /users/{id}/fetch-history # 获取用户刷新历史
```

**内容管理**
```
GET    /contents               # 获取内容列表（支持过滤、搜索、分页）
GET    /contents/{id}          # 获取内容详情
PUT    /contents/{id}          # 更新内容（标记已读、收藏等）
DELETE /contents/{id}          # 删除内容
POST   /contents/{id}/notes    # 添加备注
GET    /contents/stats         # 获取内容统计
```

**标签管理**
```
GET    /tags                   # 获取标签列表
POST   /tags                   # 创建标签
PUT    /tags/{id}              # 更新标签
DELETE /tags/{id}              # 删除标签
POST   /contents/{id}/tags     # 为内容添加标签
```

**任务管理**
```
GET    /tasks                  # 获取任务列表
GET    /tasks/{id}             # 获取任务详情
POST   /tasks/fetch-all        # 触发全量拉取
POST   /tasks/fetch-incremental # 触发增量拉取
GET    /tasks/schedule/status  # 获取定时任务状态
PUT    /tasks/schedule/enable  # 启用全局定时任务
PUT    /tasks/schedule/disable # 禁用全局定时任务
PUT    /tasks/schedule/users/{id}/enable  # 启用指定用户的定时任务
PUT    /tasks/schedule/users/{id}/disable # 禁用指定用户的定时任务
GET    /tasks/schedule/history # 获取定时任务执行历史
GET    /tasks/fetch/queue      # 获取刷新任务队列
GET    /tasks/fetch/{task_id} # 获取刷新任务详情
DELETE /tasks/fetch/{task_id} # 取消刷新任务
```

**通知管理**
```
GET    /notifications          # 获取通知列表
GET    /notifications/{id}     # 获取通知详情
PUT    /notifications/{id}/read # 标记已读
GET    /notification-rules     # 获取通知规则
POST   /notification-rules     # 创建通知规则
```

**导出功能**
```
POST   /export                 # 导出数据
GET    /export/{job_id}        # 查询导出任务状态
GET    /export/{job_id}/download # 下载导出文件
```

### 3.5 数据流设计

#### 3.5.1 内容拉取流程

**定时任务拉取流程**：
```
1. 定时任务调度器触发（检查全局开关和用户开关是否启用）
2. 获取所有启用的追踪用户（且该用户的定时任务已启用）
3. 遍历用户，调用对应平台的 API 适配器
4. 获取新内容数据（从用户最后拉取时间至今）
5. 数据清洗与验证
6. 去重检查（基于 hash）
7. 保存到数据库
8. 触发通知（如果满足规则）
9. 更新用户最后拉取时间
10. 记录任务执行历史
```

**手动刷新流程**：
```
1. 用户点击刷新按钮（单用户或批量用户）
2. 如果是自定义时间范围，用户选择起始时间
3. 创建刷新任务记录（状态：pending）
4. 异步执行刷新任务（状态：running）
5. 根据时间范围获取内容数据
   - 默认模式：从该用户最后拉取的文章时间点至今
   - 自定义模式：从用户指定的起始时间至今
6. 数据清洗与验证
7. 去重检查（基于 hash）
8. 保存到数据库
9. 更新任务进度（实时反馈给前端）
10. 更新用户最后拉取时间
11. 标记任务完成（状态：completed）
12. 触发通知（如果满足规则）
13. 记录刷新历史
```

#### 3.5.2 内容展示流程
```
1. 用户请求内容列表
2. API 接收请求，解析过滤条件
3. 查询数据库（PostgreSQL）
4. 如果涉及搜索，查询 Elasticsearch
5. 数据聚合与格式化
6. 返回 JSON 响应
7. 前端渲染内容卡片
```

---

## 4. UI/UX 设计

### 4.1 页面结构

#### 4.1.1 主要页面
1. **仪表盘 (Dashboard)**
   - 内容统计概览
   - 最近更新内容
   - 定时任务状态显示（全局开关状态、最近执行时间）
   - 快速操作入口
     - 全局定时任务开关
     - 快速刷新所有用户

2. **内容列表页 (Content List)**
   - 内容卡片展示
   - 过滤侧边栏
   - 搜索栏
   - 分页/无限滚动

3. **内容详情页 (Content Detail)**
   - 完整内容展示
   - 元数据信息
   - 操作按钮（收藏、备注、分享）
   - 相关内容推荐

4. **用户管理页 (User Management)**
   - 用户列表
   - 添加用户表单
   - 用户分组管理
   - 定时任务开关（全局开关 + 单用户开关）
   - 刷新功能
     - 每个用户卡片上的"刷新"按钮
     - 刷新时间选择器（默认：从最后拉取时间；自定义：选择起始时间）
     - 刷新进度显示（进度条、状态提示）
     - 批量刷新操作（多选用户后批量刷新）

5. **平台配置页 (Platform Settings)**
   - 平台列表
   - 平台配置表单
   - 认证管理

6. **设置页 (Settings)**
   - 通知设置
   - 拉取频率设置
   - 定时任务管理
     - 全局定时任务开关
     - 定时任务执行历史
     - 定时任务日志查看
   - 导出配置
   - 账户设置

### 4.2 设计原则
- **简洁清晰**：界面简洁，信息层次分明
- **响应式设计**：支持桌面端、平板、移动端
- **暗色模式**：支持明暗主题切换
- **无障碍设计**：遵循 WCAG 标准
- **性能优化**：懒加载、虚拟滚动、缓存策略

### 4.3 交互设计
- **快捷键支持**：常用操作支持键盘快捷键
- **拖拽排序**：支持拖拽调整内容顺序
- **批量操作**：支持批量标记、删除、归档、刷新
- **实时更新**：WebSocket 支持实时内容更新和刷新进度
- **离线支持**：Service Worker 支持离线阅读
- **刷新交互**：
  - 点击刷新按钮后显示进度提示
  - 支持取消进行中的刷新任务
  - 刷新完成后显示结果统计（成功/失败数量）
  - 刷新历史记录可查看

---

## 5. 安全设计

### 5.1 认证与授权
- JWT Token 认证
- Token 刷新机制
- 角色权限控制（RBAC）
- API 访问频率限制

### 5.2 数据安全
- API Key/Token 加密存储
- 敏感数据脱敏
- HTTPS 传输
- SQL 注入防护
- XSS 防护

### 5.3 隐私保护
- 用户数据隔离
- 数据访问日志
- 数据删除机制（GDPR 合规）

---

## 6. 性能优化

### 6.1 后端优化
- 数据库索引优化
- 查询缓存（Redis）
- 异步任务处理
- 数据库连接池
- API 响应压缩

### 6.2 前端优化
- 代码分割（Code Splitting）
- 图片懒加载
- 虚拟滚动（长列表）
- 服务端渲染（SSR，可选）
- CDN 加速

### 6.3 存储优化
- 媒体文件 CDN 存储
- 数据库分表（按时间）
- 数据归档策略（冷热数据分离）

---

## 7. 监控与运维

### 7.1 日志系统
- 结构化日志（JSON 格式）
- 日志级别管理
- 日志聚合与分析
- 错误追踪（Sentry）

### 7.2 监控指标
- 系统指标（CPU、内存、磁盘）
- 应用指标（API 响应时间、错误率）
- 业务指标（拉取成功率、内容数量）
- 告警规则配置

### 7.3 部署方案
- Docker 容器化部署
- Kubernetes 编排（可选）
- 自动化部署流程
- 回滚机制

---

## 8. 开发计划

### 8.1 MVP 版本（第一阶段）
**目标**：实现核心功能，验证产品可行性

**功能范围**：
- 支持 2-3 个主流平台（如 Twitter、GitHub）
- 基础的用户管理
- 定时拉取功能
- 简单的内容展示
- 基础搜索功能

**时间估算**：4-6 周

### 8.2 Beta 版本（第二阶段）
**目标**：完善功能，提升用户体验

**功能范围**：
- 支持 5-8 个平台
- 完整的用户管理（分组、标签）
- 内容归档与分类
- 通知功能
- 数据导出
- 基础数据分析

**时间估算**：6-8 周

### 8.3 正式版本（第三阶段）
**目标**：产品化，支持生产环境

**功能范围**：
- 支持 10+ 平台
- AI 智能分析
- 高级数据分析
- 完整的监控与运维
- 性能优化
- 文档完善

**时间估算**：8-10 周

---

## 9. 风险评估

### 9.1 技术风险
- **API 变更风险**：平台 API 可能变更，需要适配器模式
- **速率限制**：平台 API 有调用限制，需要合理控制
- **数据量增长**：长期运行数据量大，需要存储优化

### 9.2 业务风险
- **平台政策风险**：平台可能限制第三方数据抓取
- **合规风险**：需要遵守各平台的使用条款
- **数据准确性**：确保数据拉取的完整性和准确性

### 9.3 应对策略
- 建立平台适配器抽象层，便于快速适配
- 实现完善的错误处理和重试机制
- 定期备份数据
- 遵守平台 API 使用规范
- 提供数据验证机制

---

## 10. 未来规划

### 10.1 功能扩展
- 移动端 App（iOS/Android）
- 浏览器插件（一键收藏）
- 社交功能（内容分享、评论）
- 协作功能（团队共享）

### 10.2 技术升级
- 机器学习推荐算法
- 自然语言处理（NLP）
- 图像识别与分析
- 区块链存储（去中心化）

### 10.3 商业化方向
- 企业版功能（多用户、权限管理）
- API 服务（提供数据接口）
- 数据分析报告（付费功能）

---

## 11. 附录

### 11.1 术语表
- **CAAT**：Content Aggregator & Archive Tool
- **增量拉取**：只拉取上次拉取后的新内容
- **全量拉取**：拉取所有历史内容
- **内容哈希**：用于内容去重的唯一标识

### 11.2 参考资源
- RESTful API 设计规范
- OAuth 2.0 认证流程
- 各平台 API 文档

### 11.3 更新日志
- 2026-01-24：初始版本创建
- 2026-01-24：更新后端技术栈为 Java（Spring Boot）
- 2026-01-24：新增定时任务开关功能
- 2026-01-24：新增手动刷新作者文章功能（支持默认模式和自定义时间范围）

---

**文档版本**：v1.0  
**最后更新**：2026-01-24  
**维护者**：开发团队
