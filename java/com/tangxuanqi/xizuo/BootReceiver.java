package com.tangxuanqi.xizuo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 应用更新后重新把闹钟排上，保证手机重启也不会漏提醒 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            AlarmReceiver.ensureChannel(context);
            AlarmScheduler.rescheduleAll(context);
        } catch (Throwable ignored) {}
    }
}
