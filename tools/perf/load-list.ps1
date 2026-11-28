# 读接口并发压测（TC-P01 列表 / TC-P03 推荐与详情）

# 不依赖 JMeter：用 runspace 池并发发 GET，统计 TPS / 平均 / P95 / 最大值 / 错误率。

# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\perf\load-list.ps1 -Scenario list -Threads 50 -Iterations 200
#   -Scenario 可选：list（关键词检索）/ recommend（推荐）/ detail（商品详情，需 -ListingId）

param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [ValidateSet('list', 'recommend', 'detail')][string]$Scenario = "list",
    [int]$Threads = 50,
    [int]$Iterations = 200,
    [int]$ListingId = 1,
    [string]$Keyword = "数学",
    [string]$Token = ""
)

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$path = switch ($Scenario) {
    'list' { "/v1/listings?keyword=$([uri]::EscapeDataString($Keyword))&size=20&page=1" }
    'recommend' { "/v1/recommend?size=8" }
    'detail' { "/v1/listings/$ListingId" }
}

$total = $Threads * $Iterations
Write-Host ("场景 {0}：{1}  → {2} 线程 × {3} 次 = {4} 请求" -f $Scenario, $path, $Threads, $Iterations, $total)

$worker = {
    param($BaseUrl, $Path, $Iterations, $Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $rows = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $Iterations; $i++) {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $okFlag = $true; $code = 0
        try {
            $r = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Get -Headers $headers -TimeoutSec 60 -ErrorAction Stop
            $code = [int]$r.code
            if ($code -ne 0) { $okFlag = $false }
        }
        catch {
            $okFlag = $false; $code = -1
        }
        $sw.Stop()
        [void]$rows.Add([pscustomobject]@{ Ok = $okFlag; Code = $code; Ms = [int]$sw.ElapsedMilliseconds })
    }
    , $rows.ToArray()
}

$pool = [runspacefactory]::CreateRunspacePool(1, [Math]::Max(1, $Threads))
$pool.Open()
$tasks = @()
$wall = [Diagnostics.Stopwatch]::StartNew()
for ($t = 1; $t -le $Threads; $t++) {
    $ps = [powershell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($worker).AddArgument($BaseUrl).AddArgument($path).AddArgument($Iterations).AddArgument($Token)
    $tasks += [pscustomobject]@{ PS = $ps; Handle = $ps.BeginInvoke() }
}

$all = @()
foreach ($t in $tasks) {
    foreach ($chunk in $t.PS.EndInvoke($t.Handle)) { $all += $chunk }
    $t.PS.Dispose()
}
$pool.Close(); $pool.Dispose()
$wall.Stop()

$ms = @($all | ForEach-Object { $_.Ms } | Sort-Object)
$okCount = @($all | Where-Object { $_.Ok }).Count
$p95Index = [Math]::Min($ms.Count - 1, [Math]::Floor($ms.Count * 0.95))
$secs = [Math]::Max(0.001, $wall.Elapsed.TotalSeconds)

Write-Host ""
Write-Host "================ 结果 ================"
Write-Host ("总请求     : {0}" -f $all.Count)
Write-Host ("成功       : {0}　失败: {1}" -f $okCount, ($all.Count - $okCount))
Write-Host ("墙钟耗时   : {0:N2} s　→ TPS ≈ {1:N1}" -f $secs, ($all.Count / $secs))
Write-Host ("平均 RT    : {0:N1} ms" -f ($ms | Measure-Object -Average).Average)
Write-Host ("P95 RT     : {0} ms" -f $ms[$p95Index])
Write-Host ("最大 RT    : {0} ms" -f ($ms | Measure-Object -Maximum).Maximum)
Write-Host ("错误率     : {0:P2}" -f (($all.Count - $okCount) / [Math]::Max(1, $all.Count)))
Write-Host "======================================"

if (($all.Count - $okCount) -gt 0) { exit 1 } else { exit 0 }
