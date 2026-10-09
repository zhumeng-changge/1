package com.tangxuanqi.xizuo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 主界面：本地加载打包好的作息应用（完全离线、手机独立运行） */
public class MainActivity extends Activity {

    private WebView web;
    private long lastScheduleDay = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(0xFF0D1120);
            getWindow().setNavigationBarColor(0xFF0D1120);
        }

        AlarmReceiver.ensureChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1120);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new ChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidAlarm");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        try { AlarmScheduler.rescheduleAll(this); } catch (Throwable ignored) {}
    }

    private static final class ChromeClient extends WebChromeClient {
        @Override public boolean onConsoleMessage(ConsoleMessage cm) { return true; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long day = System.currentTimeMillis() / 86400000L;
        if (day != lastScheduleDay) {
            lastScheduleDay = day;
            try { AlarmScheduler.rescheduleAll(this); } catch (Throwable ignored) {}
        }
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    /** 在界面线程上取一个布尔结果 */
    private boolean onUiBool(BooleanSupplier task) {
        final AtomicBoolean out = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(new ResultTask(task, out, latch));
        try { latch.await(1500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        return out.get();
    }

    private interface BooleanSupplier { boolean get(); }

    private static final class ResultTask implements Runnable {
        private final BooleanSupplier task;
        private final AtomicBoolean out;
        private final CountDownLatch latch;
        ResultTask(BooleanSupplier task, AtomicBoolean out, CountDownLatch latch) {
            this.task = task; this.out = out; this.latch = latch;
        }
        @Override public void run() {
            try { out.set(task.get()); } catch (Throwable ignored) {}
            latch.countDown();
        }
    }

    private void onUi(Runnable task) {
        runOnUiThread(task);
    }

    /** 网页端 ↔ 原生侧 的桥（方法均在界面线程安全调用） */
    private class Bridge {

        @JavascriptInterface
        public void schedule(final String json) {
            final String payload = json == null ? "[]" : json;
            onUi(new Runnable() {
                @Override public void run() {
                    List<AlarmItem> items = Json.parseItems(payload);
                    long now = System.currentTimeMillis();
                    for (AlarmItem it : items) {
                        if (it.at > now) AlarmScheduler.set(MainActivity.this, it);
                    }
                    AlarmScheduler.save(MainActivity.this, items, payload);
                    lastScheduleDay = now / 86400000L;
                }
            });
        }

        @JavascriptInterface
        public int scheduledCount() {
            return AlarmScheduler.count(MainActivity.this);
        }

        @JavascriptInterface
        public String next() {
            AlarmItem it = AlarmScheduler.next(MainActivity.this);
            if (it == null) return "{\"title\":\"\",\"at\":0,\"time\":\"\"}";
            return "{\"title\":\"" + Json.esc(it.title) + "\",\"at\":" + it.at
                    + ",\"time\":\"" + AlarmScheduler.hhmm(it.at) + "\"}";
        }

        @JavascriptInterface
        public boolean canNotify() {
            return onUiBool(new BooleanSupplier() {
                @Override public boolean get() {
                    try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                                    == PackageManager.PERMISSION_GRANTED;
                        }
                        android.app.NotificationManager nm = (android.app.NotificationManager)
                                getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                        return nm == null || nm.areNotificationsEnabled();
                    } catch (Throwable t) {
                        return false;
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean canExactAlarm() {
            return onUiBool(new BooleanSupplier() {
                @Override public boolean get() {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) {
                            android.app.AlarmManager am = (android.app.AlarmManager)
                                    getSystemService(android.content.Context.ALARM_SERVICE);
                            return am == null || am.canScheduleExactAlarms();
                        }
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isIgnoringBattery() {
            return onUiBool(new BooleanSupplier() {
                @Override public boolean get() {
                    try {
                        PowerManager pm = (PowerManager)
                                getSystemService(android.content.Context.POWER_SERVICE);
                        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
                    } catch (Throwable t) {
                        return false;
                    }
                }
            });
        }

        /** Android 14 起「全屏提醒」是单独开关，直接跳到该应用的这一页 */
        @JavascriptInterface
        public boolean canFullScreen() {
            return onUiBool(new BooleanSupplier() {
                @Override public boolean get() {
                    try {
                        if (Build.VERSION.SDK_INT >= 34) {
                            android.app.NotificationManager nm = (android.app.NotificationManager)
                                    getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                            return nm == null || nm.canUseFullScreenIntent();
                        }
                        return true;
                    } catch (Throwable t) {
                        return true;
                    }
                }
            });
        }

        @JavascriptInterface
        public void openFullScreenSettings() {
            onUi(new SettingsTask("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"));
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            onUi(new SettingsTask(Settings.ACTION_APP_NOTIFICATION_SETTINGS));
        }

        @JavascriptInterface
        public void openExactAlarmSettings() {
            if (Build.VERSION.SDK_INT >= 31) {
                onUi(new SettingsTask(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }

        @JavascriptInterface
        public void openBatterySettings() {
            onUi(new SettingsTask(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            onUi(new Runnable() {
                @Override public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public String info() {
            return "{\"version\":\"1.0\",\"sdk\":" + Build.VERSION.SDK_INT
                    + ",\"model\":\"" + Json.esc(Build.MODEL) + "\"}";
        }
    }

    /** 跳到系统设置页（带包名，尽量直达本应用） */
    private final class SettingsTask implements Runnable {
        private final String action;
        SettingsTask(String action) { this.action = action; }
        @Override public void run() {
            try {
                Intent i = new Intent(action);
                if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)) {
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                }
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }
    }
}
