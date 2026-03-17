#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${1:-$ROOT_DIR/offline-package}"

BACKEND_IMAGE="evidence-manager-backend:offline"
FRONTEND_IMAGE="evidence-manager-frontend:offline"
DB_IMAGE="postgres:16"

echo "[1/7] 清理输出目录: $OUTPUT_DIR"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/images" "$OUTPUT_DIR/deploy"

echo "[2/7] 构建后端镜像: $BACKEND_IMAGE"
docker build -f "$ROOT_DIR/backend/app/Dockerfile" -t "$BACKEND_IMAGE" "$ROOT_DIR"

echo "[3/7] 构建前端镜像: $FRONTEND_IMAGE"
docker build -f "$ROOT_DIR/frontend/Dockerfile" -t "$FRONTEND_IMAGE" "$ROOT_DIR"

echo "[4/7] 拉取数据库镜像: $DB_IMAGE"
docker pull "$DB_IMAGE"

echo "[5/7] 导出镜像到 tar"
docker save -o "$OUTPUT_DIR/images/evidence-manager-backend-offline.tar" "$BACKEND_IMAGE"
docker save -o "$OUTPUT_DIR/images/evidence-manager-frontend-offline.tar" "$FRONTEND_IMAGE"
docker save -o "$OUTPUT_DIR/images/postgres-16.tar" "$DB_IMAGE"

echo "[6/7] 拷贝离线部署文件"
cp "$ROOT_DIR/deploy/docker-compose.offline.yml" "$OUTPUT_DIR/deploy/"
cp "$ROOT_DIR/deploy/.env.example" "$OUTPUT_DIR/deploy/.env"
cp "$ROOT_DIR/scripts/offline-load-and-run.sh" "$OUTPUT_DIR/"
cp "$ROOT_DIR/docs/deploy-kylin-docker-offline.md" "$OUTPUT_DIR/"
[[ -f "$ROOT_DIR/db/scripts/admin_recover.sql" ]] && cp "$ROOT_DIR/db/scripts/admin_recover.sql" "$OUTPUT_DIR/"

echo "[7/7] 生成最终压缩包"
(
  cd "$OUTPUT_DIR/.."
  PACKAGE_NAME="$(basename "$OUTPUT_DIR")"
  tar -czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME"
)

echo
echo "离线部署包已生成："
echo "目录: $OUTPUT_DIR"
echo "压缩包: ${OUTPUT_DIR}.tar.gz"
echo
echo "下一步：把该目录或 tar.gz 传到离线服务器。"
