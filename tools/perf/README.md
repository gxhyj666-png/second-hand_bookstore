# 性能压测（对应任务书"性能测试"一节）

两条路：**不装 JMeter 也能跑**（仓库自带两个 PowerShell 脚本），或者用 JMeter GUI 自己搭。
结果回填到 `docs/12-系统测试用例与报告.md` 的 5.3 节。

## 0. 准备

```powershell
# 1) 干净数据（可选，但建议：避免上次跑脏的数据影响对比）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1

# 2) 取买家 token（dev 环境验证码随响应返回，不用真收短信）
$b = "http://127.0.0.1:8088"
$c = Invoke-RestMethod "$b/v1/auth/sms-code" -Method Post -ContentType 'application/json' -Body '{"phone":"13800000002"}'
$login = Invoke-RestMethod "$b/v1/auth/sms-login" -Method Post -ContentType 'application/json' -Body (@{ phone='13800000002'; code=$c.data.devCode } | ConvertTo-Json)
$token = $login.data.token
```

> 注意：dev 环境短信频控 3 秒/号，脚本里 get token 只做一次就够。

## 1. 读接口并发（TC-P01 / TC-P03）

```powershell
# 关键词检索：20 线程 × 50 次
powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\load-list.ps1 -Scenario list -Threads 20 -Iterations 50

# 推荐列表
powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\load-list.ps1 -Scenario recommend -Threads 20 -Iterations 50

# 商品详情（ListingId 换成实际在售商品）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\load-list.ps1 -Scenario detail -Threads 20 -Iterations 50 -ListingId 32
```

输出含：总请求 / 成功 / 失败 / 墙钟耗时 / **TPS** / 平均 RT / **P95** / 最大 RT / 错误率；有失败时退出码 1。

脚本用 `runspace` 池并发（**PowerShell 5.1 也能跑**，不依赖 PS7 的 `ForEach-Object -Parallel`）。

## 2. 并发抢单（TC-P02，最能说明问题的一条）

```powershell
# 先造一本"卖家 3 的在售书"（买家不能买自己的商品）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\concurrent-order.ps1 -Token $token -ListingId 33 -Concurrency 20
```

判定逻辑写死在脚本里：**成功下单 ≤ 1 才 PASS**，多于 1 直接退出码 1。
失败分布会按「code + 文案」分组打印，方便你写论文时解释两条拦截路径：

- `商品已下架或已被预订` —— 下单前的状态预检；
- `商品已被其他人抢先下单` —— 条件更新 `WHERE status='ON_SALE'` 影响行数为 0。

跑完脚本会追加查询商品状态，期望 `LOCKED`。

## 3. 索引效果对比（TC-P04）

在 H2 控制台（`http://127.0.0.1:8088/h2-console`，JDBC URL `jdbc:h2:file:./data/textbook_loop`，用户 `sa`，密码空）或 MySQL 里：

```sql
-- 看当前索引
SHOW INDEX FROM biz_listing;
-- 删掉一个查询会用到的索引（H2/MySQL 名字不同，按 SHOW INDEX 的结果来）
DROP INDEX idx_listing_status ON biz_listing;   -- 示例
-- 重跑同一场景，记录 P95 变化；再建回来
CREATE INDEX idx_listing_status ON biz_listing(status);
```

论文里写清："同一 SQL、同一数据量，仅索引不同，P95 从 X ms 变为 Y ms"，比空谈"加了索引"有说服力。

## 4. JMeter 方案（可选，喜欢图形界面的话）

1. 新建 Thread Group：`Threads = 50`、`Ramp-up = 5`、`Loop Count = 200`。
2. 加 HTTP Request：`localhost` / `8088` / `GET` / `/v1/listings`，参 `keyword=数学`、`size=20`。
3. 需要登录的接口加 HTTP Header Manager：`Authorization: Bearer ${token}`，token 用「用户定义的变量」传入。
4. 加 **Response Assertion**：`响应文本` 包含 `"code":0`（避免 200 但业务失败）。
5. 加 **Aggregate Report**，命令行执行：

```powershell
jmeter -n -t perf.jmx -l result.jtl -e -o report
```

## 5. 记录表模板（论文直接用）

| 场景 | 并发 | 总请求 | 成功 | 失败 | TPS | 平均 RT | P95 | 最大 RT |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 检索列表 | 20 | 1000 | 1000 | 0 | 1065 | 15.9 ms | 42 ms | 102 ms |
| 推荐列表 | 20 | 1000 | 1000 | 0 | 1057 | 14.0 ms | 65 ms | 159 ms |
| 商品详情 | 20 | 1000 | 1000 | 0 | 1159 | 13.4 ms | 45 ms | 95 ms |
| 并发抢单 | 20 | 20 | 1 | 19 | — | 最长 70 ms | — | — |

> 上面是本机（Windows + JDK 17 + H2 文件库 + `-Xmx512m`）的实测值。换到 ECS（MySQL 8 + 2C4G）重跑后替换即可，并在论文里注明测试环境。

## 6. 可能的坑

| 现象 | 原因 / 处理 |
| --- | --- |
| 抢单脚本报 40300「只能购买他人发布的商品」 | 用了卖家自己的账号下单；换买家 token，或让卖家 3 发布商品 |
| 一开始就大量 40900「发送过于频繁」 | 反复取 token 触发短信频控（dev 3 秒/号），token 只取一次并复用 |
| 并发拉高后接口 500 | 先看后端控制台是否 `OutOfMemoryError`（`-Xmx512m`），或 H2 文件锁；把并发降到 20–30 再测 |
| TPS 异常高（> 5000） | 请求打到了缓存/代理而不是后端，确认 `BaseUrl` 是 `http://127.0.0.1:8088` 且 `/v1/health` 有响应 |
