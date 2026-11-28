# 并发抢单压测（TC-P02）

# 目的：验证「同一本书被 100 个买家同时下单，只有 1 单成功，其余 40900」——即超卖防护。

# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\concurrent-order.ps1 `
#       -Token <买家JWT> -ListingId 12 -Concurrency 100
#   拿 token：先跑 tools\start-backend.ps1，再按 README 里的短信登录取 JWT。

param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][int]$ListingId,
    [int]$Concurrency = 100
)

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

Write-Host ("并发抢单：listingId={0}，并发 {1}，目标 {2}" -f $ListingId, $Concurrency, $BaseUrl)

$worker = {
    param($BaseUrl, $Token, $ListingId, $Index)
    $body = @{
        listingId = $ListingId
        pointId   = 0
        requestId = ("PERF-{0}-{1}" -f $Index, [guid]::NewGuid().ToString('N'))
    } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $Token" }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-RestMethod -Uri "$BaseUrl/v1/orders" -Method Post -Headers $headers `
            -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 60 -ErrorAction Stop
        $sw.Stop()
        [pscustomobject]@{
            Index   = $Index
            Code    = [int]$r.code
            Message = [string]$r.message
            OrderNo = if ($r.data) { [string]$r.data.orderNo } else { '' }
            Ms      = [int]$sw.ElapsedMilliseconds
        }
    }
    catch {
        $sw.Stop()
        $code = -1; $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            try { $j = $_.ErrorDetails.Message | ConvertFrom-Json; $code = [int]$j.code; $msg = [string]$j.message } catch { }
        }
        [pscustomobject]@{ Index = $Index; Code = $code; Message = $msg; OrderNo = ''; Ms = [int]$sw.ElapsedMilliseconds }
    }
}

$pool = [runspacefactory]::CreateRunspacePool(1, [Math]::Max(1, $Concurrency))
$pool.Open()
$tasks = @()
for ($i = 1; $i -le $Concurrency; $i++) {
    $ps = [powershell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($worker).AddArgument($BaseUrl).AddArgument($Token).AddArgument($ListingId).AddArgument($i)
    $tasks += [pscustomobject]@{ PS = $ps; Handle = $ps.BeginInvoke() }
}

$results = @()
foreach ($t in $tasks) {
    $results += $t.PS.EndInvoke($t.Handle)
    $t.PS.Dispose()
}
$pool.Close(); $pool.Dispose()

$ok = @($results | Where-Object { $_.Code -eq 0 })
$fail = @($results | Where-Object { $_.Code -ne 0 })
$elapsed = ($results | Measure-Object -Property Ms -Maximum).Maximum

Write-Host ""
Write-Host "================ 结果 ================"
Write-Host ("总请求      : {0}" -f $results.Count)
Write-Host ("成功下单    : {0}" -f $ok.Count)
Write-Host ("失败        : {0}" -f $fail.Count)
if ($ok.Count -gt 0) {
    Write-Host ("成功订单号  : {0}" -f (($ok | ForEach-Object { $_.OrderNo }) -join ', '))
}
if ($fail.Count -gt 0) {
    Write-Host "失败分布（按 code / 文案）："
    $fail | Group-Object Code, Message | Sort-Object Count -Descending | ForEach-Object {
        Write-Host ("  [{0}] x{1}" -f $_.Name, $_.Count)
    }
}
Write-Host ("最长耗时    : {0} ms" -f $elapsed)
Write-Host ("判定        : {0}" -f $(if ($ok.Count -le 1) { 'PASS（无超卖）' } else { 'FAIL（出现多单，检查防超卖）' }))
Write-Host "======================================"

# 追加核对：商品状态应为 LOCKED（被唯一成功订单锁定），不应为 ON_SALE
try {
    $d = Invoke-RestMethod -Uri "$BaseUrl/v1/listings/$ListingId" -Method Get -TimeoutSec 30
    Write-Host ("商品当前状态: {0}（期望 LOCKED）" -f $d.data.listing.status)
}
catch {
    Write-Host "读取商品状态失败（不影响结论）"
}

if ($ok.Count -gt 1) { exit 1 } else { exit 0 }
