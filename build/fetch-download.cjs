// 通用下载器：node fetch-download.cjs <url> <输出文件>
const fs = require('fs');
const path = require('path');

const url = process.argv[2];
const dest = process.argv[3];
if (!url || !dest) { console.error('用法: node fetch-download.cjs <url> <dest>'); process.exit(2); }

(async () => {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  if (fs.existsSync(dest) && fs.statSync(dest).size > 1024 * 1024) {
    console.log('已存在，跳过:', (fs.statSync(dest).size / 1048576).toFixed(1) + 'MB');
    return;
  }
  console.log('下载', url);
  const res = await fetch(url);
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const total = Number(res.headers.get('content-length') || 0);
  const out = fs.createWriteStream(dest + '.part');
  const reader = res.body.getReader();
  let got = 0, last = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    got += value.length;
    if (!out.write(Buffer.from(value))) await new Promise(r => out.once('drain', r));
    if (got - last > 10 * 1048576) {
      last = got;
      console.log('  ' + (got / 1048576).toFixed(1) + 'MB' + (total ? ' / ' + (total / 1048576).toFixed(1) + 'MB' : ''));
    }
  }
  await new Promise(r => out.end(r));
  fs.renameSync(dest + '.part', dest);
  console.log('完成:', dest, (got / 1048576).toFixed(1) + 'MB');
})().catch(e => { console.error('失败:', e.message); process.exit(1); });
