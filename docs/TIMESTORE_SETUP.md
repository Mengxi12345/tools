# TimeStore (timestore.vip) 接入说明

系统已按 **api.timestore.vip** 的真实接口接入：`/timeline/mymblog` + `mate-auth: Bearer <token>`，拉取「我的博客」文章并保存到本地。

## 1. 在系统里怎么填

### 添加平台（平台管理 → 添加平台）

| 表单项 | 填写内容 |
|--------|----------|
| **平台名称** | 任意，例如：`我的 TimeStore` |
| **平台类型** | 选择 **TimeStore (timestore.vip)** |
| **API 基础地址** | 填：`https://api.timestore.vip`（不要带末尾斜杠） |
| **配置（JSON）** | 填你的 mate-auth，**二选一**：<br>① 只填 JWT：`{"mateAuth": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx"}`<br>② 带 Bearer：`{"mateAuth": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx"}`<br>系统会自动补全 `Bearer `，两种写法都可以。 |

**从你的 curl 里取 token：**

- curl 里有一行：`-H 'mate-auth: Bearer eyJhbGci...'`
- 复制 **整段**（含 `Bearer `）或 **只复制 JWT**（`eyJhbGci...` 那串），放进配置里即可：
  - 整段：`{"mateAuth": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOi...B-3gN_E5AmrA8qIhmMnqnlJtAQFaRVuozU1VFrkWQGM"}`
  - 仅 JWT：`{"mateAuth": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOi...B-3gN_E5AmrA8qIhmMnqnlJtAQFaRVuozU1VFrkWQGM"}`

保存后可在列表中点击「测试连接」验证。

### 添加被跟踪用户（用户/关注管理 → 添加用户）

| 表单项 | 填写说明 |
|--------|----------|
| **平台** | 选刚创建的 TimeStore 平台 |
| **用户 ID** | 填要拉取的 **TimeStore 用户 uid**（如 `3906890`）。<br>• 接口支持：用 mate-auth token 鉴权，请求参数 `uid` 指定要拉取的用户时间线；<br>• 系统会用你填写的「用户 ID」作为 `uid` 请求接口，拉取该用户的文章；<br>• token 可与被拉取用户不同（例如用你的账号 token 拉取他人时间线）。 |

保存后对该用户执行「拉取」：

- **默认**：从最后拉取时间至今。
- **指定时间段**：在弹窗里选「指定时间段」，填开始时间、结束时间（结束不选则到当前），会拉取该时间段内的文章和图片并保存到本地。
- 请求会带上 `date`（开始日期 yyyy-MM-dd）、自动翻页直到没有更多页；文章正文、多图（逗号分隔的 img）、点赞/评论/浏览等元数据都会解析并保存。

## 2. 和你提供的 curl 的对应关系

| curl 内容 | 在系统里的对应 |
|-----------|----------------|
| `https://api.timestore.vip/timeline/mymblog?current=1&size=10&uid=3907902&id=0&screen=0&date=2024-01-01` | **API 基础地址**：`https://api.timestore.vip`；适配器会带上 `date`（按你选的开始时间 yyyy-MM-dd）、并自动翻页拉取该时间段内所有文章和图片 URL。 |
| `mate-auth: Bearer eyJhbGci...` | **配置（JSON）** 里的 `mateAuth`，填 `Bearer eyJ...` 或只填 `eyJ...`。 |

## 3. API 约定（当前实现）

- **拉取接口**：`GET .../timeline/mymblog?current=<页>&size=10&uid=<uid>&id=0&screen=0&date=yyyy-MM-dd`（指定开始时间时会带 `date`）
- **uid 来源**：使用配置的「用户 ID」作为请求参数 `uid`，拉取该用户的时间线；token 仅用于鉴权。若未配置用户 ID 则退化为 JWT 中的 `userId` 或 `0`。
- **认证**：请求头 `mate-auth: Bearer <token>`（未带 `Bearer ` 时系统会自动加上）
- **响应**：期望 JSON 中 `data.records` 为文章列表，每项含 `id`、`postContent`、`ctime`（支持 ISO 或时间戳）、`img`（单图或多图逗号分隔）、`nickName`、`userAvatar` 等；分页从 `data.pages`、`data.current`（可为字符串）解析，适配器会自动翻页拉取所有页。

## 4. 保存与展示约定

- **文章链接**：每条内容保存的「原文」地址为 TimeStore 官方详情页：  
  `https://web.timestore.vip/#/time/pages/timeDetail/index?timeid={id}`（`id` 为接口返回的记录 id），点击「打开原文」可在浏览器中打开。
- **图片**：`img` 字段（单图或多图逗号分隔）会解析到内容的「图片」列表，在内容详情页展示并可点击查看大图。
- **作者**：作者昵称 `nickName`、头像 `userAvatar` 会随完整文章 JSON 存入元数据，内容详情页会展示昵称与头像。
- **元数据**：内容元数据保存为**完整文章 JSON**（含 `id`、`postContent`、`ctime`、`img`、`nickName`、`userAvatar`、`liked`、`views` 等），便于持久化与前端展示。

## 5. 拉取没看到数据时怎么查

- **刷新进度弹窗**：任务结束后若「状态」为 **FAILED**，弹窗会显示「失败原因」；若为 **COMPLETED** 但「数量」为 0，会提示可能原因。
- **后端日志**：在 `backend/logs/application.log` 中搜索 `TimeStore`：
  - 出现 `TimeStore 拉取到 N 条记录` 表示接口返回正常、已解析到 N 条；
  - 出现 `TimeStore 响应无 records/list` 表示接口返回的 JSON 里没有 `data.records` 或 `data.list`，需对照实际 API 响应调整适配器；
  - 出现 `PKIX path building failed` 表示 SSL 证书问题，需确保后端已使用带「放宽 SSL」的 TimeStore 专用 RestTemplate 并重启。
- **确认配置**：平台配置里 `mateAuth` 与 curl 中一致；被跟踪用户的「用户 ID」填要拉取的用户 uid（如 curl 里 `uid=3906890` 就填 `3906890`），系统会用该 uid 请求接口。

## 6. 安全说明

- `mateAuth` 仅保存在平台配置的 JSON 中，请勿泄露。
- 建议仅在可信环境使用，并定期更换 Token。
