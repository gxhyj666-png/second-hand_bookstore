# 同窗书 · 校园教材循环平台

> 基于 Spring Boot 3 + Vue 3 的校园二手教材循环平台
> —— 要解决的一句话问题是：**"老师说的到底是哪一版，我手上这本书有没有人要？"**
>
> 形态：**用户端响应式 Web（H5，可封装 APK）** + 管理后台 + 服务端；小程序代码保留作参考实现。

![status](https://img.shields.io/badge/build-passing-brightgreen)
![java](https://img.shields.io/badge/Java-17-blue)
![spring](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F)
![mysql](https://img.shields.io/badge/MySQL-8.0-4479A1)
![license](https://img.shields.io/badge/license-MIT-black)

## 这是什么

一套完整的校园教材循环系统，围绕「**课程 → 指定教材 → 二手书供给 → 担保交易 → 自提核销**」的主链路，
把校园里零散的"学长学姐卖书 / 班委统一收书"变成可检索、可信任、可结算的闭环。

三端三角色：

| 端 | 使用者 | 目录 | 技术 |
| --- | --- | --- | --- |
| **用户端 Web（H5）** | 学生（买家/卖家/团长） | `web/` | Vue 3 + Vite + Vant 4，响应式，可封装 APK |
| 管理后台 | 运营/教务处 | `admin/` | Vue 3 + Vite + Element Plus + ECharts |
| 服务端 | —— | `server/` | Spring Boot 3.2.5 + MyBatis-Plus + JWT |
| 小程序（参考） | 学生 | `miniapp/` | 原生微信小程序，保留作探索记录，非主交付 |

## 八个模块

1. **用户与认证**：手机号注册 / 微信一键登录（`/v1/auth/wechat`，dev 用 code 模拟）、JWT、RBAC 四角色
   （STUDENT / LEADER 团长 / CLERK 自提点店员 / ADMIN）、学号实名走"提交 → PENDING → 审核"。
2. **课程—教材知识库**：课程库 + 教材主数据（ISBN-13 维度，一版一条）+ 课程教材映射（REQUIRED/REFERENCE），
   从根上解决"第几版"的歧义。
3. **商品发布与品相**：选教材 → 定价 → 五档品相（NEW/LIKE_NEW/GOOD/FAIR/POOR）→ 多图，
   并给出**同教材成交价指数**（最低/最高/均价/建议价），让定价有据可依。
4. **检索匹配与推荐**：按课程/教材/关键词/品相检索，四种排序（最新、价格升、价格降、热度）；
   推荐走"收藏 + 成交过的同课程"召回，冷启动兜底热门。
5. **订单与担保交易**：`PENDING_PAY → FROZEN → WAIT_VERIFY → VERIFIED → SETTLED` 状态机，
   下单即锁商品（条件更新防超卖）、幂等表防重复提交、15 分钟未支付自动关单（`@Scheduled`）、退款释放库存。
6. **自提点与核销**：自提点带经纬度（Haversine 算距离）、货位容量；
   线下交付用 **HMAC-SHA256 动态核销码**（30 秒轮换，接受当前与上一窗口），核销落 `biz_pickup_verify`。
7. **信用评价与纠纷工单**：买卖双向评价（4-5 星 +3 分，3 星 0 分，1-2 星 -3 分，流水可追溯）；
   纠纷走工单，用户/管理员多轮留言，处理结果回写并通知。
8. **班级接龙 + 运营看板**：团长按课程发起接龙（目标册数/预期单价/6 位邀请码），同学凭码登记需求，
   满额自动置 DONE；后台看板有 7 日成交趋势、转化漏斗（曝光→详情→发布→下单→支付→核销）、品相分布。

## 快速开始

### 方式一：零依赖跑后端（推荐，答辩演示用）

后端自带 **H2 文件库** profile，不需要装 MySQL / Redis：

```bash
cd server
# 需要 JDK 17+ 与 Maven 3.8+
mvn spring-boot:run
# 或打包后运行
mvn -DskipTests package && java -jar target/textbook-loop-server-0.1.0.jar
```

- 服务地址：<http://127.0.0.1:8088>
- 健康检查：<http://127.0.0.1:8088/v1/health>
- H2 控制台：<http://127.0.0.1:8088/h2-console>（JDBC URL 见 `application-dev.yml`，用户 `sa`，空密码）
- 首次启动自动建表并灌入示例数据（演示账号见下表）。

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
cd admin && pnpm install && pnpm dev     # http://localhost:5173，默认 mock 数据
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
├── db/
│   ├── schema.sql          # MySQL 8 正式 DDL（33 张表，说明见 docs/02-数据库设计.md）
│   └── seed.sql            # 纯 SQL 示例数据（后端已有 DataInitializer，二选一）
├── docs/
│   ├── 01-项目模块介绍.md   # 模块划分 / 功能点 / 技术难点
│   ├── 02-数据库设计.md     # 表结构、字段、索引与设计取舍
│   ├── 03-问卷设计与统计口径.md
│   ├── 04-调研实施计划.md
│   ├── 06-终端形态说明.md   # 为什么先做响应式 Web、APK 怎么出
│   ├── 07-需求对齐与改造清单.md  # 打磨需求逐条对照与差距
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
├── tools/                  # 冒烟测试、H2 DDL 生成、小程序结构自检
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
`/v1/reviews`、`/v1/tickets/*`、`/v1/group-buys/*`、`/v1/pickup-points/*`、`/v1/notices/*`、`/v1/admin/*`。

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

- **短信、身份证核验、人脸识别、ISBN 查询当前都是 mock**（`app.mock-sms` / `app.mock-identity` 开关），换真实供应商只改 `SmsService` / `IdentityService` / `IsbnService`，接口契约不变；支付同为 mock。
- 推荐是规则召回（同课程 + 收藏共现），不是模型排序；数据量上来后可替换为 ItemCF。
- 管理后台默认走 mock，接真后端需改 `admin/.env`。
- 用户端 Web 的图片只接受 URL（后端不落盘），打包 APK 时须把 `VITE_API_BASE` 指向公网后端并配 HTTPS。
- 小程序端仍是旧版「店员核销」流程，定位为参考实现；主线交易流程在响应式 Web。
- 爽约扣分（约定时间 +30 分钟才可上报）逻辑已实现，但自动化脚本未覆盖该等待分支。

## License

MIT
