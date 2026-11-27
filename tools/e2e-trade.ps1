# 同窗书 · 打磨后交易链路 E2E（退款/平台介入/私信/商户/认证/ISBN）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\e2e-trade.ps1
# 前置：后端在 http://127.0.0.1:8088 运行（dev profile，mock 短信与实名）

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$base = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://127.0.0.1:8088' }
$script:pass = 0
$script:fail = 0

function Step([string]$name, [scriptblock]$action) {
    try {
        $result = & $action
        $script:pass++
        Write-Host ("[通过] {0}" -f $name) -ForegroundColor Green
        return $result
    } catch {
        $script:fail++
        Write-Host ("[失败] {0} -> {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
        return $null
    }
}

function Api([string]$method, [string]$path, $token, $body) {
    $headers = @{ 'Accept' = 'application/json' }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; ContentType = 'application/json; charset=utf-8'; TimeoutSec = 30 }
    if ($null -ne $body) { $params['Body'] = ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6 -Compress))) }
    elseif ($method -eq 'POST') { $params['Body'] = ([Text.Encoding]::UTF8.GetBytes('{}')) }
    $resp = Invoke-RestMethod @params
    if ($resp.code -ne 0) { throw "业务错误 [$($resp.code)] $($resp.message)" }
    return $resp.data
}

function SmsLogin([string]$phone) {
    $sent = Api 'POST' '/v1/auth/sms-code' $null @{ phone = $phone }
    Start-Sleep -Milliseconds 1200
    return Api 'POST' '/v1/auth/sms-login' $null @{ phone = $phone; code = $sent.devCode }
}

# 下单一笔并走完「支付 → 取码 → 卖家核销」，返回 COMPLETED 订单
function CompleteOrder($buyerToken, $sellerToken, $listingId) {
    $order = Api 'POST' '/v1/orders' $buyerToken @{
        listingId = $listingId; pointId = 0; requestId = "E2E-$([guid]::NewGuid().ToString('N'))"
    }
    Api 'POST' "/v1/orders/$($order.orderNo)/pay" $buyerToken @{ channel = 'MOCK' } | Out-Null
    $c = Api 'GET' "/v1/orders/$($order.orderNo)/verify-code" $buyerToken $null
    $m = Api 'POST' "/v1/orders/$($order.orderNo)/meets" $sellerToken @{
        type = 'TRADE'; place = '图书馆西门'; time = (Get-Date).AddMinutes(20).ToString('yyyy-MM-ddTHH:mm:ss')
    }
    Api 'POST' "/v1/orders/meets/$($m.id)/confirm" $buyerToken @{ agreed = $true; note = 'ok' } | Out-Null
    $done = Api 'POST' "/v1/orders/$($order.orderNo)/verify" $sellerToken @{
        code = $c.code; signPhotoUrl = 'demo/sign-e2e.jpg'
    }
    if ($done.status -ne 'COMPLETED') { throw "订单未完成：$($done.status)" }
    return $done
}

Write-Host "`n=== 同窗书打磨链路 E2E ($base) ===`n" -ForegroundColor Cyan

$buyer = Step '买家短信登录' { SmsLogin '13800000002' }
$seller = Step '卖家短信登录' { SmsLogin '13800000003' }
$admin = Step '管理员短信登录' { SmsLogin '13800000001' }
$buyerToken = $buyer.token; $sellerToken = $seller.token; $adminToken = $admin.token

Step 'ISBN 查询（命中教材库）' {
    $r = Api 'GET' '/v1/isbn/9787040396638' $null $null
    if (-not $r.found) { throw '未命中示例 ISBN' }
    Write-Host ("     {0} / {1}" -f $r.title, $r.publisher)
} | Out-Null

Step 'ISBN 查询（未命中应 found=false）' {
    $r = Api 'GET' '/v1/isbn/9999999999999' $null $null
    if ($r.found) { throw '不应命中' }
} | Out-Null

Step '实名/学生认证状态' {
    $s = Api 'GET' '/v1/verify/status' $buyerToken $null
    if (-not $s.idVerified -or -not $s.studentVerified) { throw "认证未通过：$($s | ConvertTo-Json -Compress)" }
} | Out-Null

$list = Api 'GET' '/v1/listings?size=30' $null $null
$pick = $list.records | Where-Object { $_.sellerId -eq $seller.user.id } | Select-Object -First 1
if (-not $pick) { throw '没有卖家在售商品，先跑 tools\smoke.ps1' }

# ---- 场景一：退款协商同意 ----
$order1 = Step '场景一：下单并完成交易' { CompleteOrder $buyerToken $sellerToken $pick.id }
$refund1 = Step '场景一：买家申请退款' {
    $r = Api 'POST' "/v1/orders/$($order1.orderNo)/refund" $buyerToken @{ reason = '书内有大量笔记'; photoUrl = 'demo/evidence1.jpg' }
    if ($r.status -ne 'PENDING') { throw "退款单状态异常：$($r.status)" }
    $r
}
Step '场景一：卖家同意退款' {
    $r = Api 'POST' "/v1/orders/refunds/$($refund1.id)/agree" $sellerToken $null
    if ($r.status -ne 'AGREED') { throw "状态异常：$($r.status)" }
} | Out-Null
Step '场景一：买家确认退货完成 → 订单退款' {
    Api 'POST' "/v1/orders/refunds/$($refund1.id)/finish" $buyerToken $null | Out-Null
    $o = Api 'GET' "/v1/orders/$($order1.orderNo)" $buyerToken $null
    if ($o.status -ne 'REFUNDED') { throw "订单状态异常：$($o.status)" }
} | Out-Null

# ---- 场景二：协商不成 → 平台介入判买家胜 ----
$order2 = Step '场景二：下单并完成交易' {
    $fresh = (Api 'GET' '/v1/listings?size=30' $null $null).records | Where-Object { $_.sellerId -eq $seller.user.id -and $_.status -eq 'ON_SALE' } | Select-Object -First 1
    if (-not $fresh) { throw '没有可售商品' }
    CompleteOrder $buyerToken $sellerToken $fresh.id
}
$refund2 = Step '场景二：买家申请退款' {
    Api 'POST' "/v1/orders/$($order2.orderNo)/refund" $buyerToken @{ reason = '与描述不符'; photoUrl = 'demo/evidence2.jpg' }
}
Step '场景二：卖家拒绝退款' {
    $r = Api 'POST' "/v1/orders/refunds/$($refund2.id)/reject" $sellerToken @{ reason = '书况一致，拒绝' }
    if ($r.status -ne 'REJECTED') { throw "状态异常：$($r.status)" }
} | Out-Null
$arb = Step '场景二：买家申请平台介入' {
    $r = Api 'POST' "/v1/orders/refunds/$($refund2.id)/arbitrate" $buyerToken @{ note = '请平台核对照片' }
    if ($r.status -ne 'ARBITRATING') { throw "状态异常：$($r.status)" }
    $r
}
Step '场景二：平台判定卖家责任 → 强制退款' {
    $r = Api 'POST' "/v1/orders/refunds/$($refund2.id)/judge" $adminToken @{ buyerWin = $true; note = '照片证据成立' }
    $o = Api 'GET' "/v1/orders/$($order2.orderNo)" $buyerToken $null
    if ($o.status -ne 'REFUNDED') { throw "订单应退款，实际：$($o.status)" }
} | Out-Null

# ---- 场景三：买卖私信 ----
$conv = Step '买家打开会话并发送消息' {
    $c = Api 'POST' '/v1/conversations' $buyerToken @{ listingId = $pick.id }
    Api 'POST' "/v1/conversations/$($c.id)/messages" $buyerToken @{ type = 'TEXT'; content = '同学你好，这本书还有吗？'; extra = '' } | Out-Null
    $c
}
Step '卖家看到会话并回复' {
    $page = Api 'GET' '/v1/conversations?page=1&size=20' $sellerToken $null
    if (-not ($page.records | Where-Object { $_.conversation.id -eq $conv.id })) { throw '卖家会话列表里没有该会话' }
    Api 'POST' "/v1/conversations/$($conv.id)/messages" $sellerToken @{ type = 'TEXT'; content = '在的，随时可以看'; extra = '' } | Out-Null
    $msgs = Api 'GET' "/v1/conversations/$($conv.id)/messages?afterId=0" $buyerToken $null
    if ($msgs.Count -lt 2) { throw "消息条数不足：$($msgs.Count)" }
    Write-Host ("     会话 {0} 共 {1} 条消息" -f $conv.id, $msgs.Count)
} | Out-Null

# ---- 场景四：商户入驻 → 平台审核 → 批量上架 ----
$apply = Step '用户提交商户入驻申请' {
    Api 'POST' '/v1/merchant/apply' $buyerToken @{ shopName = 'E2E 测试书店'; licenseUrl = 'demo/license.jpg'; contact = '13800000002' }
} | Out-Null
Step '平台审核通过商户' {
    $pend = Api 'GET' '/v1/admin/merchants?status=PENDING' $adminToken $null
    $rows = if ($pend.records) { $pend.records } else { $pend }
    $target = $rows | Where-Object { $_.status -eq 'PENDING' } | Select-Object -First 1
    if (-not $target) { throw '待审列表里没有刚提交的商户' }
    Api 'POST' "/v1/admin/merchants/$($target.id)/review" $adminToken @{ approved = $true; reason = '资质齐全' } | Out-Null
} | Out-Null
Step '商户批量上架 2 本书' {
    $r = Api 'POST' '/v1/merchant/listings/batch' $buyerToken @{ listings = @(
        @{ title = 'E2E 批量书 A'; textbookId = 1; courseId = 1; price = 9.90; originalPrice = 49.80; conditionLevel = 'GOOD'; description = '批量测试 A'; coverUrl = 'demo/a.jpg'; imageCount = 1; dorm = '1-101' },
        @{ title = 'E2E 批量书 B'; textbookId = 2; courseId = 2; price = 15.00; originalPrice = 59.00; conditionLevel = 'LIKE_NEW'; description = '批量测试 B'; coverUrl = 'demo/b.jpg'; imageCount = 1; dorm = '1-102' }
    ) }
    Write-Host ("     上架 {0} 本" -f $r.count)
    if ($r.count -ne 2) { throw "上架数量异常：$($r.count)" }
} | Out-Null
Step '商户统计' {
    $s = Api 'GET' '/v1/merchant/stats' $buyerToken $null
    Write-Host ("     店铺 {0} 在售 {1} 件" -f $s.shopName, $s.onSale)
} | Out-Null

Write-Host ("`n=== 结果：{0} 通过 / {1} 失败 ===`n" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
