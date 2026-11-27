# 同窗书 · 后端冒烟测试（PowerShell 版）
# 用法：后端已在 8088 端口运行时执行  pwsh tools/smoke.ps1
# 作用：把答辩最该演示的主链路跑一遍：登录 → 选书 → 下单 → 支付冻结 → 店员核销 → 结算。

$ErrorActionPreference = 'Stop'
$base = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://127.0.0.1:8088' }
$pass = 0
$fail = 0

function Step($name, $script) {
    try {
        & $script
        Write-Host ("  [通过] " + $name) -ForegroundColor Green
        $global:pass++
    } catch {
        Write-Host ("  [失败] " + $name + "：" + $_.Exception.Message) -ForegroundColor Red
        $global:fail++
    }
}

function Api($method, $path, $token, $body) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$base$path"; Headers = $headers; ContentType = 'application/json' }
    if ($body -ne $null) { $params['Body'] = ($body | ConvertTo-Json -Depth 6) }
    $resp = Invoke-RestMethod @params
    if ($resp.code -ne 0) { throw "$path -> code=$($resp.code) $($resp.message)" }
    return $resp.data
}

Write-Host "`n== 同窗书后端冒烟测试 @ $base ==`n" -ForegroundColor Cyan

Step '健康检查' {
    $h = Api GET '/v1/health'
    if ($h.status -ne 'UP') { throw "status=$($h.status)" }
}

$buyer = $null
Step '买家登录（13800000002）' {
    $global:buyer = Api POST '/v1/auth/login' $null @{ account = '13800000002'; password = '123456' }
    if (-not $global:buyer.token) { throw '没有拿到 token' }
}

$clerk = $null
Step '店员登录（13800000004）' {
    $global:clerk = Api POST '/v1/auth/login' $null @{ account = '13800000004'; password = '123456' }
}

Step '管理员登录并读取看板' {
    $admin = Api POST '/v1/auth/login' $null @{ account = '13800000001'; password = 'admin123' }
    $dash = Api GET '/v1/admin/dashboard' $admin.token
    if ($dash.userCount -lt 5) { throw "userCount=$($dash.userCount)" }
    Write-Host ("      用户 $($dash.userCount) / 在售 $($dash.onSaleCount) / GMV $($dash.gmv) / 待处理工单 $($dash.pendingTickets)")
}

$listing = $null
Step '课程→教材链路' {
    $courses = Api GET '/v1/courses?size=5' $buyer.token
    if ($courses.total -lt 1) { throw '课程库为空' }
    $books = Api GET '/v1/textbooks?size=5' $buyer.token
    if ($books.total -lt 1) { throw '教材库为空' }
}

Step '检索在售商品' {
    $list = Api GET '/v1/listings?size=5&sort=newest' $buyer.token
    if ($list.total -lt 1) { throw '没有在售商品，请确认示例数据已灌入' }
    $global:listing = $list.records[0]
    Write-Host ("      选中：" + $global:listing.title + "  ￥" + $global:listing.price)
}

Step '商品详情与价格指数' {
    $detail = Api GET "/v1/listings/$($global:listing.id)" $buyer.token
    if (-not $detail.listing) { throw '详情为空' }
    $idx = Api GET "/v1/textbooks/$($global:listing.textbookId)/price-index" $buyer.token
    Write-Host ("      同类成交价：最低 $($idx.min) / 均价 $($idx.avg) / 建议 $($idx.suggest)")
}

Step '推荐列表' {
    # 推荐接口对登录用户会排除自己发布的商品；种子数据里演示买家同时也是卖家，
    # 商品被买光后个性化推荐为空是正常业务语义，因此公开推荐必须可用、个性化只做提示。
    $rec = Api GET '/v1/recommend?limit=5'
    if ($rec.Count -lt 1) { throw '公开推荐为空' }
    $mine = Api GET '/v1/recommend?limit=5' $buyer.token
    Write-Host ("      公开推荐 $($rec.Count) 条 / 个性化 $($mine.Count) 条")
}

$order = $null
Step '下单（幂等 requestId）' {
    $points = Api GET '/v1/pickup-points?schoolId=1' $buyer.token
    if ($points.Count -lt 1) { throw '没有自提点' }
    $rid = [guid]::NewGuid().ToString()
    $global:order = Api POST '/v1/orders' $buyer.token @{
        listingId = $global:listing.id
        pointId   = $points[0].id
        requestId = $rid
    }
    # 同一个 requestId 再提交一次，应返回同一张订单
    $again = Api POST '/v1/orders' $buyer.token @{
        listingId = $global:listing.id
        pointId   = $points[0].id
        requestId = $rid
    }
    if ($again.orderNo -ne $global:order.orderNo) { throw '幂等失效：两次返回不同订单' }
    Write-Host ("      订单号：$($global:order.orderNo)  状态：$($global:order.status)")
}

Step '支付（资金冻结）' {
    $paid = Api POST "/v1/orders/$($global:order.orderNo)/pay" $buyer.token @{ channel = 'MOCK' }
    if ($paid.status -ne 'FROZEN') { throw "status=$($paid.status)" }
}

Step '等待核销' {
    $wait = Api POST "/v1/orders/$($global:order.orderNo)/wait-verify" $buyer.token
    if ($wait.status -ne 'WAIT_VERIFY') { throw "status=$($wait.status)" }
}

Step '获取动态核销码' {
    $code = Api GET "/v1/orders/$($global:order.orderNo)/verify-code" $buyer.token
    if (-not $code.code) { throw '没有核销码' }
    $global:code = $code.code
}

Step '店员核销' {
    $done = Api POST '/v1/orders/verify' $clerk.token @{
        orderNo = $global:order.orderNo
        code    = $global:code
        method  = 'SCAN'
    }
    if ($done.status -ne 'SETTLED') { throw "status=$($done.status)" }
}

Step '核销后信用分与结算' {
    $me = Api GET '/v1/auth/me' $buyer.token
    if ($me.creditScore -lt 100) { throw "creditScore=$($me.creditScore)" }
    $orders = Api GET '/v1/orders?role=buyer' $buyer.token
    if ($orders.total -lt 1) { throw '订单列表为空' }
}

Write-Host "`n== 结果：通过 $pass，失败 $fail ==`n" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
