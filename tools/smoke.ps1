# 同窗书 · 主链路冒烟（打磨后的三态交易流程）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke.ps1
# 说明：短信验证码在 mock 模式下由 /v1/auth/sms-code 直接回传 devCode，无需真实短信。

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$base = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://127.0.0.1:8088' }
$api = $base
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
    $params = @{
        Uri         = "$api$path"
        Method      = $method
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec  = 30
    }
    if ($null -ne $body) { $params['Body'] = ([System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6 -Compress))) }
    elseif ($method -eq 'POST') { $params['Body'] = ([System.Text.Encoding]::UTF8.GetBytes('{}')) }
    $resp = Invoke-RestMethod @params
    if ($resp.code -ne 0) { throw "业务错误 [$($resp.code)] $($resp.message)" }
    return $resp.data
}

function SmsLogin([string]$phone) {
    $sent = Api 'POST' '/v1/auth/sms-code' $null @{ phone = $phone }
    $view = Api 'POST' '/v1/auth/sms-login' $null @{ phone = $phone; code = $sent.devCode }
    return $view
}

Write-Host "`n=== 同窗书主链路冒烟 ($api) ===`n" -ForegroundColor Cyan

$health = Step '健康检查' { Api 'GET' '/v1/health' $null $null }
if ($health) { Write-Host ("     status={0} app={1}" -f $health.status, $health.app) }

$buyerToken = $null
$buyer = Step '买家短信登录（13800000002）' {
    $v = SmsLogin '13800000002'
    if (-not $v.token) { throw '未返回 token' }
    $script:buyerToken = $v.token
    $v
}
$seller = Step '卖家短信登录（13800000003）' {
    $v = SmsLogin '13800000003'
    $script:sellerToken = $v.token
    $v
}
$admin = Step '管理员短信登录（13800000001）' {
    $v = SmsLogin '13800000001'
    $script:adminToken = $v.token
    $v
}

$list = Step '游客检索商品' { Api 'GET' '/v1/listings?size=20' $null $null }
if ($list) { Write-Host ("     在售 {0} 件" -f $list.total) }

$pick = $null
if ($list -and $list.records) {
    $pick = $list.records | Where-Object { $_.sellerId -eq $seller.user.id } | Select-Object -First 1
}
Step '商品详情 + 价格指数' {
    if (-not $pick) { throw '没有可买商品' }
    $view = Api 'GET' "/v1/listings/$($pick.id)" $null $null
    $detail = $view.listing
    $idx = Api 'GET' "/v1/textbooks/$($detail.textbookId)/price-index" $null $null
    Write-Host ("     《{0}》¥{1} 参考均价 ¥{2}" -f $detail.title, $detail.price, $idx.avg)
    $detail
} | Out-Null

Step '推荐列表（公开）' {
    $rec = Api 'GET' '/v1/recommend?size=8' $null $null
    if (-not $rec) { throw '推荐为空' }
    Write-Host ("     推荐 {0} 件" -f $rec.Count)
} | Out-Null

$myListing = Step '买家发布商品（含 7 张照片）' {
    $publish = Api 'POST' '/v1/listings' $buyerToken @{
        textbookId = 1; courseId = 1; price = 12.50; condition = 'LIKE_NEW'
        description = '冒烟测试商品'; dorm = '3-201'; images = @()
    }
    $photos = @(
        @{ type = 'COVER'; url = 'demo/cover.jpg' },
        @{ type = 'BACK'; url = 'demo/back.jpg' },
        @{ type = 'PUBLISH_DATE'; url = 'demo/pubdate.jpg' },
        @{ type = 'CONTENT1'; url = 'demo/p1.jpg' },
        @{ type = 'CONTENT2'; url = 'demo/p2.jpg' },
        @{ type = 'CONTENT3'; url = 'demo/p3.jpg' },
        @{ type = 'CONTENT4'; url = 'demo/p4.jpg' }
    )
    $saved = Api 'POST' "/v1/listings/$($publish.id)/photos" $buyerToken @{ photos = $photos }
    Write-Host ("     照片 {0} 张，齐全={1}" -f $saved.count, $saved.complete)
    $publish
}

$order = Step '买家下单' {
    if (-not $pick) { throw '没有可买商品' }
    Api 'POST' '/v1/orders' $buyerToken @{ listingId = $pick.id; pointId = 0; requestId = "SMOKE-$([guid]::NewGuid().ToString('N'))" }
}
if ($order) { Write-Host ("     订单 {0} 状态 {1}" -f $order.orderNo, $order.status) }

$paid = Step '买家支付' {
    if (-not $order) { throw '无订单' }
    $o = Api 'POST' "/v1/orders/$($order.orderNo)/pay" $buyerToken @{ channel = 'MOCK' }
    if ($o.status -ne 'WAIT_VERIFY') { throw "支付后状态异常：$($o.status)" }
    $o
}

$codeInfo = Step '买家出示核销码（15 分钟有效）' {
    if (-not $order) { throw '无订单' }
    $c = Api 'GET' "/v1/orders/$($order.orderNo)/verify-code" $buyerToken $null
    if ($c.code.Length -ne 6) { throw '核销码不是 6 位' }
    Write-Host ("     核销码 {0}，剩余 {1}s" -f $c.code, $c.remainSeconds)
    $c
}

$meet = Step '卖家发起约见 → 买家确认' {
    if (-not $order) { throw '无订单' }
    $m = Api 'POST' "/v1/orders/$($order.orderNo)/meets" $sellerToken @{
        type = 'TRADE'; place = '图书馆西门'; time = (Get-Date).AddMinutes(30).ToString('yyyy-MM-ddTHH:mm:ss')
    }
    $c = Api 'POST' "/v1/orders/meets/$($m.id)/confirm" $buyerToken @{ agreed = $true; note = '准时到' }
    if ($c.status -ne 'CONFIRMED') { throw "确认后状态异常：$($c.status)" }
    $c
}

$done = Step '卖家核销 + 签到照片' {
    if (-not $order -or -not $codeInfo) { throw '缺少订单或核销码' }
    $o = Api 'POST' "/v1/orders/$($order.orderNo)/verify" $sellerToken @{
        code = $codeInfo.code; signPhotoUrl = 'demo/sign.jpg'
    }
    if ($o.status -ne 'COMPLETED') { throw "核销后状态异常：$($o.status)" }
    $o
}
if ($done) { Write-Host ("     订单完成于 {0}" -f $done.completedAt) }

Step '双边评价' {
    if (-not $order) { throw '无订单' }
    Api 'POST' '/v1/reviews' $buyerToken @{ orderNo = $order.orderNo; rating = 5; content = '书很新，交易顺利'; anonymous = $false } | Out-Null
    Api 'POST' '/v1/reviews' $sellerToken @{ orderNo = $order.orderNo; rating = 5; content = '买家很爽快'; anonymous = $false } | Out-Null
} | Out-Null

Step '信用分与信用记录' {
    $me = Api 'GET' '/v1/auth/me' $buyerToken $null
    $logs = Api 'GET' '/v1/me/credit-logs?size=5' $buyerToken $null
    Write-Host ("     买家信用分 {0}，最近记录 {1} 条" -f $me.creditScore, $logs.total)
    if ($me.creditScore -lt 100) { throw '信用分未按预期增加' }
} | Out-Null

Step '未读消息' {
    $n = Api 'GET' '/v1/notices/unread-count' $buyerToken $null
    Write-Host ("     未读 {0} 条" -f $n.count)
} | Out-Null

Step '管理员看板 + 商户审核列表' {
    $d = Api 'GET' '/v1/admin/dashboard' $adminToken $null
    Write-Host ("     用户 {0} / 在售 {1} / GMV {2}" -f $d.userCount, $d.onSaleCount, $d.gmv)
    $m = Api 'GET' '/v1/admin/merchants' $adminToken $null
    $cnt = @($m).Count
    if ($m.records) { $cnt = @($m.records).Count }
    Write-Host ("     商户 {0} 家" -f $cnt)
} | Out-Null

Write-Host ("`n=== 结果：{0} 通过 / {1} 失败 ===`n" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
