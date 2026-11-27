# 重置本地开发数据库并重启后端（H2 文件库在仓库根 data/ 下）
# 用法: powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1
$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $PSScriptRoot
$jar  = Join-Path $root "server\target\textbook-loop-server-0.1.0.jar"
$data = Join-Path $root "data"
$javaHome = "C:\Users\86180\.jdks\corretto-17.0.20.1"
$java = Join-Path $javaHome "bin\java.exe"

if (-not (Test-Path $jar)) { throw "未找到 $jar，请先构建：mvn -DskipTests package" }

# 1) 停掉占用 8088 的 java 进程
$conn = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conn) {
  $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
  if ($p -and $p.ProcessName -eq "java") {
    Write-Host "[1/4] 停止旧后端 PID $($p.Id)"
    Stop-Process -Id $p.Id -Force
    Start-Sleep -Seconds 2
  }
}
if (-not $conn) { Write-Host "[1/4] 8088 未被占用" }

# 2) 删除 H2 数据目录（下次启动自动重新种子）
if (Test-Path $data) {
  Write-Host "[2/4] 删除旧数据目录 $data"
  Remove-Item $data -Recurse -Force
} else { Write-Host "[2/4] 无旧数据目录" }

# 3) 启动后端（工作目录必须是仓库根，application-dev.yml 用的是 ./data 相对路径）
Write-Host "[3/4] 启动后端 ..."
$env:JAVA_HOME = $javaHome
$proc = Start-Process -FilePath $java -ArgumentList "-jar","`"$jar`"" -WorkingDirectory $root -PassThru -WindowStyle Hidden
Write-Host "      PID $($proc.Id)"

# 4) 等健康检查
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  try {
    $r = Invoke-RestMethod "http://127.0.0.1:8088/v1/health" -TimeoutSec 3
    if ($r.code -eq 0) { $ok = $true; break }
  } catch { }
}
if ($ok) {
  Write-Host "[4/4] 后端就绪：http://127.0.0.1:8088"
  Write-Host "      买家 13800000002 / 卖家 13800000003（短信登录，密码 123456 亦可用）"
  Write-Host "      管理员 13800000001 / admin123"
} else {
  Write-Host "[4/4] 启动超时，请查看 java 输出或 logs"
  exit 1
}