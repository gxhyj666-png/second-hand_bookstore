# 云服务器部署手册（阿里云 ECS · 华为云 ECS 通用）

面向：阿里云免费试用 / 学生机、华为云免费试用；常见系统为 Alibaba Cloud Linux 3.2104、Ubuntu 22.04、Huawei Cloud EulerOS 2.0，架构多为 x86_64（华为云部分规格是鲲鹏 aarch64）。

流程只有五步：**确认配置 → 开安全组 → 传代码 → 一键装环境 → 启动**。

---

## 第 0 步：确认这台机器的三件事

在控制台点「远程连接」或用 SSH 登进去，执行：

```bash
uname -m                 # x86_64 还是 aarch64
cat /etc/os-release | head -3
free -m | head -2        # 看 MemTotal
df -h / | tail -1
```

判断标准：

| 结果 | 影响 |
| --- | --- |
| `MemTotal ≥ 3500`（2C4G） | 走标准流程：MySQL + 后端 + 用户端 + 管理后台 |
| `MemTotal ≈ 1800–2000`（2C2G） | 见文末「2 GB 内存怎么办」，去掉 MySQL 用内嵌库 |
| 系统是 Alibaba Cloud Linux 3 | 有 `dnf`，本仓库脚本可直接装 Docker；`docker compose` 插件缺了会自动装二进制 |
| 系统是 CentOS 7.9 | 已 EOL，yum 源失效 → 控制台换系统为 Alibaba Cloud Linux 3 或 Ubuntu 22.04 |

阿里云免费试用额度参考（300 元 / 3 个月，每小时上限 0.833 元）：
2C4G ¥0.254/h ≈ 1181 小时（约 49 天常开）；按需开机（每天 4 小时 × 90 天）约 91 元，最划算。
**不测试时在控制台「停止」实例（不释放），数据盘与 IP 保留。**

---

## 第 1 步：安全组放行端口（控制台操作，两朵云位置不同）

**阿里云**：ECS 控制台 → 实例 → 点实例 ID 进详情 → 「网络与安全」→「安全组」→ 配置规则 → **入方向** → 手动添加：

| 端口 | 用途 | 必须 |
| --- | --- | --- |
| 22 | SSH | ✅ |
| 80 | 用户端 Web | ✅（大陆需备案域名才可对外提供网站；无备案就先用 8080） |
| 8080 | 管理后台 | ✅ |
| 8088 | 后端接口 | 建议临时开 |

**华为云**：控制台 → 弹性云服务器 → 实例 →「安全组」→ 配置规则 → 入方向。

协议 TCP、授权对象 `0.0.0.0/0`；**不要**开放 3306 / 6379。
同时确认实例已绑定公网 IP（阿里云叫 EIP，华为云也叫 EIP）。

---

## 第 2 步：把代码传到服务器

**方式 A：服务器直连 GitHub（推荐）**

```bash
ssh root@<公网IP>
mkdir -p /opt && cd /opt
git clone <你的仓库地址> second-hand_bookstore
cd second-hand_bookstore
```

**方式 B：本机上传**（Windows PowerShell 自带 scp）

```powershell
# 在仓库的上一级目录执行
scp -r .\second-hand_bookstore root@<公网IP>:/opt/
```

**方式 C：对象存储中转**（阿里云 OSS / 华为云 OBS）：打包 zip 上传，服务器 `wget` 临时链接后 `unzip`。一般用不到。

> 不要传 `target/`、`node_modules/`、`dist/`（`.gitignore` 已忽略，scp 时注意排除）。

---

## 第 3 步：一键装环境

```bash
cd /opt/second-hand_bookstore
bash deploy/ecs-setup.sh
```

脚本做的事：识别系统/架构 → 装 Docker（发行版源 → get.docker.com 阿里云镜像，多路线兜底）→ 写镜像加速 → 尝试装 compose 插件（失败会提示用下一个脚本）→ 内存 < 6 GB 自动加 2 GB swap → 设时区 → 调句柄 → 处理 firewalld。

看到 `准备完成` 继续；报错就把最后 10 行发我。

---

## 第 4 步：启动（二选一）

**有 `docker compose`（`docker compose version` 有输出）**

```bash
cd /opt/second-hand_bookstore
docker compose up -d --build      # 首次 5–10 分钟
docker compose ps
```

**没有 compose 插件（Alibaba Cloud Linux 3 / EulerOS 常见）**

```bash
cd /opt/second-hand_bookstore
bash deploy/run-with-docker.sh up # 纯 docker CLI，自动等 MySQL 与 /v1/health
```

启动顺序：MySQL 建表 → 健康检查 → 后端 → 两个前端。

---

## 第 5 步：验证

```bash
curl http://127.0.0.1:8088/v1/health
# 期望 {"code":0,"message":"ok","data":{"status":"UP",...}}

curl -I http://127.0.0.1/         # 200
curl -I http://127.0.0.1:8080/    # 200
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

浏览器打开：

| 入口 | 地址 | 账号 |
| --- | --- | --- |
| 用户端 | `http://<公网IP>/`（80 未放行/未备案用 `:8080` 端口的前端或临时映射） | 自己注册；或 admin / admin123 |
| 管理后台 | `http://<公网IP>:8080/` | admin / admin123 |
| 接口 | `http://<公网IP>:8088/v1/health` | — |

首页能打开、能登录、能检索到 6 本示例教材 = 全链路通了。

---

## 2 GB 内存怎么办（2C2G 免费机）

MySQL 8 即使调优也要 400–600 MB，加 JVM 512 MB 在 2 GB 上会 OOM。用内嵌 H2 单容器跑，答辩完全够用：

```bash
cd /opt/second-hand_bookstore
docker network create textbook-loop 2>/dev/null || true

docker build -t tl-server:latest ./server
docker run -d --name tl-server --restart unless-stopped \
  --network textbook-loop --network-alias server \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JAVA_TOOL_OPTIONS="-Xmx384m -Xss256k" \
  -p 8088:8088 --memory 640m \
  -v /opt/second-hand_bookstore/data:/app/data \
  tl-server:latest

docker build -t tl-web:latest ./web
docker run -d --name tl-web --restart unless-stopped --network textbook-loop -p 80:80 tl-web:latest

docker build -t tl-admin:latest ./admin
docker run -d --name tl-admin --restart unless-stopped --network textbook-loop -p 8080:80 tl-admin:latest
```

- `dev` profile 启动时自动建表 + 灌示例数据（5 用户 / 6 课 / 8 教材 / 6 商品 / 接龙）；
- 数据在 `/opt/second-hand_bookstore/data/textbook_loop.mv.db`，备份=拷文件；
- 答辩口径："演示环境用内嵌库，生产切 MySQL（compose 已定义）"。

> 想保留 MySQL 也行，但要同时满足：已加 2 GB swap、MySQL 加 `--innodb-buffer-pool-size=64M --max-connections=30`、JVM `-Xmx320m`，且只用 `docker compose up -d mysql server web`（admin 演示时再起）。**务必答辩前压测一遍。**

---

## 手机访问 / APK 联调

- 手机浏览器直接开 `http://<公网IP>/`，前端已响应式；
- 打 APK 时在 `web/.env.production` 写 `VITE_API_BASE=http://<公网IP>:8088`（或走同域 80 的 `/api`）；
- Android 9+ 明文 HTTP 需在 `AndroidManifest.xml` 加 `android:usesCleartextTraffic="true"`；
- 最省事：答辩用手机浏览器演示，APK 后置。

---

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 外网打不开，`curl 127.0.0.1` 正常 | 安全组未放行 | 控制台加入方向 TCP 规则 |
| `:8088` 也不通 | 后端没起来 | `docker logs tl-server --tail 100`，多为 MySQL 未就绪 |
| 页面白屏 / 接口 502 | Nginx 反代 `server:8088` 找不到主机 | 后端容器必须在同一 network 且别名 `server`（脚本已加） |
| `docker compose` 不存在 | 发行版无该插件 | `bash deploy/run-with-docker.sh up` |
| 拉镜像超时 | 加速器失效 | `docker pull mysql:8.0` 单测，换加速器 |
| 容器反复重启 / `Killed` | 内存不足 OOM | 按上文精简，或升 4 GB |
| 磁盘满 | 反复 build 的中间层 | `docker system prune -af`（别带 `--volumes`） |
| 80 端口要求备案 | 大陆 ECS 对外提供网站 | 用非 80 端口演示，或备案域名 |
| 访问域名很慢 | 未开 gzip / 地域远 | 已开 gzip；就近选地域（华东1·杭州） |

---

## 停机、备份、省钱

```bash
# 备份（有 MySQL 时）
docker exec tl-mysql mysqldump -uroot -proot textbook_loop > /opt/backup-$(date +%F).sql
# 2 GB 方案（H2）：直接拷 data 目录
tar czf /opt/data-$(date +%F).tgz -C /opt/second-hand_bookstore data

# 停止服务
docker compose down        # 或 bash deploy/run-with-docker.sh down
```

然后控制台「停止」实例（不释放）= 关机不计实例费，磁盘与 IP 保留；演示前「启动」→ `docker compose start`。

## 相关文件

- `deploy/ecs-setup.sh` — 环境准备（Docker / 加速器 / swap / compose 检查）
- `deploy/run-with-docker.sh` — 无 compose 时的等价启动脚本
- `docker-compose.yml` — 标准四容器（MySQL / 后端 / 用户端 / 管理后台），已按小内存调优
- `deploy/README.md` — 通用部署说明
