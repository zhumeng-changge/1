package com.tangxuanqi.xizuo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.List;

/**
 * 闹钟调度：把网页端算好的提醒交给系统 AlarmManager。
 * 用 setAlarmClock —— 系统闹钟级别的精确闹钟，Android 12+ 无需「精确闹钟」权限。
 */
public final class AlarmScheduler {

    public static final String PREFS = "xizuo";
    public static final String KEY_ALARMS = "alarms_json";
    public static final String KEY_LAST_DAY = "last_day";

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_AT = "at";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DESC = "desc";
    public static final String EXTRA_KIND = "kind";

    private AlarmScheduler() {}

    public static AlarmManager am(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static int piFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    public static Intent alarmIntent(Context c, AlarmItem it) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("com.tangxuanqi.xizuo.ALARM." + it.key());
        i.putExtra(EXTRA_ID, it.id);
        i.putExtra(EXTRA_AT, it.at);
        i.putExtra(EXTRA_TITLE, it.title);
        i.putExtra(EXTRA_DESC, it.desc);
        i.putExtra(EXTRA_KIND, it.kind);
        return i;
    }

    public static PendingIntent intentFor(Context c, AlarmItem it) {
        return PendingIntent.getBroadcast(c, requestCode(it), alarmIntent(c, it), piFlags());
    }

    private static int requestCode(AlarmItem it) {
        return Math.abs(it.key().hashCode()) % 100000;
    }

    public static void set(Context c, AlarmItem it) {
        AlarmManager m = am(c);
        if (m == null) return;
        PendingIntent pi = intentFor(c, it);
        try {
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, requestCode(it) + 7, show, piFlags());
            m.setAlarmClock(new AlarmManager.AlarmClockInfo(it.at, showPi), pi);
        } catch (Throwable t) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.at, pi);
                } else {
                    m.setExact(AlarmManager.RTC_WAKEUP, it.at, pi);
                }
            } catch (Throwable t2) {
                m.set(AlarmManager.RTC_WAKEUP, it.at, pi);
            }
        }
    }

    public static void cancel(Context c, AlarmItem it) {
        AlarmManager m = am(c);
        if (m == null) return;
        m.cancel(intentFor(c, it));
    }

    public static void save(Context c, List<AlarmItem> items, String rawJson) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ALARMS, rawJson == null ? "[]" : rawJson).apply();
    }

    public static String loadRaw(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALARMS, "[]");
    }

    /** 按已保存清单重新排一遍（开机 / 应用启动时） */
    public static int rescheduleAll(Context c) {
        List<AlarmItem> items = Json.parseItems(loadRaw(c));
        long now = System.currentTimeMillis();
        int n = 0;
        for (AlarmItem it : items) {
            if (it.at > now) { set(c, it); n++; }
        }
        return n;
    }

    /** 所有未来闹钟里最近的一个 */
    public static AlarmItem next(Context c) {
        List<AlarmItem> items = Json.parseItems(loadRaw(c));
        long now = System.currentTimeMillis();
        AlarmItem best = null;
        for (AlarmItem it : items) {
            if (it.at > now && (best == null || it.at < best.at)) best = it;
        }
        return best;
    }

    public static int count(Context c) {
        List<AlarmItem> items = Json.parseItems(loadRaw(c));
        long now = System.currentTimeMillis();
        int n = 0;
        for (AlarmItem it : items) if (it.at > now) n++;
        return n;
    }

    public static void snooze(Context c, String title, String desc, int kind, long delayMs) {
        AlarmItem it = new AlarmItem();
        it.id = "snooze";
        it.title = title;
        it.desc = desc;
        it.kind = kind;
        it.at = System.currentTimeMillis() + delayMs;
        set(c, it);
    }

    public static String hhmm(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return String.format(java.util.Locale.CHINA, "%02d:%02d",
                Integer.valueOf(cal.get(java.util.Calendar.HOUR_OF_DAY)),
                Integer.valueOf(cal.get(java.util.Calendar.MINUTE)));
    }
}
