package com.tangxuanqi.xizuo;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/** 全屏提醒页：锁屏上也能显示，带声音与震动 */
public class AlarmActivity extends Activity {

    private PowerManager.WakeLock screenLock;
    private Ringtone ringtone;
    private MediaPlayer player;
    private Vibrator vibrator;
    private Handler handler;
    private TextView clockView;

    private String title = "作息提醒";
    private String desc = "";
    private long at = System.currentTimeMillis();
    private int kind = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readIntent(getIntent());

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        buildUi();
        acquire();
        playAlarm();

        handler = new Handler(Looper.getMainLooper());
        handler.post(new ClockTask(this));
    }

    private void readIntent(Intent i) {
        if (i == null) return;
        String t = i.getStringExtra(AlarmScheduler.EXTRA_TITLE);
        String d = i.getStringExtra(AlarmScheduler.EXTRA_DESC);
        long a = i.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis());
        int k = i.getIntExtra(AlarmScheduler.EXTRA_KIND, 0);
        if (t != null && t.length() > 0) title = t;
        if (d != null) desc = d;
        at = a;
        kind = k;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readIntent(intent);
        buildUi();
    }

    /** 每秒刷新大时钟（具名嵌套类，不用匿名内部类） */
    private static final class ClockTask implements Runnable {
        private final AlarmActivity host;
        ClockTask(AlarmActivity host) { this.host = host; }
        @Override public void run() {
            if (host.clockView != null) {
                Calendar c = Calendar.getInstance();
                host.clockView.setText(String.format(Locale.CHINA, "%02d:%02d",
                        Integer.valueOf(c.get(Calendar.HOUR_OF_DAY)),
                        Integer.valueOf(c.get(Calendar.MINUTE))));
            }
            if (host.handler != null) host.handler.postDelayed(this, 1000);
        }
    }

    private static final class DismissClick implements View.OnClickListener {
        private final AlarmActivity host;
        DismissClick(AlarmActivity host) { this.host = host; }
        @Override public void onClick(View v) { host.stopAll(); host.finish(); }
    }

    private static final class SnoozeClick implements View.OnClickListener {
        private final AlarmActivity host;
        SnoozeClick(AlarmActivity host) { this.host = host; }
        @Override public void onClick(View v) {
            AlarmScheduler.snooze(host, host.title, host.desc, host.kind, 5 * 60 * 1000L);
            host.stopAll();
            host.finish();
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private static GradientDrawable round(int color, float radius, int strokeW, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        return d;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(46), dp(26), dp(30));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1B2450, 0xFF0B0F1E}));

        TextView lbl = new TextView(this);
        String lblText = kind == 1 ? "上课提醒" : (kind == 2 ? "PYTHON 时间" : (kind == 3 ? "作息提醒" : "提醒"));
        lbl.setText(lblText + "  ·  " + AlarmScheduler.hhmm(at));
        lbl.setTextColor(0xFFFFD76A);
        lbl.setTextSize(13);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(lbl);

        clockView = new TextView(this);
        clockView.setText("--:--");
        clockView.setTextColor(0xFFEEF2FF);
        clockView.setTextSize(56);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setPadding(0, dp(12), 0, dp(4));
        root.addView(clockView);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(25);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(0xFFB9C4E6);
        d.setTextSize(14);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, dp(16), 0, dp(28));
        root.addView(d);

        Button ok = new Button(this);
        ok.setText("知道了");
        ok.setTextSize(16);
        ok.setAllCaps(false);
        ok.setTextColor(0xFF08122B);
        ok.setBackground(round(0xFF5B8CFF, dp(14), 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        okLp.setMargins(0, 0, 0, dp(10));
        ok.setLayoutParams(okLp);
        ok.setOnClickListener(new DismissClick(this));
        root.addView(ok);

        Button sn = new Button(this);
        sn.setText("稍后 5 分钟");
        sn.setTextSize(15);
        sn.setAllCaps(false);
        sn.setTextColor(0xFFEEF2FF);
        sn.setBackground(round(0xFF232C4C, dp(14), dp(1), 0xFF3D4A7D));
        sn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        sn.setOnClickListener(new SnoozeClick(this));
        root.addView(sn);

        TextView tip = new TextView(this);
        tip.setText("锁屏上也能看到这条提醒；提前量可在应用「设置」里调整。");
        tip.setTextColor(0xFF7C87AC);
        tip.setTextSize(11);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(18), 0, 0);
        root.addView(tip);

        setContentView(root);
    }

    private void acquire() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                screenLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP, "xizuo:screen");
                screenLock.acquire(10 * 60 * 1000L);
            }
        } catch (Throwable ignored) {}
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void playAlarm() {
        try {
            MediaPlayer mp = new MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alarm);
            if (afd != null) {
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
            } else {
                mp.release();
            }
        } catch (Throwable t) {
            if (player != null) {
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
        if (player == null) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                ringtone = RingtoneManager.getRingtone(this, uri);
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    ringtone.play();
                }
            } catch (Throwable ignored) {}
        }
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 420, 220, 420, 220, 700};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void stopAll() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Throwable ignored) {}
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Throwable ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
        try { if (screenLock != null && screenLock.isHeld()) screenLock.release(); } catch (Throwable ignored) {}
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(AlarmReceiver.categoryId(kind));
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopAll();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        stopAll();
        super.onBackPressed();
    }
}
