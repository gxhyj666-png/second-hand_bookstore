# AGENTS.md — 同窗书项目记忆与协作约定

> 任何 AI 助手（或第一次接手的人）在动代码前，**先读完本文件**，再读 `docs/09-项目状态与交接.md`。
> 本文件是"项目宪法"：写清楚这是什么、怎么跑、改哪里、别踩哪些坑。最后更新：2026-11-28。

## 1. 这是什么

**同窗书**：校园二手教材循环交易平台，毕业设计项目。核心链路是
「课程 → 指定教材 → 二手书供给 → 线下约见 → 卖家核销 → 售后仲裁」，把"学长学姐卖书 / 班委统一收书"变成可检索、可担保、可追责的闭环。

- 仓库根：`D:\Users\86180\Desktop\second-hand_bookstore`（远端 `github.com/gxhyj666-png/second-hand_bookstore`，主分支 `main`）
- 形态：**前后端分离的单体应用**（不是微服务）。后端只出 JSON REST，`web/`、`admin/` 各自 `vite build` 出静态 dist。
- 三端：**个体端 + 商户端**（`web/`，Vant 响应式 H5，可 Capacitor 出 APK）、**平台端**（`admin/`，Element Plus）、**服务端**（`server/`）。`miniapp/` 是旧流程参考实现，**非主交付**。

## 2. 技术栈（版本以 pom/package.json 为准）

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.5、JJWT 0.12.5、H2 2.2.224 / MySQL 8.0 |
| 用户端 | Vue 3.5、Vite 5.4、Vant 4.9、Pinia、axios（hash 路由，纯 CSR） |
| 管理后台 | Vue 3.4、Vite 5.4、Element Plus 2.7、ECharts 5.5 |
| 构建 | Maven Wrapper（仓库根 `mvnw.cmd`，免装 Maven）、pnpm 11.8（锁文件已提交） |
| 部署 | Docker Compose 四容器（mysql / server / web / admin）+ Nginx，阿里云 ECS 2C4G |

## 3. 目录地图

```
pom.xml                 聚合 POM（只含 <module>server</module>，让 IDEA 识别成 Maven 工程）
mvnw / mvnw.cmd / .mvn  Maven Wrapper（根目录，boot 别删）
.run/                   IDEA 共享运行配置（后端 dev / 后端 mysql / Web / Admin / Dev all）
server/                 后端（src/main/java/com/textbook/loop/）
  controller/ service/ domain/ mapper/ security/ config/ sms/ bootstrap/ common/
  src/main/resources/   application*.yml、schema-h2.sql（生成物，勿手改）
web/ admin/ miniapp/    三个前端
db/schema.sql           MySQL 8 正式 DDL（38 表）；schema-h2.sql 由 tools/gen-h2-schema.js 生成
tools/                  脚本：setup-dev / start-backend / reset-dev / smoke / e2e-trade / gen-entities / gen-h2-schema / md2docx
docs/                   01–08 设计文档，09 状态交接，10 新会话提示；docs/word/ 是 Word 版
deploy/                 ECS 部署脚本与手册
```

## 4. 常用命令（Windows / PowerShell 5.1）

```powershell
# 一键配环境（新机器/新拷贝先跑这个）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1

# 启动后端（默认 mock 短信；-Sms spug 走真实短信；-Reset 清库重灌种子）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-backend.ps1 -Sms spug

# 回到干净演示数据（停服务 → 删 data/ → 重启 → 等健康检查）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1

# 验证（后端必须在 8088 跑着）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke.ps1        # 17 项主链路
powershell -NoProfile -ExecutionPolicy Bypass -File tools\e2e-trade.ps1    # 23 项纠纷/私信/商户链路

# 构建
.\mvnw.cmd -q -DskipTests package          # 产物 server/target/textbook-loop-server-0.1.0.jar
cd web;  pnpm install; pnpm build          # 注意：pwsh 下用 pnpm，不要用 npm（npm.ps1 被执行策略拦）
cd admin; pnpm install; pnpm build

# 重新生成 H2 DDL / 实体（改了 db/schema.sql 之后必须跑）
node tools/gen-entities.js ; node tools/gen-h2-schema.js

# 重新生成 Word（docs/*.md → docs/word/*.docx）
python tools/md2docx.py
```

## 5. 环境事实（本机，换机器以 setup-dev.ps1 探测结果为准）

- JDK 17：`C:\Users\86180\.jdks\corretto-17.0.20.1`（**系统默认 java 是 1.8，必须用这个**）
- Maven：不用装，根目录 `mvnw.cmd` 会自动下载 3.9.9
- Node v24.20.0 / pnpm 11.8.0（`pnpm` 是 DSH 运行时包装器，IDEA 里解析不到 → `.run` 配置里显式用 npm）
- 端口：后端 **8088**、用户端 **5174**、后台 **5173**、MySQL 3306、web/admin 容器 80/8080
- H2 数据文件：**仓库根 `data/`**（不是 `server/data/`）——`application-dev.yml` 里是相对路径 `./data`，**相对进程工作目录**，所以后端必须从仓库根启动
- 演示账号：管理员 `13800000001 / admin123`；买家 `13800000002`、卖家 `13800000003`、其他 `13800000004/5`（密码 `123456`，短信登录为主，mock 下验证码随响应返回）
- 短信：Spug 推送助手官方模板；模板编码只放 `server/src/main/resources/application-spug-local.yml`（**已 gitignore**）或环境变量 `APP_SPUG_TEMPLATE_CODE`

## 6. 接口约定

- 统一前缀 `/v1`，统一响应 `{ "code": 0, "message": "ok", "data": ... }`
- 错误码：`40000` 参数错 / `40100` 未登录 / `40300` 无权限 / `40400` 不存在 / `40900` 状态冲突
- 分页：`{ records, total, page, size }`
- 认证：`Authorization: Bearer <JWT>`；`AuthInterceptor` **只解析不拒绝**，需要登录的接口由 `UserContext.requireUserId()` 抛 401，游客可读的 GET 用 `currentUserIdOrNull()`
- 游客可浏览：商品列表/详情、推荐、课程、教材、ISBN、自提点；交易与私信需要**实名 + 学生双认证**
- 第三方能力都在接口层后面，改 `app.*-provider` / `app.identity-provider` 即可切换：
  - 短信：`app.sms-provider=mock|spug`（Spug 模板编码放 `application-market-local.yml` 或 `APP_SPUG_TEMPLATE_CODE`）
  - 实名：`app.identity-provider=mock|aliyun`（阿里云云市场身份证二要素，AppCode 同账号共用）
  - ISBN：`app.isbn-provider=mock|market`（云市场「ISBN 标准书号查询」）；`POST /v1/isbn/scan` 是本地 ZXing 解码书背 EAN-13，离线可用、不消耗额度
  - 凭证统一放 gitignored 的 `server/src/main/resources/application-market-local.yml`，**绝不进仓库**

## 7. 关键设计决策（改之前先看理由）

1. **订单三态主干**：`PENDING_PAY → WAIT_VERIFY → COMPLETED`，旁支 `REFUNDING / REFUND_AGREED / REFUNDED / ARBITRATING / CLOSED`。
   理由：线下交易没有"物流在途"，状态越多非法迁移组合越多；资金与结算信息放 `biz_settlement`，不塞进订单状态。
2. **防超卖**：`UPDATE biz_listing SET status='LOCKED' WHERE id=? AND status='ON_SALE'`，用影响行数判断，不靠先查后改。
3. **幂等**：`sys_idempotent(request_id, scene)` 唯一键，重复请求返回同一订单（订单 id 存 `result` 字符串列）。
4. **核销码**：买家侧 6 位随机码 + `verify_expire_at`（默认 15 分钟），过期刷新换码；**由卖家核销**并要求上传签到照。
5. **第三方能力 provider 化**：`sms/SmsProvider`（mock / spug）、`realname/RealNameProvider`（mock / 阿里云云市场二要素）、`isbn/IsbnMetaProvider`（mock / 云市场图书库）；ISBN 条码解码用本地 ZXing（不依赖网络）→ 演示走 mock、上线只改配置。
6. **双 DDL 不手写第二份**：`schema-h2.sql` 由 `tools/gen-h2-schema.js` 从 `db/schema.sql` 机械生成，改表只改后者再重跑生成器。
7. **JWT 无状态、不用 Redis**：compose 里没有 redis 容器，别加回来（后端无 redis 依赖）。
8. **前后端分离**：后端不做服务端渲染；Vite 开发期 `/api` 代理到 8088，生产 nginx 去前缀反代。
9. **分页与跨域在框架层兜底**：`common/PageSizeFilter` 把任何 `size>50` 夹到 50（审计发现 31 个分页接口全无上限）；CORS 只认 `app.cors-allowed-origins` 白名单。两者都是"默认安全"，别在业务代码里再放开。
10. **退款幂等靠条件更新**：`UPDATE biz_orders SET status='REFUNDING' WHERE id=? AND status='COMPLETED'`，影响行数 0 即视为重复申请（`40900`）——只靠先查状态挡不住并发。

## 8. 踩坑清单（都是实际踩过的，别再踩）

| 现象 | 原因 / 修法 |
| --- | --- |
| 所有接口 50000 `BadSqlGrammarException` | H2 库文件在仓库根 `data/`，删错目录；或改表后没重跑 `gen-h2-schema.js` |
| `mvn package` 报 `Unable to rename ... .jar.original` | 旧服务还占着 jar，先停 8088 上的 java |
| 中文乱码 / 中文比较永远不等 | PS 5.1 把**无 BOM 的 UTF-8 .ps1** 按 GBK 读；脚本必须 UTF-8 **with BOM**，并设 `[Console]::OutputEncoding` |
| `java -version` / Maven 输出让脚本直接中断 | `$ErrorActionPreference="Stop"` + 原生程序写 stderr → `NativeCommandError`；改 `Continue` 并显式查 `$LASTEXITCODE` |
| 脚本赋值 `$home` / `$args` 报只读 | PowerShell 自动变量，改名（`$sdkPath` / `$javaArgs`） |
| IDEA 里"无法解析 pnpm 软件包管理器" | 本机 pnpm 非标准安装；`.run` 配置里 `<package-manager value="npm" />` 或改 Settings → Node.js → Package manager |
| 前端白屏、`main.js` 首行是 `import ... from 'vue'` | Vite 没转换源码；**停服 → 删 `{web,admin}/node_modules/.vite` → 重启**（运行中删缓存会把旧进程搞坏） |
| `git commit -m` 含 `{}`/引号报 `Invalid path` | 用 `-F 消息文件`；且消息文件用 `[IO.File]::WriteAllText(..., UTF8Encoding($false))` 避免 BOM 前导零宽字符 |
| `python tools/md2docx.py` 生成的 Word 表格错位 | 只支持本项目约定的 markdown 子集，改 md 后重跑即可 |

## 9. 当前状态（详见 docs/09）

- **已交付**：后端 38 表 / 146 个 Java 文件 / 133 个接口、web 35 个视图、admin 页面、`docs/01–13`、Word 版、部署脚本与 ECS 手册、Spug 真实短信、阿里云二要素实名、ISBN 双通道、一键环境配置与 IDEA `.run` 配置。
- **验证基线**：`tools/smoke.ps1` 17/17、`tools/e2e-trade.ps1` 23/23；后端编译与两端 `pnpm build` 均通过。
- **未做**：287 份问卷回收、部署到阿里云 ECS 8.149.233.30 后的线上验证、图片上 OSS、小程序口径收口、微信支付（按 m04487 搁置）。系统测试章节（`docs/12`，53 条用例）、并发压测（`tools/perf`，实测 TPS 1057–1159、20 并发抢单成功 1 单）、四张论文图（`docs/13` + `docs/img`）、图片落盘（`FileController`）、admin 关 mock 均已完成（2026-11-28）。

## 10. 角色与身份口径（2026-11-28 作者确认，改之前先看）

- **只有一个"用户"实体**（`sys_user`），身份分两层：**主身份** `sys_user.role`（`STUDENT / LEADER / MERCHANT / OPERATOR / ADMIN`）+ **附加身份** `sys_user_role`（多对一，一用户可叠加多个）。JWT 里 `role` 是主身份、`roles` 是完整身份串（如 `MERCHANT,OPERATOR`）。
  - **学生 / 商户**：**两类账号，登录页切换**（「学生登录」/「商户登录」），登录方式相同（手机号验证码或账号密码）。商户**不再由进阶码产生**——在商户入口首次短信登录自动建号（`role=MERCHANT`），填店铺资料 → 运营审核通过（`biz_merchant.status=APPROVED`）后开店。学生账号提交入驻会被 `40300` 拒绝；商户账号从学生入口登录会被 `40900` 拒绝，反之亦然。
  - **运营**：**唯一靠进阶码**的身份。学生账号或商户账号都能在「我的 → 进阶运营」兑换运营码（`OPER2026` / `OPER8888` 或管理员生成的码），成功后写入 `sys_user_role`，**主身份不变**——商户进阶后店铺与商户工作台全部保留，只多一个运营身份与运营入口。
  - **团长**：普通用户可添加的身份，由**运营或平台按需赋予**。当前实现里团长=发起接龙的人（`biz_group_buy.leader_id`），`sys_user.role='LEADER'` 只是标识，还没有"运营赋权"接口（要做属于新功能，先问）。
- **权限按能力判定，不按角色硬切**：交易看实名 + 学生认证（`sys_verify_record`）与信用分；商户模块看 `merchantId`（入驻审核通过才有）；运营端看 `UserContext.requireOperator()`（等价于 `hasRole("OPERATOR")`，读 `sys_user_role`）。**不要写"必须是 STUDENT 才能买/卖"这类判断**，也不要为一个用户建多套账号。
- `sys_user.role` 只是"最主要那重身份"的标签，不代表能力全集；判断"能不能"一律用 `LoginUser.hasRole(...)` / `UserRoleService.has(...)`。

### 范围冻结（等作者指令再动）

- `miniapp/`：**保留、不开发、不删除**；论文口径写"旧流程参考实现、非主交付"。
- **接龙拼单**：保留，**暂不优化**（不做新功能、不改交互），等指令。
- **进阶码**：保留，但**只用于运营身份**（`OPERATOR` 单线，运营端/管理员批量生成；商户码已停用）。

## 11. 协作规则（给 AI）

- **先推送，再写代码**（硬规则，2026-11-28 起）：每次动手前先 `git status` 与 `git log --oneline origin/main..HEAD`；有未提交改动先提交，有未推送提交先 `git push`，**本地干净了才开始写**。收尾必须"提交 + 推送"成对完成，不留本地领先远端的提交。
- **严格以任务书为边界**：开发范围以 `docs/11-任务书与选题表对照验证.md` 里那份任务书为准——**11 个功能模块 / 5 类角色（学生·团长·商户·运营·管理员）/ 任务书 37 张表（实现含 `sys_user_role` 共 38 张）**。任务书之外的新模块（支付渠道、社交动态、优惠券、竞价推广位、积分商城之类）**先问再决定**，不许"顺手加功能"。
- **先读后写**：动任何接口前先看对应 `controller` + `service`，别按记忆猜字段名（实体是生成物，以 `db/schema.sql` 为准）。
- **改表流程**：`db/schema.sql` → `node tools/gen-entities.js` → `node tools/gen-h2-schema.js` → 删本地 `data/` → 重启。
- **验证要求**：改后端必须 `.\mvnw.cmd -q -DskipTests compile` 通过；改接口必须跑 `smoke.ps1` / `e2e-trade.ps1`；改前端必须 `pnpm build` 通过。**不许说"应该能跑"**。
- **前端每个可点元素都必须有响应**（硬规则，2026-11-28 起）：开发过程中，页面上**每一个按钮、超链接、可点卡片/图标/单元格**都必须绑定点击行为并真的产生结果（跳页、弹层/抽屉、发起请求、Toast/确认框），**禁止留"占位按钮"或点了没反应的死链接**。
  - 新增交互三选一：①跳路由（`router.push`，目标路径必须在 `web/src/router/index.js` / `admin/src/router/index.js` 里存在）②弹层（`showDialog` / `van-popup` / `el-dialog`，必须能关闭）③调接口（成功与失败都要有反馈：Toast / `ElMessage` / 空态）。
  - 只做展示的元素**不要**写成按钮外观（用 `<span>`/`<div class="tag">`），避免用户误以为可点。
  - 提交前端改动前必须跑 `node tools/check-clicks.mjs`，**必须以"0 个问题"通过**；它静态扫描 `web/src`、`admin/src` 下所有 `.vue`，报告三类问题：A 按钮/链接没有任何点击绑定、B `@click` 调用了未定义的函数、C `router.push` 指向不存在的路由（当前基线：56 个 `.vue` / 208 个可点元素 / 0 问题）。
  - 改了页面结构或路由后，还要跑一次实机检查（后端 8088 + 前端 dev server 在跑）：`node tools/check-clicks.mjs --live`（默认查 web 5174）与 `node tools/check-clicks.mjs --live --app=admin --url=http://localhost:5173`。它会用无头 Edge 逐条打开路由表里的路径，报告**白屏 / 控制台异常 / 已登录却被踢回登录页**；当前基线：web 28 条、admin 13 条全部正常。
  - 关键流程（登录、下单、支付、核销、退款、商户上下架、运营兑换）的按钮，除静态检查外还要用无头 Edge 真的点一遍并读 DOM 结果：点完必须读一个可核对的结果（`.van-toast` 文案 / `location.hash` / 列表条数 / 状态标签），不能只看"页面能打开"。登录和页面地址可用 `--account` / `--password` / `--url` 指定。2026-11-28 实测通过：订单页「售后」筛选（5→4 张卡）、我的发布「下架」确认弹窗 + Toast「已下架」、商户工作台指标格跳 `/merchant/orders`、查看店铺主页跳 `/shop/1`、商户底部导航 5 个 tab 跳结算页，全程 0 控制台异常。
- **提交**：一个主题一次提交，中文说明写清"做了什么/为什么"；`git add` 具体路径，别 `git add -A` 扫进 `.vite`、`dist`、`data`。
- **凭证**：Spug 模板编码、JWT 密钥、数据库密码一律不进仓库（`**/application-*-local.yml` 已忽略）。
- **不确定就问**：涉及产品口径（要不要小程序、要不要接支付）先问，不擅自扩展范围。
- **网络事实**：本机 GitHub **HTTPS 出网被拦**（`Failed to connect to github.com:443`），remote 已改为 SSH `git@github.com:gxhyj666-png/second-hand_bookstore.git`（复用 `~/.ssh/id_ed25519`，`ssh -T git@github.com` 返回 "Hi gxhyj666-png!" 即通）。push 走 SSH，别改回 HTTPS。
