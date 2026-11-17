package com.vignex.alarmclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Schedules alarms with AlarmManager.setAlarmClock (exact, Doze-exempt, no special permission). */
public final class AlarmScheduler {
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_SNOOZE = "alarm_snooze";
    private static final String ACTION_FIRE = "com.vignex.alarmclock.FIRE";
    private static final int SNOOZE_OFFSET = 100000;

    private AlarmScheduler() {}

    public static void schedule(Context c, Alarm alarm) {
        long t = alarm.nextTrigger(System.currentTimeMillis());
        set(c, t, pending(c, alarm.id, false));
    }

    public static void scheduleSnooze(Context c, int id, int minutes) {
        long t = System.currentTimeMillis() + minutes * 60_000L;
        set(c, t, pending(c, id, true));
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pending(c, id, false));
        am.cancel(pending(c, id, true));
    }

    public static void rescheduleAll(Context c) {
        for (Alarm a : AlarmStore.getAll(c)) {
            if (a.enabled) {
                schedule(c, a);
            } else {
                cancel(c, a.id);
            }
        }
    }

    private static void set(Context c, long triggerAt, PendingIntent operation) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent show = PendingIntent.getActivity(
                c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, show), operation);
    }

    private static PendingIntent pending(Context c, int id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_FIRE);
        i.putExtra(EXTRA_ID, id);
        i.putExtra(EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(
                c, snooze ? id + SNOOZE_OFFSET : id, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
