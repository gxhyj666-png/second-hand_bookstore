#!/usr/bin/env bash
# ============================================================
# 同窗书 · ECS 一键准备脚本（Alibaba Cloud Linux 3 / Ubuntu 22.04）
#
# 用法（在服务器上以 root 执行）：
#   bash deploy/ecs-setup.sh
#
# 做完这一步后，把代码上传到 /opt/second-hand_bookstore 再执行：
#   cd /opt/second-hand_bookstore && docker compose up -d
# ============================================================
set -euo pipefail

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn] %s\033[0m\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 执行：sudo bash deploy/ecs-setup.sh" >&2
  exit 1
fi

# ---------- 1. 基础工具 ----------
log "安装基础工具"
if command -v dnf >/dev/null 2>&1; then
  dnf install -y curl git tar unzip >/dev/null
elif command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y >/dev/null && apt-get install -y curl git tar unzip >/dev/null
fi

# ---------- 2. 安装 Docker（国内镜像源） ----------
if command -v docker >/dev/null 2>&1; then
  log "Docker 已安装：$(docker --version)"
else
  log "安装 Docker（Aliyun 镜像源）"
  curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun
fi

systemctl enable --now docker
log "Docker 状态：$(systemctl is-active docker)"

# ---------- 3. Docker 镜像加速 ----------
if [ ! -f /etc/docker/daemon.json ]; then
  log "写入 /etc/docker/daemon.json（镜像加速 + 日志限额）"
  mkdir -p /etc/docker
  cat >/etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
JSON
  systemctl restart docker
else
  warn "/etc/docker/daemon.json 已存在，跳过（请自行确认 registry-mirrors 配置）"
fi

# ---------- 4. 小内存机器加 swap ----------
MEM_MB=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
log "物理内存：${MEM_MB} MB"
if [ "$MEM_MB" -lt 6000 ] && ! swapon --show | grep -q '/swapfile'; then
  log "内存 < 6 GB，创建 2 GB swap（避免 MySQL/JVM 峰值 OOM）"
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

# ---------- 5. 时区与文件句柄 ----------
log "设置时区为 Asia/Shanghai"
timedatectl set-timezone Asia/Shanghai || warn "timedatectl 不可用，跳过"

if [ ! -f /etc/sysctl.d/99-textbook-loop.conf ]; then
  log "调高文件句柄与连接数上限"
  cat >/etc/sysctl.d/99-textbook-loop.conf <<'SYSCTL'
fs.file-max = 200000
net.core.somaxconn = 1024
net.ipv4.tcp_tw_reuse = 1
SYSCTL
  sysctl --system >/dev/null
fi

# ---------- 6. 防火墙提示 ----------
log "检查本机防火墙"
if systemctl is-active --quiet firewalld; then
  warn "firewalld 正在运行：请在阿里云控制台安全组放行 22 / 80，本机也要放行"
  firewall-cmd --permanent --add-service=http  >/dev/null 2>&1 || true
  firewall-cmd --permanent --add-service=ssh   >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

# ---------- 7. 只打印必要信息，不自动改业务配置 ----------
log "完成"
cat <<'DONE'

接下来（把本仓库上传到 /opt/second-hand_bookstore 之后）：

  cd /opt/second-hand_bookstore
  docker compose up -d --build        # 首次构建约 5–10 分钟（2C4G）
  docker compose ps
  curl http://127.0.0.1:8088/v1/health

访问：
  用户端   http://<公网IP>/
  管理后台 http://<公网IP>:8080/     admin / admin123

2C4G 机器请在 docker-compose.yml 里确认已限制内存（本仓库默认已配）：
  mysql  --innodb-buffer-pool-size=128M --performance-schema=OFF
  server JAVA_TOOL_OPTIONS=-Xmx512m -Xss256k

注意：不要在服务器上反复 `docker compose build`，会吃掉 2–3 GB 磁盘；
本地构建好镜像再 docker save/load，或定期 `docker system prune -af`。
DONE
