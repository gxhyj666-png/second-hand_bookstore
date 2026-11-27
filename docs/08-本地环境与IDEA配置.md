# 08 本地环境搭建与 IDEA 配置

> 目标：**别人把仓库拷贝过去，IDEA 打开 → 跑一个脚本 → 就能启动**。不用装 Maven，不用配 MySQL，不用手填一堆配置项。

## 1. 环境清单

| 组件 | 版本要求 | 是否必须先装 | 说明 |
| --- | --- | --- | --- |
| JDK | **17**（推荐 Amazon Corretto 17） | ✅ 必须 | 项目用 Java 17 语法与 Spring Boot 3，低版本编译不过 |
| Maven | 3.9.x | ❌ 不需要 | 仓库自带 **Maven Wrapper**（根目录 `mvnw` / `mvnw.cmd`），首次运行自动下载 3.9.9 |
| Node.js | ≥ 18（推荐 20/22/24 LTS） | ✅ 必须（要跑前端） | 只跑后端可以不装 |
| pnpm | 9 / 11 | ❌ 脚本自动处理 | 脚本会用 `corepack` 启用，失败则退回 `npx pnpm@11` |
| MySQL | 8.0 | ❌ 可选 | 默认 dev profile 用 **H2 文件库**，零依赖；要接 MySQL 用 `mysql` profile |
| IDE | IntelliJ IDEA 2023+ | 建议 | 仓库已带 `.run/` 运行配置与 `.idea` 的 SDK/编码设置 |

## 2. 一键配置脚本

在仓库根目录执行（PowerShell 5.1+，Windows 自带的就够）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1
```

脚本做 6 件事，**幂等**（重复跑不报错、不覆盖已有私有配置）：

1. 找 JDK 17：依次看 `-JdkPath` → `JAVA_HOME` → `%USERPROFILE%\.jdks\*17*`，找到就为本进程设置 `JAVA_HOME`
2. 检查 Maven Wrapper（缺失时提示装 Maven）
3. 检查 Node ≥ 18、pnpm（必要时用 corepack 启用）
4. 用 `mvnw -DskipTests compile` 编译后端（走聚合工程，编译 `server` 模块）
5. 在 `web/`、`admin/` 执行 `pnpm install --frozen-lockfile`
6. 写 IDE 与本地配置：
   - `.idea/misc.xml`：Project SDK 指向本机 JDK 17（从 IDEA 的 `jdk.table.xml` 里匹配 SDK 名，如 `corretto-17`）
   - `.idea/encodings.xml`：UTF-8；`.idea/compiler.xml`：target 17 + 开启注解处理（Lombok 需要）
   - `server/src/main/resources/application-spug-local.yml`：不存在才创建，模板编码留空 = 走 mock
   - `admin/.env`：不存在才创建，默认 `VITE_USE_MOCK=true`

常用参数：

| 参数 | 作用 |
| --- | --- |
| `-Start` | 配好后顺手启动后端并等待 `/v1/health` |
| `-WithPackage` | 编译并打出 `server/target/textbook-loop-server-0.1.0.jar` |
| `-SkipInstall` | 跳过 `pnpm install`（前端依赖已装好时） |
| `-SkipBuild` | 跳过后端编译 |
| `-JdkPath "C:\...\jdk17"` | 手动指定 JDK 17 位置 |

退出码：0 = 全部就绪；1 = 有项目没配上（脚本会逐条列出缺什么）。

## 3. IDEA 里要做的三件事

### 3.1 打开项目并导入 Maven

- `File > Open` 选仓库根目录（不是 `server/`）。
- 右下角/顶部若提示 **Load Maven Project**（或 “Maven projects need to be imported”），点 **Load**。
  根目录有聚合 `pom.xml`，导入后模块树里会出现 `textbook-loop-server`（对应 `server/`）。

### 3.2 确认 SDK 与 Maven 设置

| 设置项 | 位置 | 期望值 |
| --- | --- | --- |
| Project SDK | `File > Project Structure > Project` | **17（corretto-17 / temurin-17）** |
| Language level | 同上 | `17 - Sealed types, always-strict floating-point semantics` |
| Maven | `Settings > Build Tools > Maven` | **Use Maven wrapper**（或 Maven home 指向自带 3.9.9） |
| Maven JRE | 同上 | 17 |
| 编码 | `Settings > Editor > File Encodings` | 全局/项目均 **UTF-8** |
| Lombok | `Settings > Build > Compiler > Annotation Processors` | ✅ Enable annotation processing |
| 终端编码 | 可选 | PowerShell 里先执行 `chcp 65001`，避免中文乱码 |

### 3.3 选运行配置（`Run/Debug Configurations` 下拉）

仓库自带 5 个共享配置（`.run/*.run.xml`，IDEA 会自动识别）：

| 名称 | 内容 | 端口 |
| --- | --- | --- |
| **Backend dev (H2)** | `com.textbook.loop.LoopApplication` + `--spring.profiles.active=dev`，工作目录 = `$PROJECT_DIR$` | 8088 |
| **Backend mysql** | 同上但 `--spring.profiles.active=mysql`，带 `MYSQL_*` 环境变量 | 8088 |
| **Web dev (Vite)** | `web/` 下 `pnpm dev` | 5174 |
| **Admin dev (Vite)** | `admin/` 下 `pnpm dev`（默认 mock 数据） | 5173 |
| **Dev all** | 上面「Backend dev (H2)」+「Web dev (Vite)」组合启动 | 8088 + 5174 |

> 💡 **运行配置里的包管理器是 `npm`，不是 pnpm**：IDEA 的 npm 运行配置需要能解析到包管理器可执行文件，
而有些机器上 pnpm 只存在于 corepack / 第三方包装器里，IDEA 会报「无法解析 'pnpm' 软件包管理器」。
这两个配置只是执行 `package.json` 里的 `dev` 脚本（实际调用本地 `node_modules/.bin/vite`），用 Node 自带的
`npm` 一样能跑；**依赖仍由 `pnpm install` 安装**，两者不冲突。
若本机确实装了全局 pnpm 且想让 IDEA 用它：`Settings > Languages & Frameworks > Node.js > Package manager` 指定 pnpm 路径。
> ⚠️ **后端工作目录必须是仓库根**。`application-dev.yml` 里 H2 是 `jdbc:h2:file:./data/textbook_loop`，相对**进程工作目录**解析；如果工作目录设成 `server/`，数据库会跑到 `server/data/`，出现「换了个库、数据不见了」。`.run` 配置已经把 `WORKING_DIRECTORY` 写成 `$PROJECT_DIR$`，不要改。

## 4. 启动后验证

| 地址 | 内容 |
| --- | --- |
| <http://127.0.0.1:8088/v1/health> | `{"code":0,"data":{"status":"UP",...}}` |
| <http://localhost:5174> | 用户端（买家/卖家/团长/商户） |
| <http://localhost:5173> | 管理后台（默认 mock；接真后端改 `admin/.env` 的 `VITE_USE_MOCK=false`） |
| <http://127.0.0.1:8088/h2-console> | H2 控制台（JDBC URL 见 `application-dev.yml`，用户 `sa`，空密码） |

演示账号：买家 `13800000002`、卖家 `13800000003`（密码 `123456`），管理员 `13800000001` / `admin123`。
登录默认走**短信验证码**：dev 的 `app.sms-provider=mock`，`POST /v1/auth/sms-code` 会把验证码作为 `devCode` 返回，前端自动回填。

重置数据：`powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1`（停服务 → 删仓库根 `data/` → 重启 → 等健康检查）。

## 5. 只跑后端（不要前端）

```powershell
# 方式一：IDEA 里跑 Backend dev (H2)
# 方式二：命令行
.\mvnw.cmd -DskipTests package
java -jar server\target\textbook-loop-server-0.1.0.jar --spring.profiles.active=dev
```

## 6. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `无效的目标发行版: 17` / `release version 17 not supported` | IDEA 的 Project SDK 或 Maven JRE 不是 17；按 3.2 改 |
| `Port 8088 was already in use` | 已有后端在跑：`Get-NetTCPConnection -LocalPort 8088` 找 PID 后 `Stop-Process -Id <pid> -Force`，或直接用 `tools\reset-dev.ps1` |
| 重启后数据「消失」/ 出现两套数据 | 工作目录不是仓库根，H2 文件被写到 `server/data/`；按 3.3 检查 `WORKING_DIRECTORY` |
| 前端 `pnpm install` 报 esbuild/vue-demi 构建脚本被拦 | pnpm 11 默认禁止依赖执行构建脚本；`web/pnpm-workspace.yaml`、`admin/pnpm-workspace.yaml` 里已声明 `allowBuilds`，若被改过请恢复 |
| `mvnw` 下载很慢 | 首次会从 Maven Central 拉 3.9.9（约 9 MB）；可先在 IDEA 里配好 Maven home 绕过 wrapper |
| 中文在 PowerShell 里乱码 | 脚本已强制 UTF-8；交互终端先 `chcp 65001`，`.ps1` 文件必须带 BOM（本仓库的脚本都有） |
| IDEA 打开后没有运行配置 | `.run/` 目录要在仓库根；若被删，重新 `git checkout -- .run` 或手建 Application 配置（Main class `com.textbook.loop.LoopApplication`，程序参数 `--spring.profiles.active=dev`，工作目录仓库根） |

## 7. 给别人交付的最小步骤（可直接复制给对方）

```powershell
# 1) 拿到项目后进入目录
cd second-hand_bookstore

# 2) 一键配置（检查 JDK/Node，编译后端，装前端依赖，写好 IDEA 配置）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1

# 3) IDEA：File > Open 选该目录 → 右下角 Load Maven Project

# 4) 运行配置选 Dev all（或 Backend dev (H2)），浏览器打开 http://localhost:5174
```

> 没有装 Node 也想先看后端接口？第 2 步加 `-SkipInstall`，只跑后端即可。
