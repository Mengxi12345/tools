#!/bin/bash
# 回滚脚本：将服务回滚到上一版本
# 使用方式：
#   1) 基于 Git 标签回滚：./scripts/rollback.sh <git-tag>
#   2) 仅重启当前镜像（无新构建）：./scripts/rollback.sh --restart

set -e

echo "=== 内容聚合工具回滚脚本 ==="

if [ "$1" = "--restart" ]; then
  echo "仅重启当前容器..."
  docker compose down
  docker compose up -d
  echo "重启完成。"
  exit 0
fi

if [ -n "$1" ]; then
  TAG="$1"
  echo "回滚到 Git 标签: $TAG"
  if ! git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "错误: 标签 $TAG 不存在"
    exit 1
  fi
  git checkout "$TAG"
  echo "已切换到 $TAG，正在重新部署..."
  ./scripts/deploy.sh
  echo "回滚完成。如需回到最新代码，请执行: git checkout main"
  exit 0
fi

# 无参数时输出使用说明
echo "用法："
echo "  ./scripts/rollback.sh <git-tag>   # 回滚到指定标签并重新部署"
echo "  ./scripts/rollback.sh --restart  # 仅重启当前容器"
echo ""
echo "建议：每次部署前打标签以便回滚，例如："
echo "  git tag deploy-$(date +%Y%m%d-%H%M) && ./scripts/deploy.sh"
exit 1
