#!/bin/bash

# 镜像构建脚本
# 用法: ./scripts/build-images.sh [version] [registry]
# 示例: ./scripts/build-images.sh 1.0.0 docker.io/your-registry

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
VERSION=${1:-latest}
REGISTRY=${2:-""}
PROJECT_NAME="content-aggregator"

# 获取项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${GREEN}开始构建镜像...${NC}"
echo -e "版本: ${YELLOW}${VERSION}${NC}"
echo -e "镜像仓库: ${YELLOW}${REGISTRY:-默认（本地）}${NC}"

# 构建后端镜像
echo -e "\n${GREEN}[1/2] 构建后端镜像...${NC}"
cd "$PROJECT_ROOT/backend"

if [ -n "$REGISTRY" ]; then
    BACKEND_IMAGE="${REGISTRY}/${PROJECT_NAME}-backend:${VERSION}"
    BACKEND_IMAGE_LATEST="${REGISTRY}/${PROJECT_NAME}-backend:latest"
else
    BACKEND_IMAGE="${PROJECT_NAME}-backend:${VERSION}"
    BACKEND_IMAGE_LATEST="${PROJECT_NAME}-backend:latest"
fi

docker build -t "$BACKEND_IMAGE" -t "$BACKEND_IMAGE_LATEST" .
echo -e "${GREEN}✓ 后端镜像构建完成: ${BACKEND_IMAGE}${NC}"

# 构建前端镜像
echo -e "\n${GREEN}[2/2] 构建前端镜像...${NC}"
cd "$PROJECT_ROOT/frontend"

if [ -n "$REGISTRY" ]; then
    FRONTEND_IMAGE="${REGISTRY}/${PROJECT_NAME}-frontend:${VERSION}"
    FRONTEND_IMAGE_LATEST="${REGISTRY}/${PROJECT_NAME}-frontend:latest"
else
    FRONTEND_IMAGE="${PROJECT_NAME}-frontend:${VERSION}"
    FRONTEND_IMAGE_LATEST="${PROJECT_NAME}-frontend:latest"
fi

docker build -t "$FRONTEND_IMAGE" -t "$FRONTEND_IMAGE_LATEST" .
echo -e "${GREEN}✓ 前端镜像构建完成: ${FRONTEND_IMAGE}${NC}"

# 输出镜像信息
echo -e "\n${GREEN}构建完成！镜像列表:${NC}"
echo -e "  后端: ${YELLOW}${BACKEND_IMAGE}${NC}"
echo -e "  前端: ${YELLOW}${FRONTEND_IMAGE}${NC}"

# 保存镜像信息到文件（用于后续推送）
cat > "$PROJECT_ROOT/.docker-images.txt" <<EOF
BACKEND_IMAGE=${BACKEND_IMAGE}
BACKEND_IMAGE_LATEST=${BACKEND_IMAGE_LATEST}
FRONTEND_IMAGE=${FRONTEND_IMAGE}
FRONTEND_IMAGE_LATEST=${FRONTEND_IMAGE_LATEST}
VERSION=${VERSION}
REGISTRY=${REGISTRY}
EOF

echo -e "\n${GREEN}镜像信息已保存到 .docker-images.txt${NC}"
