# 华为云 ECS 部署手册（同窗书）

面向：华为云免费试用 / 学生机（弹性云服务器 ECS），系统多为 Huawei Cloud EulerOS 2.0、Ubuntu 22.04 或 openEuler，架构可能是 x86_64 或鲲鹏 aarch64。

整个流程只有 5 步：**确认配置 → 开安全组 → 传代码 → 一键装环境 → 启动**。

---

## 第 0 步：先确认这台机器的三件事

登录服务器后执行：

```bash
uname -m                 # x86_64 还是 aarch64（鲲鹏）
cat /etc/os-release      # 系统与版本
free -m                  # 内存（看 MemTotal）
df -h /                  # 磁盘可用
```

把上面四行输出发我也可以，我按结果给具体命令。判断标准：

| 结果 | 影响 |
| --- | --- |
| `aarch64` | 鲲鹏机器，镜像走 arm64，首次构建慢 2–3 倍，**能用**，不用换系统 |
| `MemTotal` ≥ 3500 | 按本手册标准流程走（MySQL + 后端 + 两个前端共存） |
| `MemTotal` ≈ 1800–2000 | 见文末「2C2G 怎么办」，要精简掉 MySQL 或大幅压 JVM |
| `ID=openeuler` / `hce` / `euleros` | 系统自带源里**通常没有 docker compose 插件** → 用 `deploy/run-with-docker.sh`，别硬装 |

> 如果系统是 CentOS 7.9：官方已 EOL，yum 源失效，建议在控制台「重置密码/重装系统」里换成 **Ubuntu 22.04** 或 **Huawei Cloud EulerOS 2.0**。

---

## 第 1 步：安全组放行端口（控制台操作）

华为云控制台 → 弹性云服务器 ECS → 实例 → 「安全组」→ 配置规则 → **入方向** → 添加规则：

| 端口 | 用途 | 是否必须 |
| --- | --- | --- |
| 22 | SSH 登录 | 必须 |
| 80 | 用户端 Web（平台主入口） | 必须 |
| 8080 | 管理后台 | 必须 |
| 8088 | 后端接口（调试用，正式可关） | 建议临时开 |

协议选 TCP，源地址 `0.0.0.0/0`（测试期）；不要在安全组开放 3306 / 6379。

同时确认实例已绑定**弹性公网 IP（EIP）**，否则外网访问不到。

---

## 第 2 步：把代码传到服务器

**方式 A：服务器直连 GitHub（最快，服务器能联网就行）**

```bash
ssh root@<公网IP>
mkdir -p /opt && cd /opt
git clone <你的仓库地址> second-hand_bookstore
cd second-hand_bookstore
```

> 仓库如果是私有的，用带 token 的地址，或用方式 B。

**方式 B：本机上传（Windows PowerShell 即可，Win10+ 自带 scp）**

```powershell
# 在仓库根目录的上一级执行
scp -r .\second-hand_bookstore root@<公网IP>:/opt/
```

首次连接会问 `yes/no`，输 yes；然后输密码（或指定密钥 `-i C:\path\key.pem`）。

**方式 C：华为云 OBS**（代码包几百 MB、或服务器根本连不上外网时）

控制台上传 zip 到 OBS → 服务器 `wget <OBS 临时链接>` → `unzip`。一般用不到。

> 传完后确认目录里有 `server/ web/ admin/ db/ deploy/ docker-compose.yml`；`target/`、`node_modules/` 不要传（`.gitignore` 已忽略）。

---

## 第 3 步：一键装环境

```bash
cd /opt/second-hand_bookstore
bash deploy/ecs-setup.sh
```

脚本会做：识别系统与架构 → 装 Docker（华为云源 / 官方源多路线兜底）→ 写镜像加速 → 装 compose 插件（装不上会提示，不影响后续）→ 内存 < 6 GB 自动加 2 GB swap → 设时区 → 调句柄 → 放行 firewalld。

看到 `准备完成` 就继续。若中途报错，把最后 10 行发我。

---

## 第 4 步：启动

**有 docker compose（`docker compose version` 有输出）**

```bash
cd /opt/second-hand_bookstore
docker compose up -d --build          # 首次 5–10 分钟（ARM 更久）
docker compose ps
```

**没有 compose 插件（EulerOS 常见）**

```bash
cd /opt/second-hand_bookstore
bash deploy/run-with-docker.sh up     # 纯 docker CLI，功能等价
```

启动顺序脚本已处理好：MySQL 初始化 → 等健康 → 后端 → 等 `/v1/health` → 两个前端。

---

## 第 5 步：验证（4 条）

```bash
curl http://127.0.0.1:8088/v1/health
# 期望：{"code":0,...,"data":{"status":"UP",...}}

curl -I http://127.0.0.1/            # 期望 200
curl -I http://127.0.0.1:8080/       # 期望 200
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

然后浏览器（手机也行，同一 wifi/公网都行）：

| 入口 | 地址 | 账号 |
| --- | --- | --- |
| 用户端 | `http://<公网IP>/` | 自己注册；或 admin / admin123 |
| 管理后台 | `http://<公网IP>:8080/` | admin / admin123 |
| 接口 | `http://<公网IP>:8088/v1/health` | — |

首屏能出首页、能登录、能检索到 6 本示例教材，就说明全链路通了。

---

## 1C2G / 2C2G 怎么办

免费试用常见 2 核 2 GB（甚至 1 核 2 GB）。MySQL 8 即使压到 128 MB 缓冲池，峰值也要 400–600 MB，再叠 JVM 512 MB，2 GB 会很吃力。两个方案：

### 方案 A（推荐，2 GB 内存）：去掉 MySQL，用内嵌 H2 单文件库

```bash
cd /opt/second-hand_bookstore

# 先建网络：容器名会被 nginx 当成上游主机名，必须给后端加别名 server
docker network create textbook-loop 2>/dev/null || true

# 只需要后端（内嵌 H2）+ 两个前端，不启动 MySQL
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

- `dev` profile 会自动建表并灌示例数据（5 用户 / 6 课 / 8 教材 / 6 商品），**答辩足够用**；
- 数据落在 `/opt/.../data/textbook_loop.mv.db`，备份就是拷这个文件；
- 代价：并发弱、无 MySQL 那种"企业级"叙事。答辩时说明"演示环境用内嵌库，生产切 MySQL（`docker-compose.yml` 已有定义）"即可。

### 方案 B（2 GB 内存想保留 MySQL）

- 保留 `docker compose up -d mysql server`，前端只起 `web`（admin 演示时再起）；
- 给 MySQL 加 `--innodb-buffer-pool-size=64M --performance-schema=OFF --max-connections=30`；
- JVM 压到 `-Xmx320m`；
- 必须先确认 `deploy/ecs-setup.sh` 已加 2 GB swap（脚本默认做）。
- 依然可能 OOM，**答辩前务必压测一遍**。

---

## 手机 / APK 联调

- 手机浏览器直接访问 `http://<公网IP>/` 即可，前端已做响应式；
- 打 APK 时把接口地址写死到公网：`web/.env.production` 加 `VITE_API_BASE=http://<公网IP>:8088`（或走 80 的 `/api`，需在同域部署）；
- Android 9+ 用明文 HTTP 要在 `AndroidManifest.xml` 加 `android:usesCleartextTraffic="true"`；
- 想省事：答辩就用手机浏览器演示，APK 后置。

---

## 常见问题对照表

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 浏览器打不开，`curl 127.0.0.1` 正常 | 安全组没放行 80 | 控制台加入方向规则 |
| `curl 127.0.0.1:8088` 也不通 | 后端没起来 | `docker logs tl-server --tail 100`，多为 MySQL 未就绪 |
| 页面白屏、接口 502 | Nginx 反代到 `server:8088` 失败 | 确认容器名是 `tl-server` 且在同一 network；用 compose 时它会自动建网 |
| `docker compose` 命令不存在 | EulerOS 无该插件 | `bash deploy/run-with-docker.sh up` |
| 拉镜像超时 | 加速器无效 | `docker pull mysql:8.0` 单测；必要时换镜像源 |
| `Killed` / 容器反复重启 | 内存不足被 OOM | 按上文精简，或升级到 4 GB |
| 磁盘满 | 反复 build 产生中间层 | `docker system prune -af`（别加 `--volumes`） |
| ARM 机器构建报 `no matching manifest` | 镜像无 arm64 版 | 本仓库用的 mysql/node/nginx/temurin 都有 arm64；其余镜像换官方版 |
| 80 端口访问被要求备案 | 中国大陆 ECS 直接对外提供网站 | 用 `IP:8080` 演示，或换非 80 端口，或备案域名 |

---

## 停机、备份与省钱

```bash
# 备份数据库（有 MySQL 时）
docker exec tl-mysql mysqldump -uroot -proot textbook_loop > /opt/backup-$(date +%F).sql

# 演示结束
docker compose down          # 或 bash deploy/run-with-docker.sh down
```

然后在华为云控制台「关机」（Stop，不释放）：数据盘与 EIP 保留，按需开机；免费试用额度按小时扣，关机不计实例时长（以控制台计费说明为准）。

## 相关文件

- `deploy/ecs-setup.sh`：环境准备（Docker、镜像加速、swap、compose 检查）
- `deploy/run-with-docker.sh`：无 compose 时的等价启动脚本
- `docker-compose.yml`：标准四容器（MySQL / 后端 / 用户端 / 管理后台），已按小内存调优
- `deploy/README.md`：通用部署说明（阿里云等同样适用）
