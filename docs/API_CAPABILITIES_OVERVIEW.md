# 对外能力与业务逻辑总览

本文档从「能力维度」而非「单个接口」角度，概述 CAAT 当前对外提供的所有 API 能力以及背后的核心业务逻辑。  
具体路径、参数与示例请参考 `docs/API_DOCUMENTATION.md` 和在线 Swagger 文档。

## 1. 认证与安全（Auth & Security）

- **能力**：
  - 用户注册、登录，获取 JWT Token。
  - 基于 RBAC 的角色与权限控制。
  - 基于请求频率的简单限流与安全审计。
- **核心接口**（前缀均为 `/api/v1`，下同）：
  - `POST /auth/register`：注册新用户。
  - `POST /auth/login`：登录，返回 JWT。
- **业务逻辑要点**：
  - 所有业务接口统一走 `ApiResponse<T>` 包装（`code/message/data/timestamp`）。
  - 登录成功后返回 `token/username/userId`，前端将 Token 持久化并放入 `Authorization: Bearer <token>` 请求头。
  - 角色、权限数据存储在 `sys_users/roles/role_permissions/user_roles` 表，权限校验在 Service 层通过 `PermissionService` 完成。

## 2. 平台管理（Platforms）

- **能力**：
  - 管理第三方平台配置（GitHub、知乎、掘金、CSDN、Medium、Reddit、知识星球、TimeStore、微博、Twitter 等）。
  - 测试平台连接可用性，校验 Token、Cookie 等配置是否有效。
- **核心接口**：
  - `GET /platforms`：分页查询平台列表。
  - `POST /platforms`：创建平台配置。
  - `PUT /platforms/{id}`：更新平台配置。
  - `DELETE /platforms/{id}`：删除平台。
  - `POST /platforms/{id}/test`：测试平台连接。
- **业务逻辑要点**：
  - 平台配置存储于 `platforms` 表，`config` 字段使用 JSONB 存储不同平台的专有配置。
  - 调用第三方平台时统一通过 `AdapterFactory` 和各类 `*Adapter` 实现，避免在 Controller 中写死第三方细节。
  - TimeStore 相关逻辑全部封装在 `TimeStoreAdapter` 与对应 Service 中，支持加密文章修复、图片修复。

## 3. 用户与分组管理（Users & Groups）

- **能力**：
  - 管理被追踪的作者（跨多个平台）。
  - 支持标签、优先级、分组等维度管理。
- **核心接口**：
  - `GET /users`、`POST /users`、`PUT /users/{id}`、`DELETE /users/{id}`：用户 CRUD。
  - `PUT /users/{id}/toggle`：启用 / 禁用某个用户的拉取任务。
  - `GET /users/{id}/stats`：获取单个用户的内容统计。
  - `GET /groups`、`POST /groups` 等：用户分组管理。
- **业务逻辑要点**：
  - 用户与平台通过 `tracked_users` 表关联，支持同一平台多用户及不同平台同名用户。
  - 用户标签与优先级存储在 `tracked_users`、`tracked_user_tags` 表，用于筛选和优先拉取。
  - 分组信息存储在 `user_groups` 表，用于在前端 `/groups` 页面按组管理。

## 4. 内容拉取与任务调度（Content Fetch & Scheduling）

- **能力**：
  - 手动拉取指定用户或用户组的内容。
  - 基于 Quartz 的定时自动拉取（例如每 10 分钟一次）。
  - 查询拉取任务历史与状态。
- **核心接口**：
  - `POST /users/{id}/fetch`：手动拉取某个用户的内容（支持时间范围）。
  - `GET /tasks/schedule/status` / `PUT /tasks/schedule/enable|disable`：全局定时任务开关。
  - `PUT /tasks/schedule/users/{id}/enable|disable`：单用户定时任务开关。
  - `GET /tasks/schedule/history`：定时任务执行历史。
- **业务逻辑要点**：
  - 拉取任务记录在 `fetch_tasks` 表，标记来源（手动 / 定时）、时间范围、执行结果和错误信息。
  - 定时配置存储在 `schedule_configs`、`user_schedules` 表，Quartz 表 `QRTZ_*` 存储调度器内部状态。
  - 实际内容拉取由 `ContentFetchService` 协调：根据用户所属平台选择对应 `*Adapter` 拉取、去重、入库。

## 5. 内容管理与搜索（Contents & Search）

- **能力**：
  - 按平台、用户、标签、关键词等多维度浏览和筛选内容。
  - 支持数据库搜索与 Elasticsearch 全文检索、高级搜索、正则搜索。
- **核心接口**：
  - `GET /contents`：分页内容列表（多维过滤）。
  - `GET /contents/{id}`：内容详情。
  - `PUT /contents/{id}`：更新内容状态（标记已读、收藏等）。
  - `GET /contents/categories/stats`：分类统计。
  - `GET /contents/search` / `/search/regex` / `/search/advanced`：搜索接口族。
  - `GET /contents/search/history` / `/search/popular`：搜索历史与热门搜索。
- **业务逻辑要点**：
  - 主数据存储在 `contents` 表，媒体链接在 `content_media_urls`，标签关联在 `content_tags`。
  - Elasticsearch 启用时，`ContentFetchService` 会在入库后同步索引文档，搜不到或搜索失败时回退到数据库搜索。
  - 搜索历史与热门搜索分析存储在 `search_history` 表，用于前端搜索建议。

## 6. 标签与归档（Tags & Archive）

- **能力**：
  - 标签管理、自动打标签、热门标签统计。
  - 按规则批量归档内容（例如按时间、作者、平台、关键词、标签等）。
- **核心接口**：
  - `GET /tags`、`POST /tags` 等：标签 CRUD。
  - `GET /stats/tags`：标签使用统计。
  - `POST /contents/{id}/generate-tags`：对单条内容进行自动标签生成。
  - 归档相关接口（`/archive-rules`、`/archive/*`）：管理归档规则与执行归档（具体路径见 API 文档）。
- **业务逻辑要点**：
  - 标签主数据存储在 `tags` 表，使用情况统计与内容、用户关联结合查询。
  - 归档规则存储在 `archive_rules` 表，`ArchiveService` 根据不同规则类型筛选内容并更新其归档状态或目标存储。

## 7. 通知与订阅（Notifications）

- **能力**：
  - 定义通知规则：基于内容属性触发通知（平台、作者、关键词、标签等）。
  - 多通道通知：QQ 群（go-cqhttp / Mirai）、飞书、邮件、Webhook、浏览器桌面通知。
  - 通知记录与已读管理、免打扰时段。
- **核心接口**：
  - `GET /notification-rules`、`POST /notification-rules` 等：通知规则管理。
  - `GET /notifications`：通知列表。
  - `POST /notifications/mark-read` / `/mark-all-read`：已读标记。
- **业务逻辑要点**：
  - 通知规则存储于 `notification_rules` 表，通道配置存储在 `notification_channel_configs`，通知历史存储于 `notifications`。
  - `NotificationService` 在内容保存或任务执行结束时，根据规则判断是否触发通知，并记录每一次发送结果。
  - 支持配置免打扰时段（`quietHours`），在该时间段内规则仍会匹配但不真正下发通知。

## 8. 导出与备份（Export & Backup）

- **能力**：
  - 按用户/时间范围导出内容为 JSON、Markdown、CSV、HTML、PDF、Word。
  - 异步导出任务，支持任务进度、日志、导出文件下载。
  - 数据库备份 / 增量备份 / 恢复能力（通过单独的 Backup API 与脚本）。
- **核心接口**：
  - `/export/*`：包括 JSON/Markdown/CSV/HTML/PDF/Word 导出接口以及任务列表查询（详见 API 文档）。
  - `GET /export/tasks`：查询导出任务列表与状态。
  - 备份相关接口：`/backup/database`、`/backup/incremental`、`/backup/list` 等（详见 `BACKUP_RESTORE_GUIDE.md`）。
- **业务逻辑要点**：
  - 导出任务元数据存储在 `export_tasks` 表，包括格式、范围、进度、日志、文件路径、文件大小等。
  - JSON/Markdown/CSV/HTML 导出由 `ExportService` 直接生成字节流；PDF/Word 导出由 `PdfWordExportService` 处理，并通过 `ExportTaskProgressUpdater` 在导出过程中实时更新进度与日志。
  - 导出文件保存在 `exports/` 目录下，对应路径记录在 `export_tasks.file_path` 中，便于下载与清理。

## 9. 数据统计与分析（Analytics & Stats）

- **能力**：
  - 平台分布、用户活跃度、内容类型分布、时间分布、增长趋势等统计。
  - 为前端 `/analytics` 页面与仪表盘提供数据源。
- **核心接口**：
  - `GET /stats/overview`：总体统计概览。
  - `GET /stats/platform-distribution`：内容按平台分布。
  - `GET /stats/users`：用户统计。
  - `GET /stats/tags`：标签统计。
  - `GET /stats/content-time-distribution`：内容时间分布。
  - `GET /stats/content-type-distribution`：内容类型分布。
  - `GET /stats/active-users-ranking`：活跃用户排行。
  - `GET /stats/content-growth-trend`：内容增长趋势。
- **业务逻辑要点**：
  - 统计逻辑主要在 `StatsService` 与 `ContentService` 中实现，基于 `contents`、`tracked_users`、`tags`、`search_history` 等表的聚合查询。
  - 前端 `Analytics.tsx` 页面通过这些接口绘制折线图、饼图、词云等。

## 10. AI 与推荐（AI & Recommendation）

- **能力**：
  - 内容摘要、情感分析、话题分析。
  - 相似内容推荐、相关作者推荐、简单个性化推荐。
- **核心接口**：
  - `/ai/*`：AI 相关接口（如摘要、情感分析、推荐等，具体路径见 API 文档和 Swagger）。
- **业务逻辑要点**：
  - 由 `AIService`、`TopicAnalysisService`、`RecommendationService` 等服务封装外部或本地 AI 能力。
  - 对于 AI 服务不可用的情况，提供简化的关键词匹配或规则回退逻辑，避免影响主流程。

## 11. 监控与健康检查（Monitoring & Health）

- **能力**：
  - 基于 Spring Boot Actuator 的健康检查与指标暴露。
  - Prometheus 指标采集，Grafana 可视化。
- **核心端点**（非 `/api/v1` 前缀）：
  - `/actuator/health`：健康检查。
  - `/actuator/prometheus`：Prometheus 指标。
- **业务逻辑要点**：
  - Actuator 端点和 Micrometer 配置在 `application.yml` 与 `monitoring/` 中。
  - `docker-compose.monitoring.yml` 提供 Prometheus/Grafana 一键启动能力。

---

> 如需查看完整接口定义（路径、请求体、响应体、错误码等），请优先查阅：
> - `docs/API_DOCUMENTATION.md`
> - 在线 Swagger UI：`/swagger-ui.html`

