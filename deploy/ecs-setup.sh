#!/usr/bin/env bash
# ============================================================
# 同窗书 · 云服务器一键准备（华为云 / 阿里云通用）
#
# 支持系统：华为云 EulerOS 2.x · openEuler · Ubuntu 20/22/24 · Debian 11/12 ·
#           Alibaba Cloud Linux 3 · CentOS/Rocky/Alma 8/9
# 支持架构：x86_64 / aarch64（鲲鹏）
#
# 用法（root 执行）：
#   bash deploy/ecs-setup.sh
#
# 做的事：装 docker → 配镜像加速 → 小内存加 swap → 时区/句柄调优 → 检查 compose
# 不做的事：不启动业务容器（避免和你的数据卷/端口打架），启动用
#   docker compose up -d --build          （有 compose 插件时）
#   bash deploy/run-with-docker.sh up     （没有 compose 插件时）
# ============================================================
set -euo pipefail

log()  { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn] %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m[fail] %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 执行：sudo bash deploy/ecs-setup.sh"

ARCH="$(uname -m)"
OS_ID="unknown"; OS_NAME="unknown"; OS_VER="unknown"
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  OS_ID="${ID:-unknown}"; OS_NAME="${NAME:-unknown}"; OS_VER="${VERSION_ID:-unknown}"
fi

log "系统识别"
cat <<INFO
  发行版：$OS_NAME $OS_VER（ID=$OS_ID）
  架构  ：$ARCH
  内存  ：$(awk '/MemTotal/ {printf "%d MB", $2/1024}' /proc/meminfo)
  磁盘  ：$(df -h / | awk 'NR==2 {print $4" 可用 / "$2" 总"}')
INFO
[ "$ARCH" = "aarch64" ] && warn "鲲鹏/ARM 架构：镜像会走 arm64 版本，构建时间比 x86 长，属正常现象"

PKG=""
if command -v dnf >/dev/null 2>&1; then PKG=dnf
elif command -v yum >/dev/null 2>&1; then PKG=yum
elif command -v apt-get >/dev/null 2>&1; then PKG=apt
fi
[ -n "$PKG" ] || die "识别不到包管理器（dnf/yum/apt），请把 /etc/os-release 发给开发者"

# ---------------- 1. 基础工具 ----------------
log "安装基础工具（curl / git / tar / unzip）"
case "$PKG" in
  dnf|yum) $PKG install -y curl git tar unzip >/dev/null 2>&1 || $PKG -y install curl git tar unzip >/dev/null ;;
  apt) export DEBIAN_FRONTEND=noninteractive
       apt-get update -y >/dev/null && apt-get install -y curl git tar unzip ca-certificates >/dev/null ;;
esac

# ---------------- 2. 安装 Docker ----------------
# 注意：阿里云 Alibaba Cloud Linux 3 等镜像预装的是 podman-docker（假的 docker CLI），
# `command -v docker` 会命中但它没有 dockerd、也没有 docker.service，
# 所以这里必须同时判断「有没有 dockerd / docker.service」才算真的装了 Docker。
has_real_docker() {
  command -v dockerd >/dev/null 2>&1 \
    && systemctl list-unit-files docker.service >/dev/null 2>&1 \
    && systemctl list-unit-files | grep -q '^docker\.service'
}

if has_real_docker; then
  log "Docker 已存在：$(docker --version 2>/dev/null || echo dockerd)"
else
  if command -v docker >/dev/null 2>&1; then
    warn "检测到 $(docker --version 2>/dev/null | head -1)，但找不到 dockerd / docker.service —— 大概率是 podman-docker，接下来安装真正的 Docker"
  fi
  log "安装 Docker"
  installed=0

  # 路线 A：官方 docker-ce 仓库（走阿里云镜像）；CentOS/RHEL/EulerOS/AliyunLinux 通用
  if [ "$PKG" = "dnf" ] || [ "$PKG" = "yum" ]; then
    if [ ! -f /etc/yum.repos.d/docker-ce.repo ]; then
      curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo \
        -o /etc/yum.repos.d/docker-ce.repo 2>/dev/null || true
      # alinux3 的 $releasever 是 3，docker-ce 仓库只有 el8/el9 目录，统一按 8 取
      [ -f /etc/yum.repos.d/docker-ce.repo ] && sed -i 's/\$releasever/8/g' /etc/yum.repos.d/docker-ce.repo
    fi
    if $PKG install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin --allowerasing >/dev/null 2>&1; then
      installed=1
    fi
    # 路线 A2：发行版自带仓库（EulerOS / openEuler 等）
    if [ "$installed" -eq 0 ]; then
      $PKG install -y docker-engine >/dev/null 2>&1 && installed=1 || true
    fi
  fi

  # 路线 B：Ubuntu/Debian 自带仓库
  if [ "$installed" -eq 0 ] && [ "$PKG" = "apt" ]; then
    if apt-get install -y docker.io >/dev/null 2>&1 && command -v dockerd >/dev/null 2>&1; then installed=1; fi
  fi

  # 路线 C：官方脚本 + 阿里云镜像（兜底）
  if [ "$installed" -eq 0 ]; then
    log "使用 get.docker.com（阿里云镜像）安装"
    curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun || true
    command -v dockerd >/dev/null 2>&1 && installed=1 || installed=0
  fi

  [ "$installed" -eq 1 ] || die "Docker 安装失败：手动执行 'dnf install -y docker-ce --allowerasing'，把报错发回来"
fi

systemctl enable --now docker
systemctl is-active --quiet docker || die "docker 服务没能启动：journalctl -u docker -n 50"
log "Docker 已运行：$(docker info --format '{{.ServerVersion}}' 2>/dev/null || echo ok)"

# ---------------- 3. 镜像加速 ----------------
# 说明：ustc / 163 / dockerproxy 这几个公共镜像在 2024–2026 陆续失效或限流，
# 现在实测可用的是 daocloud 与 1ms.run（阿里云 ECS 上 401 挑战正常，客户端会自动换 token）。
if [ ! -f /etc/docker/daemon.json ]; then
  log "写 /etc/docker/daemon.json（镜像加速 + 日志限额）"
  mkdir -p /etc/docker
  cat >/etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
JSON
  systemctl restart docker
else
  warn "/etc/docker/daemon.json 已存在，跳过（自行确认 registry-mirrors）"
fi

# ---------------- 4. docker compose 插件 ----------------
if docker compose version >/dev/null 2>&1; then
  log "compose 可用：$(docker compose version --short 2>/dev/null || true)"
else
  log "安装 docker compose 插件"
  comp=0
  case "$PKG" in
    dnf|yum) $PKG install -y docker-compose-plugin >/dev/null 2>&1 && comp=1 || true ;;
    apt) apt-get install -y docker-compose-plugin >/dev/null 2>&1 && comp=1 \
         || apt-get install -y docker-compose-v2 >/dev/null 2>&1 && comp=1 || true ;;
  esac

  if [ "$comp" -eq 0 ]; then
    # 直接下二进制到 cli-plugins（华为云/便宜云厂商常缺这个包）
    case "$ARCH" in
      x86_64) BIN=x86_64 ;;
      aarch64) BIN=aarch64 ;;
      *) BIN=x86_64 ;;
    esac
    PLUGDIR=/usr/local/lib/docker/cli-plugins
    mkdir -p "$PLUGDIR"
    for url in \
      "https://repo.huaweicloud.com/docker/compose/v2.29.7/docker-compose-linux-${BIN}" \
      "https://ghfast.top/https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-${BIN}" \
      "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-${BIN}" ; do
      if curl -fsSL --connect-timeout 8 -m 180 -o "$PLUGDIR/docker-compose" "$url"; then
        chmod +x "$PLUGDIR/docker-compose"
        break
      fi
    done
    docker compose version >/dev/null 2>&1 && comp=1 || true
  fi

  if [ "$comp" -eq 1 ]; then
    log "compose 就绪：$(docker compose version --short 2>/dev/null || echo ok)"
  else
    warn "compose 插件没装上——不影响部署，改用无 compose 脚本："
    warn "   bash deploy/run-with-docker.sh up"
  fi
fi

# ---------------- 5. 小内存加 swap ----------------
MEM_MB=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
log "物理内存：${MEM_MB} MB"
if [ "$MEM_MB" -lt 6000 ] && ! swapon --show | grep -q '/swapfile'; then
  log "创建 2 GB swap（避免 MySQL/JVM 峰值被 OOM Killer 杀掉）"
  if ! fallocate -l 2G /swapfile 2>/dev/null; then
    dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  fi
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

# ---------------- 6. 时区与内核 ----------------
timedatectl set-timezone Asia/Shanghai 2>/dev/null || warn "timedatectl 不可用，时区未改"
if [ ! -f /etc/sysctl.d/99-textbook-loop.conf ]; then
  cat >/etc/sysctl.d/99-textbook-loop.conf <<'SYSCTL'
fs.file-max = 200000
net.core.somaxconn = 1024
net.ipv4.tcp_tw_reuse = 1
SYSCTL
  sysctl --system >/dev/null 2>&1 || true
fi

# ---------------- 7. 防火墙 ----------------
if systemctl is-active --quiet firewalld; then
  warn "firewalld 在运行：已放行 http/ssh，另需在云控制台安全组放行 80、8080、8088"
  firewall-cmd --permanent --add-service=http >/dev/null 2>&1 || true
  firewall-cmd --permanent --add-service=ssh  >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

# ---------------- 8. 收尾 ----------------
log "准备完成"
cat <<'DONE'

下一步：把仓库放到服务器（二选一）
  A. 服务器能连 GitHub： git clone <仓库地址> /opt/second-hand_bookstore
  B. 本机上传：          scp -r ./ root@<公网IP>:/opt/second-hand_bookstore/

然后启动（二选一）
  有 compose： cd /opt/second-hand_bookstore && docker compose up -d --build
  无 compose： bash deploy/run-with-docker.sh up

验证
  curl http://127.0.0.1:8088/v1/health
  浏览器 http://<公网IP>/        用户端
  浏览器 http://<公网IP>:8080/   管理后台 admin / admin123

提醒：2C2G 及以下内存请先看 deploy/huawei-cloud.md 的「1C2G / 2C2G 怎么办」一节。
DONE
