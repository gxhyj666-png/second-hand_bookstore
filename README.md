# 同窗书 · 校园教材循环平台

> 基于 Spring Boot 3 + Vue 3 的校园二手教材循环平台
> —— 要解决的一句话问题是：**"老师说的到底是哪一版，我手上这本书有没有人要？"**
>
> 形态：**用户端响应式 Web（H5，可封装 APK）** + 管理后台 + 服务端；小程序代码保留作参考实现。
>
> 端：**个体端**（自由买卖）→ **商户端**（进阶码升级，批量上下架）/ **运营端**（进阶码升级，推广二维码、下线业绩、提成、投诉与线下认证）→ **平台端**（管理后台）。
>
> **接手 / 换会话 / 新机器**：先读 [`AGENTS.md`](AGENTS.md)（项目宪法：命令、环境、决策、踩坑）与 [`docs/09-项目状态与交接.md`](docs/09-项目状态与交接.md)（当前进度、验证基线、未完成清单）；新会话开场白见 [`docs/10-新会话开场提示.md`](docs/10-新会话开场提示.md)。

![status](https://img.shields.io/badge/build-passing-brightgreen)
![java](https://img.shields.io/badge/Java-17-blue)
![spring](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F)
![mysql](https://img.shields.io/badge/MySQL-8.0-4479A1)
![license](https://img.shields.io/badge/license-MIT-black)

## 这是什么

一套完整的校园教材循环系统，围绕「**课程 → 指定教材 → 二手书供给 → 线下约见核销 → 纠纷仲裁**」的主链路，
把校园里零散的"学长学姐卖书 / 班委统一收书 / 小店批量卖书"变成可检索、可信任、可结算的闭环。

三端三形态：

| 端 | 使用者 | 目录 | 技术 |
| --- | --- | --- | --- |
| **个体端 + 商户端 + 运营端（用户端 Web/H5）** | 学生（买家/卖家/团长）、商户、运营 | `web/` | Vue 3 + Vite + Vant 4，响应式，可封装 APK |
| **平台端（管理后台）** | 运营/教务处 | `admin/` | Vue 3 + Vite + Element Plus + ECharts |
| 服务端 | —— | `server/` | Spring Boot 3.2.5 + MyBatis-Plus + JWT |
| 小程序（参考） | 学生 | `miniapp/` | 原生微信小程序，保留作探索记录，非主交付 |

## 十三个模块

1. **用户与认证**：手机号**短信验证码登录**（`/v1/auth/sms-code` + `/v1/auth/sms-login`，未注册自动建号，
   dev 直接回传验证码）、微信一键登录（dev 用 code 模拟）、JWT（7 天免登录）、五角色
   （STUDENT / LEADER 团长 / MERCHANT 商户 / OPERATOR 运营 / ADMIN）；密码在个人中心设置，之后可用「手机号 + 密码」登录。
2. **实名 + 学生双认证**：实名认证（身份证正反面 + 三方核验）、学生认证（学生证封面 + 信息页）、
   人脸识别留痕；**交易与私信前置校验**，未双认证只能浏览。
3. **课程—教材知识库**：课程库 + 教材主数据（ISBN-13 维度，一版一条）+ 课程教材映射（REQUIRED/REFERENCE），
   并支持 **扫码/输入 ISBN 自动带出书名、作者、出版社、版次、参考价**（`/v1/isbn/{isbn}`），从根上解决"第几版"歧义。
4. **商品发布与品相**：选教材/ISBN → 定价 → 五档品相（NEW/LIKE_NEW/GOOD/FAIR/POOR）→ **7 张实拍照片**
   （封面、封底、出版日期页、四页正文，`/v1/listings/{id}/photos`）+ **可选展示视频**，并给出**同教材成交价指数**
   （最低/最高/均价/建议价），让定价有据可依。
5. **检索匹配与推荐**：按课程/教材/关键词/品相检索，四种排序（最新、价格升、价格降、热度）；
   游客可浏览与搜索，推荐走"收藏 + 成交过的同课程"召回，冷启动兜底热门。
6. **订单与担保交易**：主干三态 `PENDING_PAY → WAIT_VERIFY → COMPLETED`，旁支
   `REFUNDING / REFUND_AGREED / REFUNDED / ARBITRATING / CLOSED`；**待支付仅维持 5 分钟**，超时自动关单（`@Scheduled`）；
   支持买家取消、卖家取消、付款后取消（`close_type` 记录 TIMEOUT/BUYER_CANCEL/SELLER_CANCEL/PAID_CANCEL，
   责任方按规则扣 3 分）；下单即锁商品（条件更新防超卖）、幂等表防重复提交。
7. **约见与线下核销**：买卖双方在会话里发起**交易地点 + 时间**并确认；买家支付后可出示 **6 位核销码**
   （生成后 15 分钟有效，且**不晚于约定交易时间后 15 分钟**；失效后由**卖家重新生成**，旧码立即作废），
   线下由**卖家**核销并上传交易签到照片，核销落 `biz_pickup_verify`、结算落 `biz_settlement`。
8. **退款与平台仲裁**：到手不符可申请 **退货退款** 或 **仅退款**（退货同样要确认地点时间）→ 卖家同意/拒绝 →
   买家寄回并签到 → 原路退款；协商不成**申请平台介入**，平台判定卖家有问题则强制退款（卖家 -5 分）、
   判定买家责任则终止退货（买家 -5 分）。
9. **信用与爽约惩戒**：买卖双向评价（4-5 星 +3 分，3 星 0 分，1-2 星 -3 分，流水可追溯）；
   约定时间后 30 分钟未到且未沟通，对方可上报爽约并**扣 5 信誉分**；纠纷工单多轮留言可追溯。
10. **买卖私信**：按商品发起会话，文本 + 约见/退款/签到卡片消息，未读计数随会话列表返回。
11. **商户端与班级接龙 + 平台看板**：个体用户凭**商户进阶码**（`/v1/upgrade/redeem`）直接升级入驻，管理员也可走人工审核；
    **批量上下架**（单批 ≤100 条）与经营统计；团长按课程发起接龙（目标册数/预期单价/6 位邀请码），满额自动置 DONE；
    后台看板有 7 日成交趋势、转化漏斗（曝光→详情→发布→下单→支付→核销）、品相分布、GMV。
12. **运营端**：个体用户凭**运营进阶码**升级为 OPERATOR，获得专属推广码与推广链接 → 下线绑定（`/v1/promotion/bind`）→
    下线交易数据、**提成明细**（核销后按成交额 1% 计提，退款自动作废）、**投诉处理**、**线下认证与核实**（`/v1/operator/*`）。
13. **存储接口**：图片与视频统一上传（`POST /v1/files/upload`，本地 `uploads/` 按月分目录，30MB 上限），
    返回可直接访问的 URL，供商品七图、展示视频、认证材料与签到照使用；换成对象存储只需替换该实现。

## 快速开始

### 方式零：一条命令配好环境（推荐，尤其给新机器 / 别人拷过去）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1
```

脚本会自动：找 JDK 17 → 用自带 **Maven Wrapper**（无需装 Maven）编译后端 → `pnpm install` 装前端依赖 →
写好 IDEA 的 Project SDK/编码/注解处理 → 生成 `admin/.env` 与 `application-spug-local.yml` 模板。
配完在 IDEA 里 `File > Open` 选仓库根目录、点 **Load Maven Project**，运行配置下拉里直接选
`Backend dev (H2)` / `Web dev (Vite)` / `Admin dev (Vite)` / `Dev all`。详见 [`docs/08-本地环境与IDEA配置.md`](docs/08-本地环境与IDEA配置.md)。

### 方式一：零依赖跑后端（推荐，答辩演示用）

后端自带 **H2 文件库** profile，不需要装 MySQL / Redis：

```bash
# 仓库根目录执行（自带 Maven Wrapper，不需要预装 Maven）
.\mvnw.cmd -DskipTests package
java -jar server\target\textbook-loop-server-0.1.0.jar --spring.profiles.active=dev

# macOS / Linux
./mvnw -DskipTests package && java -jar server/target/textbook-loop-server-0.1.0.jar --spring.profiles.active=dev
```

需要 **JDK 17**；Maven 由 `mvnw` 首次运行时自动下载 3.9.9，不必单独安装。

- 服务地址：<http://127.0.0.1:8088>
- 健康检查：<http://127.0.0.1:8088/v1/health>
- H2 控制台：<http://127.0.0.1:8088/h2-console>（JDBC URL 见 `application-dev.yml`，用户 `sa`，空密码）
- 首次启动自动建表并灌入示例数据（演示账号见下表）。
- 想**回到干净数据**（演示前重置）：`powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1`
  —— 它会停掉 8088 上的后端、删掉仓库根的 `data/`、重新启动并等健康检查通过。
  注意 `application-dev.yml` 里的 H2 是相对路径 `./data`，**相对进程工作目录**，所以必须在仓库根启动。

### 方式二：MySQL + Redis + 全部服务

```bash
docker compose up -d --build
# 用户端 http://127.0.0.1/          （web，80）
# 管理后台 http://127.0.0.1:8080   （admin，8080）
# 后端 http://127.0.0.1:8088
```

`db/schema.sql` 会被 MySQL 容器自动执行；手工初始化：

```bash
mysql -uroot -p < db/schema.sql
```

### 前端

```bash
# 用户端 Web（平台主入口）
cd web && pnpm install && pnpm dev       # http://localhost:5174，/api 代理到 8088

# 管理后台
cd admin && pnpm install && pnpm dev     # http://localhost:5173，默认直连 8088 真后端
# 接真后端：admin/.env 里把 VITE_USE_MOCK 设为 false

# 小程序（参考实现，非主交付）
# 微信开发者工具导入 miniapp/，详情里勾选"不校验合法域名"
# miniapp/utils/config.js 的 USE_MOCK = true 时不依赖后端即可点通全流程
```

## 演示账号

| 手机号 | 密码（兼容登录） | 角色 | 能做什么 |
| --- | --- | --- | --- |
| `13800000001` | `admin123` | ADMIN 管理员 | 后台看板、商户审核、平台介入判定、冻结用户 |
| `13800000002` | `123456` | STUDENT 张同学 | 浏览、下单、出示核销码、申请退款、私信卖家 |
| `13800000003` | `123456` | STUDENT 李同学 | 发布商品（示例商品卖家）、核销、同意/拒绝退款 |
| `13800000004` | `123456` | STUDENT 王同学 | 普通买家，用于制造交易对手 |
| `13800000005` | `123456` | LEADER 陈班长 | 发起班级接龙（示例接龙码 `JY2026`） |
| `13800000006` | `123456` | OPERATOR 赵运营 | 运营中心：推广码 `YUN001`、下线业绩、提成、投诉与线下认证 |

> 进阶码演示：种子数据内置 **`SHOP2026`**（商户）与 **`OPER2026`**（运营），
> 登录任一普通账号后进入「我的 → 进阶商户 / 运营」即可兑换；管理员也可在后台「进阶码管理」批量生成。

> 本版按打磨要求把**登录统一改成短信验证码**：
> `POST /v1/auth/sms-code {"phone":"13800000002"}` → 返回 `{devCode}`（dev 的 `app.mock-sms=true` 不做真实发送，
> 验证码随响应回传，方便演示）→ `POST /v1/auth/sms-login {"phone":"13800000002","code":"<devCode>"}`。
> 账号密码登录 `POST /v1/auth/login {"account":"13800000002","password":"123456"}` 仍保留，后台与脚本兜底用。
> 演示用户已在种子数据里置为「实名 + 学生」双认证通过，否则下单会被交易闸门挡住。

## 主流程演示（一条命令跑完）

```powershell
# 1) 主链路（短信登录 → 发布含 7 张照片 → 下单 → 支付 → 取核销码 → 约见 → 卖家核销 → 双边评价 → 信用分）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke.ps1

# 2) 打磨链路（ISBN、认证、退款两条分支、平台介入、私信、商户入驻与批量上架）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\e2e-trade.ps1

# macOS / Linux
bash tools/smoke.sh
```

`smoke.ps1` 走 **17 个断言（实测 17 通过 / 0 失败）**：健康检查 → 三个角色短信登录 → 游客检索 →
详情与价格指数 → 公开推荐 → 买家发布商品并上传 7 张照片 → 下单（幂等）→ 支付 → 买家出示 6 位核销码（15 分钟）→
卖家发起约见、买家确认 → 卖家核销并上传签到照 → 双边评价 → 信用分与信用记录 → 未读消息 → 管理员看板与商户列表。
**这是答辩最值得演示的一条链路。**

`e2e-trade.ps1` 走 **21 个断言（实测 21 通过 / 0 失败）**，覆盖打磨清单里的纠纷分支：
ISBN 查询（命中/未命中）→ 实名学生认证状态 → 下单并完成 → 申请退款 → 卖家同意 → 买家确认退货 → 订单退款；
第二笔走「卖家拒绝退款 → 买家申请平台介入 → 管理员判定卖家责任 → 强制退款」；再验证私信收发与商户入驻审核、批量上架。
两个脚本都要求后端已在 `http://127.0.0.1:8088` 运行。

## 目录结构

```
second-hand_bookstore/
├── pom.xml                 # 聚合 POM：让 IDE 把仓库识别成 Maven 工程（唯一子模块 server）
├── mvnw / mvnw.cmd         # Maven Wrapper（无需预装 Maven，首次自动下载 3.9.9）
├── .run/                   # IDEA 共享运行配置（后端 dev / 后端 mysql / Web / Admin / 一键全启）
├── db/
│   ├── schema.sql          # MySQL 8 正式 DDL（37 张表，说明见 docs/02-数据库设计.md）
│   └── seed.sql            # 纯 SQL 示例数据（后端已有 DataInitializer，二选一）
├── docs/
│   ├── 01-项目模块介绍.md   # 模块划分 / 功能点 / 技术难点
│   ├── 02-数据库设计.md     # 表结构、字段、索引与设计取舍
│   ├── 03-问卷设计与统计口径.md
│   ├── 04-调研实施计划.md
│   ├── 06-终端形态说明.md   # 为什么先做响应式 Web、APK 怎么出
│   ├── 07-需求对齐与改造清单.md  # 打磨需求逐条对照与差距
│   ├── 08-本地环境与IDEA配置.md  # 一键脚本 / SDK / 运行配置 / 排错
│   └── word/               # 上述四份文档的 Word 版（交作业/答辩直接打印）
├── server/                 # Spring Boot 后端
│   └── src/main/java/com/textbook/loop/
│       ├── controller/     # REST 控制器
│       ├── service/        # 业务逻辑（订单状态机、核销、接龙、评价…）
│       ├── domain/         # 33 个实体（与 db/schema.sql 一一对应）
│       ├── mapper/         # MyBatis-Plus Mapper
│       ├── security/       # JWT、登录上下文、拦截器
│       ├── bootstrap/      # 首次启动灌示例数据
│       └── common/         # 统一响应体、分页、异常处理
├── admin/                  # Vue 3 管理后台（Element Plus）
├── web/                    # Vue 3 用户端 Web / H5（Vant，可封装 APK）
├── miniapp/                # 微信小程序（参考实现，非主交付）
├── tools/                  # setup-dev（一键环境）、smoke / e2e-trade（冒烟）、reset-dev、生成器
└── docker-compose.yml
```

## 接口约定

统一前缀 `/v1`，统一响应体：

```json
{ "code": 0, "message": "ok", "data": {} }
```

错误码：`40000` 参数错误 / `40100` 未登录 / `40300` 无权限 / `40400` 不存在 / `40900` 状态冲突。
分页：`{ "records": [], "total": 0, "page": 1, "size": 20 }`。

接口分组：`/v1/auth/*`、`/v1/courses|textbooks|recommend`、`/v1/listings/*`、`/v1/orders/*`、
`/v1/reviews`、`/v1/tickets/*`、`/v1/group-buys/*`、`/v1/pickup-points/*`、`/v1/notices/*`、
`/v1/files/*`、`/v1/upgrade/*`、`/v1/operator/*`、`/v1/promotion/*`、`/v1/admin/*`。

## 设计上的几个取舍

- **为什么既有 H2 又有 MySQL DDL？** 答辩现场装 MySQL 风险高；H2 用 `MODE=MySQL`，DDL 由
  `tools/gen-h2-schema.js` 从 `db/schema.sql` 机械生成，两边结构永远一致，而不是维护两份手写脚本。
- **为什么核销码是动态的？** 静态二维码容易被截图转发，线下交付必须"当面出示、当场校验"；
  用 HMAC-SHA256 以 30 秒为窗口生成，容忍一个窗口的时钟漂移。
- **为什么下单要幂等表？** 学生网络差时容易连点，重复请求应返回同一张订单而不是报错；
  `sys_idempotent` 记录 requestId → orderNo，第二个请求直接读回结果。
- **为什么资金是 FROZEN 而不是直接打款？** 平台不碰资金池：下单冻结、核销后结算给卖家，
  退款时释放，符合"担保交易"的口径。

- **为什么先做响应式 Web 而不是继续做小程序？** 小程序要 AppID、真机预览与审核，验证成本高；响应式 Web 一套代码同时覆盖 PC 与手机，答辩演示只需要一个浏览器。将来要 APK 时用 Capacitor 套壳（`web/capacitor.config.ts` 已预留），改的只有构建配置，页面代码不动；要小程序时也能复用同一套 HTTP 接口重做 UI。

## 已知限制

- **短信通道可切真实供应商**：默认 `app.sms-provider=mock`（验证码不真发、随接口回传，冒烟脚本与离线演示用）；
  置 `spug` + `app.spug-template-code` 即接 **Spug 推送助手官方短信验证码模板**（一次 `POST /sms/<模板编码>`，
  无需申请短信签名）。真实模式不再回传 `devCode`。身份证核验、人脸识别、ISBN 查询仍是 mock
  （`app.mock-identity` 开关）。
- 推荐是规则召回（同课程 + 收藏共现），不是模型排序；数据量上来后可替换为 ItemCF。
- 管理后台已默认直连真后端（`admin/.env` 的 `VITE_USE_MOCK=false`），mock 数据仅保留作离线预览备用。
- 图片与视频上传落本地 `uploads/`（`app.upload-dir` 可改），生产建议换成对象存储；打包 APK 时须把
  `VITE_API_BASE` 指向公网后端并配 HTTPS，同时保证 `/v1/files/*` 可公网访问。
- 小程序端仍是旧版「店员核销」流程，定位为参考实现；主线交易流程在响应式 Web。
- 爽约扣分（约定时间 +30 分钟才可上报）逻辑已实现，但自动化脚本未覆盖该等待分支。

## License

MIT
