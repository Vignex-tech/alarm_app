package com.vignex.alarmclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-registers alarms after reboot, app update, or a clock/timezone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
    }
}
