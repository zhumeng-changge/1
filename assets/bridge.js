/* ============================================================
 * APK 专用桥接脚本：把网页端算好的提醒清单交给手机的系统闹钟，
 * 这样即使应用被关掉、手机重启，到点也会响。
 * 没有原生环境（用浏览器打开）时全部自动退化为应用内提醒。
 * ============================================================ */
(function () {
  var NA = window.AndroidAlarm;
  var IS_APP = !!NA;

  function call(fn, arg, fallback) {
    if (!NA || typeof NA[fn] !== 'function') return fallback;
    try { return NA[fn](arg); } catch (e) { return fallback; }
  }

  function say(msg) {
    try {
      if (typeof toast === 'function') { toast(msg); return; }
    } catch (e) {}
    try { console.log('[xizuo] ' + msg); } catch (e) {}
  }

  /** 一次点击依次补齐：通知 → 精确闹钟 → 全屏提醒 → 电池优化 */
  function fixNext() {
    if (!IS_APP) return;
    if (!window.XizuoAndroid.canNotify()) { call('openNotificationSettings'); say('第 1 步：请打开「允许通知」，完成后回来再点一次'); return; }
    if (!window.XizuoAndroid.canExact()) { call('openExactAlarmSettings'); say('第 2 步：请允许「闹钟和提醒」，完成后回来再点一次'); return; }
    if (!window.XizuoAndroid.canFullScreen()) { call('openFullScreenSettings'); say('第 3 步：请允许「全屏通知」，完成后回来再点一次'); return; }
    if (!window.XizuoAndroid.ignoringBattery()) { call('openBatterySettings'); say('第 4 步：建议把本应用加入电池优化白名单'); return; }
    say('全部就绪：锁屏、关掉应用都会按时提醒');
  }

  // ---------- 1. 把提醒交给系统闹钟（每 30 秒最多同步一次，避免频繁调用） ----------
  var lastSig = '';
  var lastPush = 0;

  function push(force) {
    if (!NA || typeof buildEvents !== 'function') return;
    var now = Date.now();
    if (!force && now - lastPush < 30000) return;
    var evs;
    try { evs = buildEvents(new Date()); } catch (e) { return; }

    var out = [];
    evs.forEach(function (e) {
      var at = new Date();
      at.setHours(0, 0, 0, 0);
      at = at.getTime() + e.at * 60000;          // 今天 00:00 + 分钟数
      if (at <= now + 5000) return;              // 已经过点的不用排
      out.push({ id: e.id, at: at, title: e.title, desc: e.desc || '', kind: kindOf(e) });
    });
    // 顺便把明天的前几条也排上，避免凌晨手机没开过应用就漏掉 6:50 起床
    try {
      var evs2 = buildEvents(new Date(Date.now() + 86400000));
      evs2.forEach(function (e) {
        var at = new Date(Date.now() + 86400000);
        at.setHours(0, 0, 0, 0);
        at = at.getTime() + e.at * 60000;
        if (at <= now + 5000) return;
        out.push({ id: 'tom-' + e.id, at: at, title: e.title, desc: e.desc || '', kind: kindOf(e) });
      });
    } catch (e) {}

    out.sort(function (a, b) { return a.at - b.at; });
    out = out.slice(0, 60);

    var sig = out.map(function (o) { return o.id + ':' + o.at; }).join('|');
    if (!force && sig === lastSig) return;
    lastSig = sig; lastPush = now;
    try { NA.schedule(JSON.stringify(out)); } catch (e) {}
  }

  function kindOf(e) {
    if (e.kind === 'cls') return 1;
    if (e.kind === 'py') return 2;
    if (e.kind === 'fix') return 3;
    return 0;
  }

  window.XizuoAndroid = {
    isApp: IS_APP,
    sync: push,
    next: function () { return call('next', null, null); },
    count: function () { return call('scheduledCount', null, 0); },
    canNotify: function () { return call('canNotify', null, false); },
    canExact: function () { return call('canExactAlarm', null, true); },
    canFullScreen: function () { return call('canFullScreen', null, true); },
    ignoringBattery: function () { return call('isIgnoringBattery', null, false); },
    openNotificationSettings: function () { call('openNotificationSettings'); },
    openExactSettings: function () { call('openExactAlarmSettings'); },
    openFullScreenSettings: function () { call('openFullScreenSettings'); },
    openBatterySettings: function () { call('openBatterySettings'); },
    keepAwake: function (on) { call('keepAwake', !!on); },
    info: function () { return call('info', null, null); }
  };

  // 应用回到前台时立刻同步一次
  window.onNativeResume = function () { push(true); renderNativeState(); };

  // 首次同步：等页面把课表渲染出来
  function boot() {
    if (!IS_APP) return;
    push(true);
    setTimeout(renderNativeState, 1200);
    setInterval(function () { push(false); }, 30000);
  }

  // ---------- 2. 在设置页里显示手机端状态 ----------
  function el(id) { return document.getElementById(id); }

  function renderNativeState() {
    var box = el('androidState');
    if (!box) return;
    if (!IS_APP) {
      box.innerHTML = '当前是<b>浏览器网页版</b>：提醒只在页面打开时生效。' +
        '把 APK 装的版本打开，才会写入手机系统闹钟，关掉应用也会响。';
      return;
    }
    var n = window.XizuoAndroid.count();
    var nx = window.XizuoAndroid.next();
    var notify = window.XizuoAndroid.canNotify();
    var exact = window.XizuoAndroid.canExact();
    var full = window.XizuoAndroid.canFullScreen();
    var bat = window.XizuoAndroid.ignoringBattery();
    var info = window.XizuoAndroid.info();
    var ready = notify && exact && full && bat;
    var html = '';
    html += '系统闹钟已写入：<b>' + n + '</b> 条（今天 + 明天）';
    if (nx && nx.at) html += '<br>下一条：<b>' + nx.time + '　' + nx.title + '</b>';
    html += '<br>通知权限：<b>' + (notify ? '已开启 ✓' : '未开启') + '</b>';
    html += '　｜　精确闹钟：<b>' + (exact ? '正常 ✓' : '受限') + '</b>';
    html += '<br>全屏提醒：<b>' + (full ? '允许 ✓' : '未允许') + '</b>';
    html += '　｜　电池优化：<b>' + (bat ? '已忽略 ✓' : '未忽略') + '</b>';
    if (info) html += '<br>设备：' + info.model + '（Android SDK ' + info.sdk + '）';
    html += ready
      ? '<br><b style="color:#34d399">四项都就绪：应用被关掉或锁屏时也会按时响。</b>'
      : '<br><b style="color:#fbbf24">还有设置没开：没开通知时，锁屏/退出应用可能不响（应用内提醒不受影响）。</b>';
    box.innerHTML = html;

    var b1 = el('btnFixAll');
    if (b1) b1.textContent = ready ? '状态正常 · 点此重新检查' : '一键完成剩余设置（点 1-4 次）';
    var bn = el('btnOpenNotif');
    if (bn) bn.textContent = notify ? '通知已开启 · 点此调整' : '① 去开启通知（锁屏提醒必需）';
    var be = el('btnOpenExact');
    if (be) be.textContent = exact ? '精确闹钟可用 ✓' : '② 去允许精确闹钟';
    var bf = el('btnOpenFull');
    if (bf) bf.textContent = full ? '全屏提醒已允许 ✓' : '③ 去允许全屏提醒';
    var bb = el('btnOpenBattery');
    if (bb) bb.textContent = bat ? '电池优化已忽略 ✓' : '④ 去关闭电池优化（更可靠）';
  }

  window.XizuoRefreshNative = renderNativeState;

  // ---------- 3. 在设置页插入「手机端」卡片 ----------
  function injectCard() {
    var host = document.querySelector('#v-set');
    if (!host || el('androidCard')) return;
    var card = document.createElement('div');
    card.className = 'card';
    card.id = 'androidCard';
    card.innerHTML =
      '<h2>手机端（APK 专属）<span class="tag">系统闹钟</span></h2>' +
      '<div class="hint" id="androidState">正在读取手机状态…</div>' +
      '<button class="btn primary wide" id="btnFixAll">一键完成剩余设置</button>' +
      '<button class="btn ghost wide" id="btnSyncAlarms">立即重新写入系统闹钟</button>' +
      '<button class="btn ghost wide" id="btnOpenNotif">① 去开启通知</button>' +
      '<button class="btn ghost wide" id="btnOpenExact">② 去允许精确闹钟</button>' +
      '<button class="btn ghost wide" id="btnOpenFull">③ 去允许全屏提醒</button>' +
      '<button class="btn ghost wide" id="btnOpenBattery">④ 去关闭电池优化</button>' +
      '<div class="hint" style="margin-top:10px">' +
      '<b>为什么要点这几下？</b>安卓不允许应用自己给自己开通知/精确闹钟/后台自启，必须由你在系统设置里点一次。' +
      '点完就永久生效，以后不用再管。若都不点：应用打开时提醒照常（弹屏+响铃），但锁屏或应用被关掉时系统可能拦截。' +
      '</div>';
    host.insertBefore(card, host.firstChild);

    el('btnFixAll').onclick = function () { fixNext(); setTimeout(renderNativeState, 900); };
    el('btnSyncAlarms').onclick = function () { push(true); renderNativeState(); say('已重新写入 ' + window.XizuoAndroid.count() + ' 条系统闹钟'); };
    el('btnOpenNotif').onclick = function () { window.XizuoAndroid.openNotificationSettings(); };
    el('btnOpenExact').onclick = function () { window.XizuoAndroid.openExactSettings(); };
    el('btnOpenFull').onclick = function () { window.XizuoAndroid.openFullScreenSettings(); };
    el('btnOpenBattery').onclick = function () { window.XizuoAndroid.openBatterySettings(); };
  }

  // ---------- 4. 让原有的「保持屏幕常亮」在 APK 里真正生效 ----------
  function hookWakeLock() {
    if (!IS_APP) return;
    var sw = document.querySelector('.sw[data-sw="wakelock"]');
    if (!sw) return;
    sw.addEventListener('click', function () {
      setTimeout(function () {
        var on = sw.classList.contains('on');
        window.XizuoAndroid.keepAwake(on);
      }, 60);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { injectCard(); hookWakeLock(); boot(); });
  } else {
    injectCard(); hookWakeLock(); boot();
  }
})();
