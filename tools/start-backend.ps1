# 启动后端（可切换短信通道）：tools\start-backend.ps1
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-backend.ps1                 # dev + mock 短信
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-backend.ps1 -Sms spug        # dev + 真实短信（Spug）
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\start-backend.ps1 -Sms spug -Reset # 先清库再起
#
# 说明：spug 模式的模板编码来自 server/src/main/resources/application-spug-local.yml
#      （已被 .gitignore 忽略）或环境变量 APP_SPUG_TEMPLATE_CODE；两者都没有时会自动回退 mock。

[CmdletBinding()]
param(
    [ValidateSet("mock", "spug")]
    [string]$Sms = "mock",
    [switch]$Reset,
    [string]$JdkPath = ""
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "server\target\textbook-loop-server-0.1.0.jar"

# 1) 选 JDK 17
$jdk = $null
foreach ($c in @($JdkPath, $env:JAVA_HOME, "$env:USERPROFILE\.jdks\corretto-17.0.20.1")) {
    if ($c -and (Test-Path (Join-Path $c "bin\java.exe"))) { $jdk = $c; break }
}
if (-not $jdk) {
    Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "17" } | ForEach-Object { if (-not $jdk) { $jdk = $_.FullName } }
}
if (-not $jdk) { Write-Host "[X] 未找到 JDK 17，请先跑 tools\setup-dev.ps1" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $jar)) { Write-Host "[X] 未找到 $jar，请先在 IDEA 里构建或运行 .\mvnw.cmd -DskipTests package" -ForegroundColor Red; exit 1 }

# 2) 停掉旧的 8088
$conn = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conn) {
    $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
    if ($p -and $p.ProcessName -eq "java") {
        Write-Host "[1/3] 停止旧后端 PID $($p.Id)"
        Stop-Process -Id $p.Id -Force
        Start-Sleep -Seconds 3
    }
}

# 3) 需要时清库
if ($Reset -and (Test-Path (Join-Path $root "data"))) {
    Write-Host "[2/3] 清空本地 H2 数据（下次启动重新灌种子）"
    Remove-Item (Join-Path $root "data") -Recurse -Force
}

# 4) 启动
$profiles = if ($Sms -eq "spug") { "dev,spug" } else { "dev" }
$javaArgs = @("-jar", "`"$jar`"", "--spring.profiles.active=$profiles")
Write-Host "[3/3] 启动后端：$jdk\bin\java.exe $($javaArgs -join ' ')"
$proc = Start-Process -FilePath (Join-Path $jdk "bin\java.exe") -ArgumentList $javaArgs -WorkingDirectory $root -PassThru -WindowStyle Hidden

for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $h = Invoke-RestMethod "http://127.0.0.1:8088/v1/health" -TimeoutSec 3
        if ($h.code -eq 0) {
            Write-Host "后端就绪：http://127.0.0.1:8088（PID $($proc.Id)，profile=$profiles）" -ForegroundColor Green
            exit 0
        }
    } catch { }
}
Write-Host "[X] 启动超时，请查看 server\target\backend.out.log" -ForegroundColor Red
exit 1
