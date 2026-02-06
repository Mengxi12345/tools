# 前端 Dockerfile - 多阶段构建
# 阶段1: 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

# 复制 package 文件并安装依赖
COPY package*.json ./
RUN npm ci --only=production=false

# 复制源代码并构建
COPY . .
RUN npm run build

# 阶段2: 运行阶段
FROM nginx:alpine

# 复制构建产物到 nginx
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]
