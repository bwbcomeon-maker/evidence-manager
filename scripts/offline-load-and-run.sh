#!/usr/bin/env bash
set -euo pipefail

# 无参数时使用脚本所在目录（即离线包根目录）
PACKAGE_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"

if [[ ! -f "$PACKAGE_DIR/deploy/docker-compose.offline.yml" ]]; then
  echo "错误：未找到 $PACKAGE_DIR/deploy/docker-compose.offline.yml"
  echo "请把离线部署包解压后，再在包目录执行本脚本。"
  exit 1
fi

if [[ ! -f "$PACKAGE_DIR/deploy/.env" ]]; then
  echo "错误：未找到 $PACKAGE_DIR/deploy/.env"
  echo "请先复制 deploy/.env.example 为 deploy/.env 并填写密码。"
  exit 1
fi

echo "[1/5] 导入后端镜像"
docker load -i "$PACKAGE_DIR/images/evidence-manager-backend-offline.tar"

echo "[2/5] 导入前端镜像"
docker load -i "$PACKAGE_DIR/images/evidence-manager-frontend-offline.tar"

echo "[3/5] 导入数据库镜像"
docker load -i "$PACKAGE_DIR/images/postgres-16.tar"

echo "[4/5] 创建持久化目录"
set -a
source "$PACKAGE_DIR/deploy/.env"
set +a
mkdir -p "${HOST_POSTGRES_DATA:-/opt/evidence-manager/data/postgres}"
mkdir -p "${HOST_UPLOADS_DATA:-/opt/evidence-manager/data/uploads}"

echo "[5/5] 启动离线部署服务"
docker compose --env-file "$PACKAGE_DIR/deploy/.env" -f "$PACKAGE_DIR/deploy/docker-compose.offline.yml" up -d

echo
echo "启动完成。可执行以下命令检查状态："
echo "docker compose --env-file \"$PACKAGE_DIR/deploy/.env\" -f \"$PACKAGE_DIR/deploy/docker-compose.offline.yml\" ps"
