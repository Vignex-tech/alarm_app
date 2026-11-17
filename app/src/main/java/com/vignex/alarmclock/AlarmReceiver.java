package com.vignex.alarmclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when AlarmManager says an alarm is due. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1);
        boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
        Alarm alarm = AlarmStore.get(context, id);
        if (alarm == null) return; // deleted in the meantime

        if (!snooze) {
            if (alarm.days == 0) {
                alarm.enabled = false; // one-time alarm is done after it rings
                AlarmStore.save(context, alarm);
            } else {
                AlarmScheduler.schedule(context, alarm); // queue next occurrence
            }
        }

        Intent svc = new Intent(context, AlarmService.class);
        svc.setAction(AlarmService.ACTION_START);
        svc.putExtra(AlarmScheduler.EXTRA_ID, id);
        context.startForegroundService(svc);
    }
}
