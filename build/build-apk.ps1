#!/usr/bin/env pwsh
# 手动打包 APK：aapt2(资源) + javac(Java) + d8(字节码) + zipalign/apksigner(签名)
# 不依赖 Gradle / Android Studio。
$ErrorActionPreference = 'Stop'

$ROOT  = "C:\Users\Lenovo\Documents\deepseek-harness\default-workspace\schedule\apk"
$SDK   = Join-Path $ROOT "build\android-sdk"
$JDK   = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\BellSoft\LibericaJDK-25" }
$BT    = Join-Path $SDK "build-tools\34.0.0"
$PLAT  = Join-Path $SDK "platforms\android-34\android.jar"
$OUT   = Join-Path $ROOT "build\out"
$APK_UNSIGNED = Join-Path $OUT "app-unsigned.apk"
$APK_ALIGNED  = Join-Path $OUT "app-aligned.apk"
$APK_FINAL    = Join-Path $ROOT "xizuo-reminder.apk"

foreach ($d in @($OUT, (Join-Path $OUT "compiled"), (Join-Path $OUT "gen"), (Join-Path $OUT "classes"), (Join-Path $OUT "dex"))) {
  New-Item -ItemType Directory -Force -Path $d | Out-Null
}

function Step($msg) { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Run($exe, $params) {
  & $exe @params
  if ($LASTEXITCODE -ne 0) { throw "$exe 失败（exit $LASTEXITCODE）" }
}

Step "0/7 检查工具链"
foreach ($p in @($PLAT, (Join-Path $BT "aapt2.exe"), (Join-Path $BT "d8.bat"), (Join-Path $BT "zipalign.exe"), (Join-Path $BT "apksigner.bat"), (Join-Path $JDK "bin\javac.exe"))) {
  if (-not (Test-Path -LiteralPath $p)) { throw "缺少组件: $p" }
  Write-Host "  ok $p"
}

Step "1/7 生成启动图标（mipmap）"
$py = "C:\Users\Lenovo\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
Run $py @((Join-Path $ROOT "build\make_icons.py"))

Step "2/7 aapt2 compile 资源"
$resDir = Join-Path $ROOT "res"
$compiled = Join-Path $OUT "compiled"
Run (Join-Path $BT "aapt2.exe") @("compile", "--dir", $resDir, "-o", $compiled, "--no-crunch")

Step "3/7 aapt2 link 生成资源包"
$resZip = Join-Path $OUT "res.zip"
Run (Join-Path $BT "aapt2.exe") @(
  "link", "-o", $resZip,
  "-I", $PLAT,
  "--manifest", (Join-Path $ROOT "AndroidManifest.xml"),
  "--min-sdk-version", "24",
  "--target-sdk-version", "34",
  "--version-code", "1",
  "--version-name", "1.0",
  "--java", (Join-Path $OUT "gen"),
  "-A", (Join-Path $ROOT "assets"),
  "--auto-add-overlay",
  (Get-ChildItem $compiled -Filter *.flat | ForEach-Object { $_.FullName })
)

Step "4/7 javac 编译 Java 源码"
$srcs = Get-ChildItem (Join-Path $ROOT "java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$genSrcs = Get-ChildItem (Join-Path $OUT "gen") -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$all = @($srcs) + @($genSrcs)
Write-Host "  源文件 $($all.Count) 个"
Run (Join-Path $JDK "bin\javac.exe") @(
  "-encoding", "UTF-8", "-source", "8", "-target", "8",
  "-bootclasspath", $PLAT,
  "-classpath", $PLAT,
  "-d", (Join-Path $OUT "classes"),
  "-nowarn"
) + $all

Step "5/7 d8 生成 dex"
$classes = Get-ChildItem (Join-Path $OUT "classes") -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Run (Join-Path $BT "d8.bat") @(
  "--release", "--min-api", "24", "--lib", $PLAT,
  "--output", (Join-Path $OUT "dex")
) + $classes

Step "6/7 组装 APK（资源 + dex + assets）"
Copy-Item -LiteralPath $resZip -Destination $APK_UNSIGNED -Force
Run (Join-Path $JDK "bin\jar.exe") @("uf", $APK_UNSIGNED, "-C", (Join-Path $OUT "dex"), "classes.dex")
Run (Join-Path $BT "zipalign.exe") @("-f", "-p", "4", $APK_UNSIGNED, $APK_ALIGNED)

Step "7/7 生成签名并签名 APK"
$ks = Join-Path $ROOT "build\xizuo.keystore"
if (-not (Test-Path -LiteralPath $ks)) {
  Run (Join-Path $JDK "bin\keytool.exe") @(
    "-genkeypair", "-keystore", $ks, "-alias", "xizuo",
    "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
    "-storepass", "xizuo123", "-keypass", "xizuo123",
    "-dname", "CN=Xizuo Reminder, OU=Student, O=TangXuanqi, L=Hengyang, ST=Hunan, C=CN"
  )
}
Run (Join-Path $BT "apksigner.bat") @(
  "sign", "--ks", $ks, "--ks-key-alias", "xizuo",
  "--ks-pass", "pass:xizuo123", "--key-pass", "pass:xizuo123",
  "--min-sdk-version", "24",
  "--out", $APK_FINAL, $APK_ALIGNED
)
Run (Join-Path $BT "apksigner.bat") @("verify", "--print-certs", $APK_FINAL)

$fi = Get-Item -LiteralPath $APK_FINAL
Write-Host "`n================ 打包完成 ================" -ForegroundColor Green
Write-Host ("APK : {0}" -f $fi.FullName)
Write-Host ("大小: {0:N2} MB" -f ($fi.Length / 1MB))
