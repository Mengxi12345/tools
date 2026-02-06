#!/bin/bash

# 阿里云镜像构建与推送脚本（一键构建并推送到阿里云 ACR）
# 用法: ./scripts/build-images-aliyun.sh [version] [username] [--vpc]
# 示例: ./scripts/build-images-aliyun.sh 1.0.0 梦溪mengxi
# 示例: ./scripts/build-images-aliyun.sh 1.0.0 梦溪mengxi --vpc  # 使用 VPC 内网地址（更快、不耗公网流量）

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 解析参数（--vpc 可放在任意位置）
USE_VPC=false
ARGS=()
for arg in "$@"; do
  if [ "$arg" = "--vpc" ]; then
    USE_VPC=true
  else
    ARGS+=("$arg")
  fi
done
VERSION="${ARGS[0]:-1.0.0}"
ALIYUN_USERNAME="${ARGS[1]:-梦溪mengxi}"

# 阿里云镜像仓库配置
ALIYUN_REGISTRY_PUBLIC="crpi-anqb8q8dr9wohaz8.cn-beijing.personal.cr.aliyuncs.com"
ALIYUN_REGISTRY_VPC="crpi-anqb8q8dr9wohaz8-vpc.cn-beijing.personal.cr.aliyuncs.com"
ALIYUN_NAMESPACE="mengxi666"
ALIYUN_REPO="mengxi666"

ALIYUN_REGISTRY=$ALIYUN_REGISTRY_PUBLIC
[ "$USE_VPC" = true ] && ALIYUN_REGISTRY=$ALIYUN_REGISTRY_VPC

# 获取项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  阿里云镜像构建与推送${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "版本: ${YELLOW}${VERSION}${NC}"
echo -e "用户名: ${YELLOW}${ALIYUN_USERNAME}${NC}"
echo -e "Registry: ${YELLOW}${ALIYUN_REGISTRY}${NC}"
[ "$USE_VPC" = true ] && echo -e "模式: ${GREEN}VPC 内网${NC}"
echo ""

# 1. 构建本地镜像
echo -e "${GREEN}[1/3] 构建镜像...${NC}"
"$SCRIPT_DIR/build-images.sh" "$VERSION"

# 2. 登录阿里云
echo -e "\n${GREEN}[2/3] 登录阿里云镜像仓库...${NC}"
if ! docker info 2>/dev/null | grep -q "Username"; then
    echo -e "${YELLOW}请登录阿里云镜像仓库（密码为开通服务时设置的凭证密码）:${NC}"
    docker login --username="${ALIYUN_USERNAME}" "${ALIYUN_REGISTRY}"
else
    echo -e "${GREEN}已登录，跳过${NC}"
fi

# 3. 标记并推送
echo -e "\n${GREEN}[3/3] 标记并推送到阿里云...${NC}"
source "$PROJECT_ROOT/.docker-images.txt"

ALIYUN_BACKEND_IMAGE="${ALIYUN_REGISTRY}/${ALIYUN_NAMESPACE}/${ALIYUN_REPO}-backend:${VERSION}"
ALIYUN_FRONTEND_IMAGE="${ALIYUN_REGISTRY}/${ALIYUN_NAMESPACE}/${ALIYUN_REPO}-frontend:${VERSION}"

echo -e "  后端: ${BACKEND_IMAGE} -> ${ALIYUN_BACKEND_IMAGE}"
docker tag "${BACKEND_IMAGE}" "${ALIYUN_BACKEND_IMAGE}"
docker push "${ALIYUN_BACKEND_IMAGE}"
echo -e "${GREEN}✓ 后端镜像推送完成${NC}"

echo -e "  前端: ${FRONTEND_IMAGE} -> ${ALIYUN_FRONTEND_IMAGE}"
docker tag "${FRONTEND_IMAGE}" "${ALIYUN_FRONTEND_IMAGE}"
docker push "${ALIYUN_FRONTEND_IMAGE}"
echo -e "${GREEN}✓ 前端镜像推送完成${NC}"

# 保存阿里云镜像信息（VPC 模式下保存 VPC 地址，便于同 VPC 的 ECS 拉取）
cat > "$PROJECT_ROOT/.docker-images-aliyun.txt" <<EOF
ALIYUN_BACKEND_IMAGE=${ALIYUN_BACKEND_IMAGE}
ALIYUN_FRONTEND_IMAGE=${ALIYUN_FRONTEND_IMAGE}
VERSION=${VERSION}
ALIYUN_REGISTRY=${ALIYUN_REGISTRY}
ALIYUN_NAMESPACE=${ALIYUN_NAMESPACE}
EOF

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "后端: ${YELLOW}${ALIYUN_BACKEND_IMAGE}${NC}"
echo -e "前端: ${YELLOW}${ALIYUN_FRONTEND_IMAGE}${NC}"
echo -e "\n在 ECS 上部署: ${BLUE}./scripts/deploy-aliyun.sh ${VERSION}${NC}"
echo -e "镜像信息已保存到 .docker-images-aliyun.txt"
