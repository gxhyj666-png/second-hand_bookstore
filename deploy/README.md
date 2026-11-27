# 部署（阿里云 ECS · Docker Compose）

面向：2 vCPU 4 GB 的入门 ECS（Alibaba Cloud Linux 3 / Ubuntu 22.04），用 Docker Compose 一把起。
本地开发不需要看这个文件。

## 0. 前置

- 已购买 ECS 并拿到公网 IP，安全组放行 **22**、**80**（测试期可加 **8088**）；
- 系统盘 ≥ 40 GB；
- 已 `ssh root@<IP>` 登录。

## 1. 一键准备

```bash
bash deploy/ecs-setup.sh
```

脚本做了这些事：装基础工具 → 用阿里云镜像装 Docker → 写 `/etc/docker/daemon.json`（镜像加速 + 日志限额）→
内存 < 6 GB 时自动加 2 GB swap → 设时区 → 调文件句柄 → 放行 firewalld 的 80/22。

## 2. 上传代码

在**本机**执行（Windows 用 Git Bash 或 PowerShell 均可）：

```bash
scp -r ./* root@<IP>:/opt/second-hand_bookstore/
```

或用 git（服务器能连 GitHub 时）：

```bash
ssh root@<IP>
git clone <仓库地址> /opt/second-hand_bookstore
```

## 3. 启动

```bash
cd /opt/second-hand_bookstore
docker compose up -d --build        # 首次约 5–10 分钟
docker compose ps
curl http://127.0.0.1:8088/v1/health
```

访问：

| 入口 | 地址 | 账号 |
| --- | --- | --- |
| 用户端 | `http://<IP>/` | 自己注册，admin/admin123 也可直接登 |
| 管理后台 | `http://<IP>:8080/` | admin / admin123 |
| 接口 | `http://<IP>:8088/v1/health` | — |

## 4. 小内存机器（2C4G）已做的调优

已写进 `docker-compose.yml`，不需要额外操作：

| 服务 | 限制 | 原因 |
| --- | --- | --- |
| mysql | `--innodb-buffer-pool-size=128M --performance-schema=OFF`，`mem_limit 512m` | 默认缓冲池 128 MB 起、performance_schema 另占百兆 |
| server | `JAVA_TOOL_OPTIONS=-Xmx512m -Xss256k`，`mem_limit 768m` | 给 JVM 明确上限，避免和 MySQL 抢 |
| web / admin | `mem_limit 64m` | 纯静态 |

> 后端当前没有引入 Redis 依赖，因此 compose 里不再启动 redis 容器（省约 200 MB 内存）。
>
> 阿里云 / 华为云环境请看更具体的 `deploy/cloud-ecs.md`（含无 compose 插件时的 `run-with-docker.sh` 用法与 2 GB 内存方案）。

另外 3306 / 6379 只绑 `127.0.0.1`，公网访问不到。

## 5. 常见问题

**`docker compose up` 卡在拉镜像**
`docker compose config` 看镜像名是否被加速器劫持；必要时 `docker pull mysql:8.0` 单独试。

**接口 502 / 页面白屏**
`docker compose logs -f server` 看是否连不上 MySQL（等待 healthcheck 通过通常要 20–40 秒）；`docker compose ps` 看 mysql 是否 healthy。

**磁盘满**
`docker system prune -af --volumes`（会删掉未使用的卷，注意备份）＋ 不要反复 `--build`；建议本地构建后用 `docker save` 传镜像。

**SSL / 域名**
80 端口直接对外需要 ICP 备案；用域名 + HTTPS 时在前面加 Nginx 或阿里云 SLB，证书用免费 DV。

**关机省钱**
控制台「停止」实例即可（不释放），下次「启动」后 `docker compose start` 恢复；磁盘与数据保留，按小时计费停摆。

## 6. 演示结束后

```bash
mysqldump -uroot -proot textbook_loop > /opt/backup-$(date +%F).sql
```

把备份拉回本机留档，再在控制台停止实例。
