# 同窗书 · 一键环境配置（Windows / PowerShell 5.1+）
#
# 用途：别人把仓库拷到本机后，运行本脚本即可把环境配好（JDK/Maven/Node/pnpm/IDE 运行配置/本地配置）。
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1 -Start        # 配好后顺手启动后端
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1 -SkipInstall  # 跳过 pnpm install
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\setup-dev.ps1 -JdkPath "C:\path\to\jdk17"
#
# 设计原则：**幂等**（重复执行不报错、不覆盖已有私有配置）、**只读不改系统**（不写注册表、不装全局软件，
# 缺什么就明确告诉你装什么）。

[CmdletBinding()]
param(
    [string]$JdkPath = "",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$Start,
    [switch]$WithPackage
)

# 注意：这里刻意用 Continue 而不是 Stop —— Maven / java -version / pnpm 都会往 stderr 写正常输出，
# PowerShell 5.1 在 Stop 模式下会把它们当成 terminating error。脚本内部改为显式检查 $LASTEXITCODE。
$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root "server"
$webDir = Join-Path $root "web"
$adminDir = Join-Path $root "admin"

function Info($msg) { Write-Host "  $msg" }
function Ok($msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  [!] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "  [X] $msg" -ForegroundColor Red }

Write-Host "`n=== 同窗书 · 环境配置 ===" -ForegroundColor Cyan
Write-Host "项目目录：$root`n"

$problems = New-Object System.Collections.ArrayList

# ---------------------------------------------------------------- 1. JDK 17
Write-Host "[1/6] 检测 JDK 17 ..."
$jdkCandidates = @()
if ($JdkPath) { $jdkCandidates += $JdkPath }
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$jdkCandidates += @(
    "$env:USERPROFILE\.jdks\corretto-17.0.20.1",
    "$env:USERPROFILE\.jdks\corretto-17",
    "$env:USERPROFILE\.jdks\temurin-17",
    "$env:USERPROFILE\.jdks\openjdk-17"
)
$jdkCandidates += (Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match "17" } | ForEach-Object { $_.FullName })

$jdkHome = $null
foreach ($c in $jdkCandidates) {
    if (-not $c) { continue }
    $javaExe = Join-Path $c "bin\java.exe"
    if (Test-Path $javaExe) {
        $ver = ""
        try { $ver = (& $javaExe -version 2>&1 | Out-String) } catch { $ver = "$_" }
        if ($ver -match '"(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { $jdkHome = $c; break }
        }
    }
}
if ($jdkHome) {
    Ok "JDK：$jdkHome"
    $env:JAVA_HOME = $jdkHome
    $env:PATH = "$jdkHome\bin;$env:PATH"
} else {
    Warn "未找到 JDK 17+。请任选一种方式安装后重跑本脚本："
    Info "winget install --id Amazon.Corretto.17 -e      # 或 Microsoft.OpenJDK.17 / EclipseAdoptium.Temurin.17.JDK"
    Info "IDEA 内：File > Project Structure > SDKs > + > Download JDK 17"
    Info "安装后若 JAVA_HOME 未生效，可用 -JdkPath 指定，例如 -JdkPath `"C:\Users\你\.jdks\corretto-17.0.20.1`""
    $problems.Add("缺少 JDK 17+") | Out-Null
}

# ---------------------------------------------------------------- 2. Maven（走 wrapper，无需全局安装）
Write-Host "[2/6] 检测 Maven / Maven Wrapper ..."
$mvnw = Join-Path $root "mvnw.cmd"
if (Test-Path $mvnw) {
    Ok "自带 Maven Wrapper（仓库根 mvnw.cmd，首次运行会自动下载 Maven 3.9.9）"
} else {
    Warn "缺少 mvnw.cmd，将回退到全局 mvn；请确认已安装 Maven 3.8+"
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { $problems.Add("缺少 Maven 且无 wrapper") | Out-Null }
}

# ---------------------------------------------------------------- 3. Node / pnpm
Write-Host "[3/6] 检测 Node / pnpm ..."
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    $nodeVer = (& node -v).Trim()
    $nodeMajor = [int]($nodeVer.TrimStart("v").Split(".")[0])
    if ($nodeMajor -ge 18) { Ok "Node：$nodeVer" } else { Warn "Node 版本过低（$nodeVer），需要 >= 18"; $problems.Add("Node < 18") | Out-Null }
} else {
    Warn "未找到 Node.js，前端无法构建。安装：winget install OpenJS.NodeJS.LTS"
    $problems.Add("缺少 Node.js") | Out-Null
}

$pnpmCmd = "pnpm"
if (Get-Command pnpm -ErrorAction SilentlyContinue) {
    Ok "pnpm：$((& pnpm -v).Trim())"
} else {
    Warn "未找到 pnpm，尝试用 corepack 启用 ..."
    try {
        & corepack enable 2>$null
        & corepack prepare pnpm@11.8.0 --activate 2>$null
    } catch { }
    if (Get-Command pnpm -ErrorAction SilentlyContinue) {
        Ok "pnpm 已通过 corepack 启用：$((& pnpm -v).Trim())"
    } else {
        Warn "corepack 不可用，回退到 npx pnpm@11"
        $pnpmCmd = "npx"
    }
}

# ---------------------------------------------------------------- 4. 编译后端
$buildAction = if ($WithPackage) { "package" } else { "compile" }
$buildLabel = if ($SkipBuild) { "跳过" } else { "mvnw -DskipTests $buildAction" }
Write-Host "[4/6] 编译后端（$buildLabel） ..."
if (-not $SkipBuild -and -not $problems.Contains("缺少 JDK 17+")) {
    Push-Location $root
    try {
        $goals = @("-B", "-q", "-DskipTests", $buildAction)
        if (Test-Path $mvnw) { & $mvnw @goals } else { & mvn @goals }
        if ($LASTEXITCODE -eq 0) { Ok "后端编译通过（聚合工程 textbook-loop -> server 模块）" } else { Fail "后端编译失败，请查看上面的 Maven 输出"; $problems.Add("后端编译失败") | Out-Null }
    } finally { Pop-Location }
} else {
    Info "已跳过"
}

# ---------------------------------------------------------------- 5. 前端依赖
Write-Host "[5/6] 安装前端依赖 ..."
if (-not $SkipInstall -and $node) {
    foreach ($dir in @($webDir, $adminDir)) {
        $name = Split-Path -Leaf $dir
        if (-not (Test-Path (Join-Path $dir "package.json"))) { Warn "$name 缺少 package.json，跳过"; continue }
        Push-Location $dir
        try {
            if ($pnpmCmd -eq "npx") {
                & npx --yes pnpm@11.8.0 install --frozen-lockfile 2>&1 | Select-Object -Last 2
            } else {
                & pnpm install --frozen-lockfile 2>&1 | Select-Object -Last 2
            }
            if ($LASTEXITCODE -eq 0) { Ok "$name 依赖就绪" } else { Warn "$name pnpm install 失败（可手动执行 pnpm install 重试）"; $problems.Add("$name 依赖安装失败") | Out-Null }
        } finally { Pop-Location }
    }
} else { Info "已跳过" }

# ---------------------------------------------------------------- 6. IDE / 本地配置
Write-Host "[6/6] 配置 IDEA 与本机私有文件 ..."

# 6.1 找 IDEA 已注册的 JDK 名称，写进 .idea/misc.xml（让 Project SDK 自动指向 JDK 17）
$jdkTable = Get-ChildItem "$env:APPDATA\JetBrains" -Recurse -Filter "jdk.table.xml" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$sdkName = $null
if ($jdkTable -and $jdkHome) {
    try {
        [xml]$tbl = Get-Content $jdkTable.FullName -Raw
        $normalized = ($jdkHome -replace '\\', '/').ToLower()
        foreach ($jdk in $tbl.application.component.jdk) {
            $sdkPath = ($jdk.homePath.value -replace '\$USER_HOME\$', $env:USERPROFILE) -replace '\\', '/'
            if ($sdkPath.ToLower().TrimEnd('/') -eq $normalized.TrimEnd('/')) { $sdkName = $jdk.name.value; break }
        }
        if (-not $sdkName) {
            $cand = $tbl.application.component.jdk | Where-Object { $_.name.value -match "17" } | Select-Object -First 1
            if ($cand) { $sdkName = $cand.name.value }
        }
    } catch { }
}
if (-not $sdkName) { $sdkName = "17" }

$ideaDir = Join-Path $root ".idea"
if (-not (Test-Path $ideaDir)) { New-Item -ItemType Directory -Path $ideaDir | Out-Null }
$utf8 = New-Object System.Text.UTF8Encoding($false)

$miscXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_17" project-jdk-type="JavaSDK" project-jdk-name="$sdkName" default="true">
    <output url="file://`$PROJECT_DIR`$/out" />
  </component>
</project>
"@
[IO.File]::WriteAllText((Join-Path $ideaDir "misc.xml"), $miscXml, $utf8)

$encXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="Encoding" defaultCharsetForPropertiesFiles="UTF-8">
    <file url="PROJECT" charset="UTF-8" />
  </component>
</project>
"@
[IO.File]::WriteAllText((Join-Path $ideaDir "encodings.xml"), $encXml, $utf8)

$compilerXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <annotationProcessing>
      <profile default="true" name="Default" enabled="true" />
    </annotationProcessing>
    <bytecodeTargetLevel target="17" />
  </component>
</project>
"@
[IO.File]::WriteAllText((Join-Path $ideaDir "compiler.xml"), $compilerXml, $utf8)
Ok "IDEA：Project SDK -> $sdkName（JDK 17），编码 UTF-8，开启注解处理（Lombok）"

# 6.2 本机私有配置：Spug 短信模板编码（留空则走 mock）
$spugLocal = Join-Path $serverDir "src\main\resources\application-spug-local.yml"
if (-not (Test-Path $spugLocal)) {
    $spugTpl = @"
# 本机私有配置（已被 .gitignore 忽略，不要提交）
# 只有要真实发短信时才需要：Spug 控制台 -> 短信验证码 -> 模板详情里的 https://push.spug.cc/sms/<这一段>
# 留空/删掉本文件时，后端自动走 mock（验证码随 /v1/auth/sms-code 响应回传）
app:
  spug-template-code: ""
"@
    [IO.File]::WriteAllText($spugLocal, $spugTpl, (New-Object System.Text.UTF8Encoding($true)))
    Info "已生成 server\src\main\resources\application-spug-local.yml（模板编码留空 = 走 mock）"
} else { Info "保留已有的 application-spug-local.yml" }

# 6.3 管理后台 mock 开关
$adminEnv = Join-Path $adminDir ".env"
if (-not (Test-Path $adminEnv)) {
    [IO.File]::WriteAllText($adminEnv, "VITE_USE_MOCK=true`nVITE_API_BASE=http://127.0.0.1:8088`n", $utf8)
    Info "已生成 admin\.env（默认 mock；接真后端把 VITE_USE_MOCK 改 false）"
} else { Info "保留已有的 admin\.env" }

# 6.4 运行配置（IDEA 会从 .run 目录自动识别）
$runDir = Join-Path $root ".run"
if (Test-Path $runDir) { Ok "IDEA 运行配置：$((Get-ChildItem $runDir -Filter *.run.xml).Count) 个（后端 dev / 后端 mysql / Web / Admin / 一键全启）" }
else { Warn "缺少 .run 目录，运行配置需手动新建" }

# ---------------------------------------------------------------- 启动（可选）
if ($Start -and -not $problems.Contains("缺少 JDK 17+")) {
    Write-Host "`n启动后端（首次会灌种子数据）..." -ForegroundColor Cyan
    $jar = Join-Path $serverDir "target\textbook-loop-server-0.1.0.jar"
    if (Test-Path $jar) {
        $p = Start-Process -FilePath (Join-Path $env:JAVA_HOME "bin\java.exe") -ArgumentList "-jar", "`"$jar`"" -WorkingDirectory $root -PassThru -WindowStyle Hidden
        $ok = $false
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 1
            try { if ((Invoke-RestMethod "http://127.0.0.1:8088/v1/health" -TimeoutSec 3).code -eq 0) { $ok = $true; break } } catch { }
        }
        if ($ok) { Ok "后端已启动（PID $($p.Id)）：http://127.0.0.1:8088" }
        else { Warn "后端启动超时，请查看控制台或 -WithPackage 后重新启动" }
    } else {
        Warn "未找到可执行 jar，先运行带 -WithPackage 的脚本，或在 IDEA 里跑 Backend dev (H2) 配置"
    }
}

# ---------------------------------------------------------------- 总结
Write-Host "`n=== 结果 ===" -ForegroundColor Cyan
if ($problems.Count -eq 0) {
    Write-Host "环境已就绪 ✅" -ForegroundColor Green
} else {
    Write-Host "还有 $($problems.Count) 项需要处理：" -ForegroundColor Yellow
    $problems | ForEach-Object { Write-Host "  - $_" }
}
Write-Host @"

下一步（IDEA 里）：
  1) 打开项目根目录，右下角若提示 Load Maven Project，点 Load（server/pom.xml 会被识别为 Maven 模块）
  2) Project SDK 应已是 JDK 17（本脚本已写入 .idea/misc.xml）；若没有，File > Project Structure > Project SDK 选 17
  3) 右上角运行配置下拉里选：
       Backend dev (H2)   —— 后端（H2 文件库，零依赖）
       Web dev (Vite)     —— 用户端 http://localhost:5174
       Admin dev (Vite)   —— 后台   http://localhost:5173（默认 mock）
       Dev all            —— 后端 + 用户端一起启动
  4) 演示账号：买家 13800000002 / 卖家 13800000003（密码 123456），管理员 13800000001 / admin123
  5) 想重置数据：powershell -NoProfile -ExecutionPolicy Bypass -File tools\reset-dev.ps1

说明：后端工作目录必须是仓库根（H2 的 ./data 相对进程工作目录），
      .run 里的配置已把 WORKING_DIRECTORY 设为 `$PROJECT_DIR`$，不要改成 server 目录。
"@ -ForegroundColor DarkGray

if ($problems.Count -gt 0) { exit 1 }
 exit 0
