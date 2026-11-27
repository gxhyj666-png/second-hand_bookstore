# 端到端验证：下单 → 支付 → 待核销 → 核销码 → 店员核销 → 双边评价 → 信用分/消息
# 用法（本机 PS 5.1）：powershell -NoProfile -ExecutionPolicy Bypass -File tools\e2e-full.ps1
$ErrorActionPreference = 'Stop'
$Base = 'http://127.0.0.1:8088/v1'
$script:fail = 0

function Api($method, $path, $token, $body) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  # 无 body 的 POST 也要显式发 JSON，否则 PowerShell 会带 form-urlencoded，Spring 报 HttpMediaTypeNotSupported
  if ($method -eq 'POST' -and $null -eq $body) { $body = @{} }
  $q = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; TimeoutSec = 30 }
  if ($null -ne $body) {
    $json = $body | ConvertTo-Json -Depth 6 -Compress
    $q['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    $q['ContentType'] = 'application/json; charset=utf-8'
  }
  try {
    $r = Invoke-RestMethod @q
  } catch {
    throw "HTTP $method $path 失败：$($_.ErrorDetails.Message)"
  }
  # 业务错误也当失败处理，避免“HTTP 200 但 code=40900”被误判为通过
  if ($null -ne $r.code -and $r.code -ne 0) {
    throw "业务错误 [$($r.code)] $($r.message)"
  }
  return $r
}

function Step($name, $block) {
  try {
    $r = & $block
    Write-Host ("  [OK]   " + $name) -ForegroundColor Green
    return $r
  } catch {
    $script:fail++
    Write-Host ("  [FAIL] " + $name + " -> " + $_.Exception.Message) -ForegroundColor Red
    return $null
  }
}

Write-Host "== 1. 登录三个角色 ==" -ForegroundColor Cyan
$buyer  = Step '登录买家 13800000002' { (Api POST '/auth/login' $null @{ account = '13800000002'; password = '123456' }).data.token }
$sellerLogin = Step '登录卖家 13800000003' { (Api POST '/auth/login' $null @{ account = '13800000003'; password = '123456' }).data }
$seller = $sellerLogin.token
$clerk  = Step '登录店员 13800000004' { (Api POST '/auth/login' $null @{ account = '13800000004'; password = '123456' }).data.token }

Write-Host "== 2. 找一件别的卖家（13800000003）的在售商品 ==" -ForegroundColor Cyan
# 必须挑登录卖家本人名下的商品，否则后置的双边评价会被服务端以“只能评价自己参与的订单”拒绝
$listing = Step '检索在售商品' {
  (Api GET '/listings?size=20' $buyer).data.records |
    Where-Object { $_.status -eq 'ON_SALE' -and $_.sellerId -eq $sellerLogin.user.id } | Select-Object -First 1
}
if (-not $listing) { Write-Host '没有可下单的在售商品，测试终止' -ForegroundColor Red; exit 1 }
Write-Host ("    选中：" + $listing.title + " ¥" + $listing.price + "（卖家 id=" + $listing.sellerId + "）")

Write-Host "== 3. 下单 → 支付 → 标记待核销 ==" -ForegroundColor Cyan
$order = Step '创建订单' {
  (Api POST '/orders' $buyer @{ listingId = $listing.id; pointId = 1; requestId = [guid]::NewGuid().ToString() }).data
}
$orderNo = $order.orderNo
Write-Host ("    订单号：" + $orderNo)
Step '支付订单' { Api POST "/orders/$orderNo/pay" $buyer $null | Out-Null }
Step '买家标记“已到店待核销”' { Api POST "/orders/$orderNo/wait-verify" $buyer $null | Out-Null }

Write-Host "== 4. 核销码与核销 ==" -ForegroundColor Cyan
$code = Step '获取 6 位动态核销码' { (Api GET "/orders/$orderNo/verify-code" $buyer).data }
if ($code) { Write-Host ("    核销码：" + $code.code + "，剩余 " + $code.remainSeconds + " 秒，状态 " + $code.status) }
Step '店员核销' { Api POST '/orders/verify' $clerk @{ orderNo = $orderNo; code = $code.code } | Out-Null }

$after = Step '查看订单终态' { (Api GET "/orders/$orderNo" $buyer).data }
if ($after) { Write-Host ("    状态：" + $after.status) }

Write-Host "== 5. 双边评价 ==" -ForegroundColor Cyan
Step '买家评价卖家（5 星）' {
  Api POST '/reviews' $buyer @{ orderNo = $orderNo; rating = 5; content = '书很新，卖家发货快，自提也方便。' } | Out-Null
}
Step '卖家评价买家（4 星）' {
  Api POST '/reviews' $seller @{ orderNo = $orderNo; rating = 4; content = '买家很准时，沟通顺畅。' } | Out-Null
}
Step '查看卖家收到的评价' {
  (Api GET "/users/$($listing.sellerId)/reviews?size=5" $buyer).data.records |
    Select-Object -First 3 | ForEach-Object { Write-Host ("    " + $_.rating + " 星：" + $_.content) }
}

Write-Host "== 6. 信用分与消息 ==" -ForegroundColor Cyan
Step '买家信用记录' {
  (Api GET '/me/credit-logs?size=3' $buyer).data.records |
    ForEach-Object { Write-Host ("    " + $_.changeVal + " 分：" + $_.reason) }
}
Step '买家未读数' {
  $u = (Api GET '/notices/unread-count' $buyer).data
  Write-Host ("    未读消息：" + ($u | ConvertTo-Json -Compress))
}

Write-Host ''
if ($script:fail -eq 0) {
  Write-Host '全链路全部通过 ✅' -ForegroundColor Green
  exit 0
} else {
  Write-Host ("有 " + $script:fail + " 步失败 ❌") -ForegroundColor Red
  exit 1
}

