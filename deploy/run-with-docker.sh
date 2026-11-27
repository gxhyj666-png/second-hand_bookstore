#!/usr/bin/env bash
# ============================================================
# 同窗书 · 不依赖 docker compose 的部署脚本（纯 docker CLI）
#
# 适用：服务器上没有 `docker compose` 插件（华为云 EulerOS / openEuler 常见），
#       只想把三端 + MySQL 起起来。
#
# 用法（仓库根目录，需 root）：
#   bash deploy/run-with-docker.sh up      # 启动（首次会自动构建镜像）
#   bash deploy/run-with-docker.sh status  # 看状态
#   bash deploy/run-with-docker.sh logs    # 跟随后端日志
#   bash deploy/run-with-docker.sh down    # 停止并删除容器（数据卷保留）
#   bash deploy/run-with-docker.sh reset   # 连数据卷一起删（危险）
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NET=textbook-loop
DB_VOL=textbook-loop-mysql
PREFIX=tl

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m[fail] %s\033[0m\n' "$*" >&2; exit 1; }

need_docker() {
  command -v docker >/dev/null 2>&1 || die "未安装 docker，请先执行 bash deploy/ecs-setup.sh"
  docker info >/dev/null 2>&1 || die "docker 守护进程没起来：systemctl start docker"
}

ensure_network() {
  docker network inspect "$NET" >/dev/null 2>&1 || docker network create "$NET" >/dev/null
}

wait_mysql() {
  log "等待 MySQL 初始化（首次建表约 20–40 秒）"
  for i in $(seq 1 60); do
    if docker exec "${PREFIX}-mysql" mysqladmin ping -h 127.0.0.1 -uroot -proot --silent >/dev/null 2>&1; then
      echo "   MySQL 就绪（等待 ${i} 轮，每轮 2 秒）"
      return 0
    fi
    sleep 2
  done
  die "MySQL 60 次探测仍未就绪，看日志：docker logs ${PREFIX}-mysql"
}

up() {
  need_docker
  ensure_network

  if ! docker volume inspect "$DB_VOL" >/dev/null 2>&1; then
    log "创建数据卷 $DB_VOL"
    docker volume create "$DB_VOL" >/dev/null
  fi

  log "启动 MySQL 8（低内存参数）"
  if docker ps -a --format '{{.Names}}' | grep -qx "${PREFIX}-mysql"; then
    docker start "${PREFIX}-mysql" >/dev/null
  else
    docker run -d --name "${PREFIX}-mysql" \
      --network "$NET" --restart unless-stopped \
      -e MYSQL_ROOT_PASSWORD=root \
      -e MYSQL_DATABASE=textbook_loop \
      -e TZ=Asia/Shanghai \
      -v "$DB_VOL":/var/lib/mysql \
      -v "$ROOT/db/schema.sql":/docker-entrypoint-initdb.d/01-schema.sql:ro \
      --memory 512m \
      mysql:8.0 \
      --character-set-server=utf8mb4 \
      --collation-server=utf8mb4_general_ci \
      --innodb-buffer-pool-size=128M \
      --performance-schema=OFF \
      --max-connections=100 >/dev/null
  fi
  wait_mysql

  log "构建后端镜像（Maven 构建在容器内完成，2C4G 约 3–6 分钟）"
  docker build -t "${PREFIX}-server:latest" "$ROOT/server"

  log "启动后端（8088）"
  docker rm -f "${PREFIX}-server" >/dev/null 2>&1 || true
  docker run -d --name "${PREFIX}-server" \
    --network "$NET" --network-alias server --restart unless-stopped \
    -e SPRING_PROFILES_ACTIVE=mysql \
    -e MYSQL_HOST="${PREFIX}-mysql" -e MYSQL_PORT=3306 \
    -e MYSQL_DB=textbook_loop -e MYSQL_USER=root -e MYSQL_PASSWORD=root \
    -e TZ=Asia/Shanghai \
    -e JAVA_TOOL_OPTIONS="-Xmx512m -Xss256k -XX:MaxMetaspaceSize=192m" \
    -p 8088:8088 --memory 768m \
    "${PREFIX}-server:latest" >/dev/null

  log "等待后端健康检查"
  for i in $(seq 1 60); do
    if curl -fsS http://127.0.0.1:8088/v1/health 2>/dev/null | grep -q '"status":"UP"'; then
      echo "   后端 UP"; break
    fi
    sleep 2
    [ "$i" -eq 60 ] && die "后端未起来：docker logs ${PREFIX}-server"
  done

  log "构建并启动用户端 Web（80）"
  docker build -t "${PREFIX}-web:latest" "$ROOT/web"
  docker rm -f "${PREFIX}-web" >/dev/null 2>&1 || true
  docker run -d --name "${PREFIX}-web" \
    --network "$NET" --restart unless-stopped \
    -p 80:80 --memory 64m "${PREFIX}-web:latest" >/dev/null

  log "构建并启动管理后台（8080）"
  docker build -t "${PREFIX}-admin:latest" "$ROOT/admin"
  docker rm -f "${PREFIX}-admin" >/dev/null 2>&1 || true
  docker run -d --name "${PREFIX}-admin" \
    --network "$NET" --restart unless-stopped \
    -p 8080:80 --memory 64m "${PREFIX}-admin:latest" >/dev/null

  status
  cat <<DONE

全部就绪：
  用户端    http://<公网IP>/
  管理后台  http://<公网IP>:8080/    admin / admin123
  健康检查  http://<公网IP>:8088/v1/health

查看日志：bash deploy/run-with-docker.sh logs
DONE
}

status() {
  need_docker
  docker ps --filter "name=${PREFIX}-" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

case "${1:-up}" in
  up) up ;;
  status) status ;;
  logs) docker logs -f --tail 120 "${PREFIX}-server" ;;
  down)
    need_docker
    for c in web admin server mysql; do docker rm -f "${PREFIX}-$c" >/dev/null 2>&1 || true; done
    log "已停止（数据卷 $DB_VOL 保留）" ;;
  reset)
    for c in web admin server mysql; do docker rm -f "${PREFIX}-$c" >/dev/null 2>&1 || true; done
    docker volume rm "$DB_VOL" >/dev/null 2>&1 || true
    log "已删除容器与数据卷" ;;
  *) die "未知参数：$1（可用：up|status|logs|down|reset）" ;;
esac
