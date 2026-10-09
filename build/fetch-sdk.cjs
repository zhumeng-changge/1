// 下载并解压 Android SDK 的 command-line tools + platform（用于离线手动打包 APK）
// 只依赖 Node 内置模块，不依赖 npm 安装。
const fs = require('fs');
const path = require('path');
const { pipeline } = require('stream/promises');
const { spawn } = require('child_process');

const NODE = process.execPath;
const ROOT = process.argv[2] || path.join(process.cwd(), 'android-sdk');
const TEMP = path.join(ROOT, '_tmp');
fs.mkdirSync(TEMP, { recursive: true });

const CLT_URL = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip';
const PLATFORM_URL = 'https://dl.google.com/android/repository/platform-34-ext7_r03.zip';

function log(...a) { console.log(new Date().toISOString().slice(11, 19), ...a); }

async function download(url, dest, label) {
  if (fs.existsSync(dest) && fs.statSync(dest).size > 1024 * 1024) {
    log(label, 'exists, skip:', (fs.statSync(dest).size / 1048576).toFixed(1) + 'MB');
    return dest;
  }
  log('downloading', label, url);
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP ' + res.status + ' for ' + url);
  const total = Number(res.headers.get('content-length') || 0);
  let got = 0, last = 0;
  const out = fs.createWriteStream(dest);
  const reader = res.body.getReader();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      got += value.length;
      if (!out.write(Buffer.from(value))) await new Promise(r => out.once('drain', r));
      if (got - last > 8 * 1048576) { last = got; log('  ', label, (got / 1048576).toFixed(1) + 'MB' + (total ? ' / ' + (total / 1048576).toFixed(1) + 'MB' : '')); }
    }
  } finally {
    await new Promise(r => out.end(r));
  }
  log('done', label, (got / 1048576).toFixed(1) + 'MB');
  return dest;
}

function extract(zip, destDir) {
  fs.mkdirSync(destDir, { recursive: true });
  return new Promise((resolve, reject) => {
    // 用 JDK 自带的 jar 解压：对 zip 兼容性最好，且无需额外下载
    const jar = path.join(process.env.JAVA_HOME || 'C:\\Program Files\\BellSoft\\LibericaJDK-25', 'bin', 'jar.exe');
    const p = spawn(jar, ['xf', zip], { cwd: destDir, stdio: ['ignore', 'ignore', 'pipe'] });
    let err = '';
    p.stderr.on('data', d => { err += d.toString(); });
    p.on('error', reject);
    p.on('close', code => code === 0 ? resolve() : reject(new Error('jar xf exit ' + code + ' ' + err)));
  });
}

(async () => {
  const cltZip = path.join(TEMP, 'clt.zip');
  await download(CLT_URL, cltZip, 'command-line-tools');
  log('extracting command-line tools...');
  await extract(cltZip, ROOT);
  // zip 内是 cmdline-tools/ 目录，sdkmanager 需要放在 cmdline-tools/latest/
  const inner = path.join(ROOT, 'cmdline-tools');
  const latest = path.join(inner, 'latest');
  if (fs.existsSync(inner) && !fs.existsSync(latest)) {
    fs.mkdirSync(latest, { recursive: true });
    for (const e of fs.readdirSync(inner)) {
      if (e === 'latest') continue;
      fs.renameSync(path.join(inner, e), path.join(latest, e));
    }
    log('moved cmdline-tools/* -> cmdline-tools/latest/');
  }
  log('cmdline-tools ready:', fs.existsSync(path.join(latest, 'bin', 'sdkmanager.bat')));

  const platZip = path.join(TEMP, 'platform-34.zip');
  await download(PLATFORM_URL, platZip, 'platform-34');
  const platDir = path.join(ROOT, 'platforms', 'android-34');
  log('extracting platform 34 ->', platDir);
  await extract(platZip, platDir);
  const jarPath = path.join(platDir, 'android.jar');
  log('android.jar:', fs.existsSync(jarPath), fs.existsSync(jarPath) ? (fs.statSync(jarPath).size / 1048576).toFixed(1) + 'MB' : '');
  log('ALL DONE');
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
