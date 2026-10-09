// 手动打包 APK：aapt2(资源) + javac(Java) + d8(字节码) + zipalign/apksigner(签名)
// 用 Node 直接调用 .exe / java -jar / d8.jar，避免依赖 Gradle 与 .bat。
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = __dirname.replace(/\\build$/, '');
const SDKDIR = path.join(ROOT, 'build', 'android-sdk');
const JDK = process.env.JAVA_HOME || 'C:\\Program Files\\BellSoft\\LibericaJDK-25';
const BT = path.join(SDKDIR, 'build-tools', '35.0.0');
const PLATFORM = path.join(SDKDIR, 'platforms', 'android-34', 'android.jar');
const OUT = path.join(ROOT, 'build', 'out');
const APK_FINAL = path.join(ROOT, 'xizuo-reminder.apk');

const NODE = process.execPath;
const JAVA = path.join(JDK, 'bin', 'java.exe');
const JAVAC = path.join(JDK, 'bin', 'javac.exe');
const JAR = path.join(JDK, 'bin', 'jar.exe');
const KEYTOOL = path.join(JDK, 'bin', 'keytool.exe');

function log(...a) { console.log(...a); }
function step(t) { console.log('\n>>> ' + t); }

function run(exe, args, opts = {}) {
  const r = spawnSync(exe, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...opts });
  if (r.error) throw new Error(exe + ' 启动失败: ' + r.error.message);
  const out = (r.stdout || '') + (r.stderr || '');
  if (r.status !== 0) {
    throw new Error(exe + ' 退出码 ' + r.status + '\n' + out.slice(-4000));
  }
  return out;
}

function mkdirp(d) { fs.mkdirSync(d, { recursive: true }); }
function walk(dir, filter) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p, filter));
    else if (!filter || filter(p)) out.push(p);
  }
  return out;
}

// ---------------------------------------------------------------- 0. 检查
step('0/7 检查工具链');
const required = [PLATFORM, path.join(BT, 'aapt2.exe'), path.join(BT, 'lib', 'd8.jar'),
  path.join(BT, 'zipalign.exe'), path.join(BT, 'lib', 'apksigner.jar'), JAVAC];
for (const p of required) {
  if (!fs.existsSync(p)) throw new Error('缺少组件: ' + p);
  log('  ok ' + p);
}

for (const d of [OUT, path.join(OUT, 'compiled'), path.join(OUT, 'gen'), path.join(OUT, 'classes'), path.join(OUT, 'dex')]) {
  mkdirp(d);
}

// ---------------------------------------------------------------- 1. 图标等资源
step('1/7 生成启动图标 / 提示音 / assets');
const PY = 'C:\\Users\\Lenovo\\.dsh\\dsh-runtimes\\dsh-primary-runtime\\dependencies\\python\\python.exe';
run(PY, [path.join(ROOT, 'build', 'make_icons.py')],
  { env: { ...process.env, PYTHONIOENCODING: 'utf-8' } });

// ---------------------------------------------------------------- 2. aapt2 compile
step('2/7 aapt2 compile 资源');
const compiledDir = path.join(OUT, 'compiled');
const flats = [];
for (const f of walk(path.join(ROOT, 'res'), p => /\.(xml|png|wav|webp|jpg)$/i.test(p))) {
  const rel = path.relative(ROOT, f);
  const outFlat = path.join(compiledDir, rel.replace(/[\\/]/g, '_') + '.flat');
  run(path.join(BT, 'aapt2.exe'), ['compile', '-o', outFlat, f]);
  flats.push(outFlat);
}
log('  compiled ' + flats.length + ' 个资源');

// ---------------------------------------------------------------- 3. aapt2 link
step('3/7 aapt2 link 生成资源包');
const resZip = path.join(OUT, 'res.zip');
if (fs.existsSync(resZip)) fs.unlinkSync(resZip);
const linkArgs = ['link', '-o', resZip, '-I', PLATFORM,
  '--manifest', path.join(ROOT, 'AndroidManifest.xml'),
  '--min-sdk-version', '24', '--target-sdk-version', '34',
  '--version-code', '1', '--version-name', '1.0',
  '--java', path.join(OUT, 'gen'),
  '-A', path.join(ROOT, 'assets'),
  '--auto-add-overlay', ...flats];
log(run(path.join(BT, 'aapt2.exe'), linkArgs).trim().slice(-500));

// ---------------------------------------------------------------- 4. javac
step('4/7 javac 编译 Java 源码');
const srcs = walk(path.join(ROOT, 'java'), p => p.endsWith('.java'))
  .concat(walk(path.join(OUT, 'gen'), p => p.endsWith('.java')));
log('  源文件 ' + srcs.length + ' 个');
const javacOut = run(JAVAC, [
  '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-nowarn',
  '-bootclasspath', PLATFORM, '-classpath', PLATFORM,
  '-d', path.join(OUT, 'classes'), ...srcs
]);
if (javacOut.trim()) log(javacOut.trim().slice(-2000));

// ---------------------------------------------------------------- 5. d8
step('5/7 d8 生成 dex');
const classFiles = walk(path.join(OUT, 'classes'), p => p.endsWith('.class'));
log('  class 文件 ' + classFiles.length + ' 个');
const d8Out = run(JAVA, ['-cp', path.join(BT, 'lib', 'd8.jar'), 'com.android.tools.r8.D8',
  '--release', '--min-api', '24', '--lib', PLATFORM,
  '--output', path.join(OUT, 'dex'), ...classFiles]);
if (d8Out.trim()) log(d8Out.trim().slice(-2000));

// ---------------------------------------------------------------- 6. 组装
step('6/7 组装 APK');
const unsigned = path.join(OUT, 'app-unsigned.apk');
const aligned = path.join(OUT, 'app-aligned.apk');
fs.copyFileSync(resZip, unsigned);
run(JAR, ['uf', unsigned, '-C', path.join(OUT, 'dex'), 'classes.dex']);
if (fs.existsSync(aligned)) fs.unlinkSync(aligned);
run(path.join(BT, 'zipalign.exe'), ['-f', '-p', '4', unsigned, aligned]);

// ---------------------------------------------------------------- 7. 签名
step('7/7 签名 APK');
const ks = path.join(ROOT, 'build', 'xizuo.keystore');
if (!fs.existsSync(ks)) {
  run(KEYTOOL, ['-genkeypair', '-keystore', ks, '-alias', 'xizuo',
    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10950',
    '-storepass', 'xizuo123', '-keypass', 'xizuo123',
    '-dname', 'CN=Xizuo Reminder, OU=Student, O=TangXuanqi, L=Hengyang, ST=Hunan, C=CN']);
  log('  已生成签名证书');
}
if (fs.existsSync(APK_FINAL)) fs.unlinkSync(APK_FINAL);
const signOut = run(JAVA, ['-cp', path.join(BT, 'lib', 'apksigner.jar'), 'com.android.apksigner.ApkSignerTool',
  'sign', '--ks', ks, '--ks-key-alias', 'xizuo',
  '--ks-pass', 'pass:xizuo123', '--key-pass', 'pass:xizuo123',
  '--min-sdk-version', '24', '--out', APK_FINAL, aligned]);
if (signOut.trim()) log(signOut.trim().slice(-1000));
const verifyOut = run(JAVA, ['-cp', path.join(BT, 'lib', 'apksigner.jar'), 'com.android.apksigner.ApkSignerTool',
  'verify', '--print-certs', APK_FINAL]);
log(verifyOut.trim());

const st = fs.statSync(APK_FINAL);
console.log('\n================ 打包完成 ================');
console.log('APK : ' + APK_FINAL);
console.log('大小: ' + (st.size / 1048576).toFixed(2) + ' MB');
