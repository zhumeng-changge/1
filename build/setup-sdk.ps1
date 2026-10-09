$ErrorActionPreference = 'Continue'
$SDK = "C:\Users\Lenovo\Documents\deepseek-harness\default-workspace\schedule\apk\build\android-sdk"
$JDK = "C:\Program Files\BellSoft\LibericaJDK-25"

# 1) cmdline-tools/* 整理成 cmdline-tools/latest/*（sdkmanager 要求的结构）
$inner = Join-Path $SDK "cmdline-tools"
$latest = Join-Path $inner "latest"
if (-not (Test-Path -LiteralPath $latest)) {
  New-Item -ItemType Directory -Force -Path $latest | Out-Null
  Get-ChildItem $inner -Exclude "latest" | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination $latest -Force }
  Write-Host "已整理为 cmdline-tools/latest/"
}

$env:JAVA_HOME = $JDK
$sdkmgr = Join-Path $latest "bin\sdkmanager.bat"
Write-Host "sdkmanager: $sdkmgr"
if (-not (Test-Path -LiteralPath $sdkmgr)) { throw "找不到 sdkmanager.bat" }

# 2) 接受许可
Write-Host "`n接受许可协议..."
$y = ("y`n" * 60)
$y | & $sdkmgr --sdk_root="$SDK" --licenses 2>&1 | Select-Object -Last 5

# 3) 安装 build-tools（platform 已手动下载）
Write-Host "`n安装 build-tools;34.0.0 ..."
& $sdkmgr --sdk_root="$SDK" "build-tools;34.0.0" 2>&1 | Select-Object -Last 25

# 4) 校验
Write-Host "`n校验组件:"
foreach ($p in @(
  (Join-Path $SDK "platforms\android-34\android.jar"),
  (Join-Path $SDK "build-tools\34.0.0\aapt2.exe"),
  (Join-Path $SDK "build-tools\34.0.0\d8.bat"),
  (Join-Path $SDK "build-tools\34.0.0\zipalign.exe"),
  (Join-Path $SDK "build-tools\34.0.0\apksigner.bat"))) {
  "{0,-6} {1}" -f (Test-Path -LiteralPath $p), $p
}
