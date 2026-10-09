package com.tangxuanqi.xizuo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.PowerManager;

/** 闹钟到点：弹全屏提醒页 + 系统通知 + 声音震动 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "xizuo_alarm";

    /** 持有唤醒锁期间的释放任务（具名嵌套类，不用匿名内部类） */
    private static final class ReleaseTask implements Runnable {
        private final PowerManager.WakeLock lock;
        ReleaseTask(PowerManager.WakeLock lock) { this.lock = lock; }
        @Override public void run() {
            try {
                if (lock != null && lock.isHeld()) lock.release();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE);
        String desc = intent.getStringExtra(AlarmScheduler.EXTRA_DESC);
        long at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis());
        int kind = intent.getIntExtra(AlarmScheduler.EXTRA_KIND, 0);
        if (title == null || title.length() == 0) title = "作息提醒";
        if (desc == null) desc = "";

        // 屏幕可能刚灭，先短暂唤醒 CPU，保证能弹出来
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            try {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xizuo:alarm");
                wl.acquire(15000);
            } catch (Throwable ignored) {}
        }

        notifyUser(context, title, desc, at, kind);
        startAlarmActivity(context, title, desc, at, kind);

        if (wl != null) {
            try { wl.release(); } catch (Throwable ignored) {}
        }
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "作息提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("上课、作息与 Python 学习提醒");
        ch.enableVibration(true);
        ch.setShowBadge(true);
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        try {
            ch.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build());
        } catch (Throwable ignored) {}
        nm.createNotificationChannel(ch);
    }

    private static int piFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void notifyUser(Context c, String title, String desc, long at, int kind) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(c);

        Intent full = new Intent(c, AlarmActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        full.putExtra(AlarmScheduler.EXTRA_TITLE, title);
        full.putExtra(AlarmScheduler.EXTRA_DESC, desc);
        full.putExtra(AlarmScheduler.EXTRA_AT, at);
        full.putExtra(AlarmScheduler.EXTRA_KIND, kind);
        PendingIntent fullPi = PendingIntent.getActivity(c,
                Math.abs(title.hashCode()) % 5000 + 100, full, piFlags());

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(c, CHANNEL_ID);
        else b = new Notification.Builder(c);

        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(desc)
                .setStyle(new Notification.BigTextStyle().bigText(desc))
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(fullPi);

        if (Build.VERSION.SDK_INT >= 21) {
            b.setPriority(Notification.PRIORITY_MAX);
            b.setFullScreenIntent(fullPi, true);
        }
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            b.setVibrate(new long[]{0, 400, 200, 400, 200, 600});
        }

        try {
            nm.notify(categoryId(kind), b.build());
        } catch (Throwable ignored) {}
    }

    public static int categoryId(int kind) {
        switch (kind) {
            case 1: return 2101;   // 上课
            case 2: return 2102;   // Python
            case 3: return 2103;   // 作息
            default: return 2100;
        }
    }

    private void startAlarmActivity(Context c, String title, String desc, long at, int kind) {
        try {
            Intent i = new Intent(c, AlarmActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            i.putExtra(AlarmScheduler.EXTRA_TITLE, title);
            i.putExtra(AlarmScheduler.EXTRA_DESC, desc);
            i.putExtra(AlarmScheduler.EXTRA_AT, at);
            i.putExtra(AlarmScheduler.EXTRA_KIND, kind);
            c.startActivity(i);
        } catch (Throwable ignored) {
            // Android 10+ 后台启动界面受限；此时依赖上面的全屏通知
        }
    }
}
