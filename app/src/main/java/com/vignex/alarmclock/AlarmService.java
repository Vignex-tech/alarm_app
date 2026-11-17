package com.vignex.alarmclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;

import java.util.Date;

/** Foreground service that plays the alarm sound, vibrates, and shows the ringing notification. */
public class AlarmService extends Service {
    public static final String ACTION_START = "com.vignex.alarmclock.START";
    public static final String ACTION_SNOOZE = "com.vignex.alarmclock.SNOOZE";
    public static final String ACTION_DISMISS = "com.vignex.alarmclock.DISMISS";
    public static final String ACTION_STOPPED = "com.vignex.alarmclock.RINGING_STOPPED";

    private static final String CHANNEL_ID = "alarm_ringing";
    private static final int NOTIF_ID = 1001;
    private static final int SNOOZE_MINUTES = 10;
    private static final long MAX_RING_MS = 10 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            finishRinging();
        }
    };

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private int alarmId = -1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_SNOOZE.equals(action)) {
            int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, alarmId);
            if (id >= 0) AlarmScheduler.scheduleSnooze(this, id, SNOOZE_MINUTES);
            finishRinging();
            return START_NOT_STICKY;
        }
        if (ACTION_DISMISS.equals(action)) {
            finishRinging();
            return START_NOT_STICKY;
        }

        // ACTION_START
        alarmId = intent == null ? -1 : intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1);
        Alarm alarm = AlarmStore.get(this, alarmId);
        String title = (alarm != null && !alarm.label.isEmpty())
                ? alarm.label : getString(R.string.alarm);

        createChannel();
        Notification n = buildNotification(title);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        stopRinging();
        startRinging();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_RING_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        stopRinging();
        super.onDestroy();
    }

    private void startRinging() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmclock:ring");
            wakeLock.acquire(MAX_RING_MS + 5000);
        }

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) {
                player.release();
                player = null;
            }
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 700}, 0), attrs);
        }
    }

    private void stopRinging() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // not started
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wakeLock != null) {
            if (wakeLock.isHeld()) wakeLock.release();
            wakeLock = null;
        }
    }

    private void finishRinging() {
        handler.removeCallbacks(timeout);
        stopRinging();
        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // sound is played by MediaPlayer on the alarm stream
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String title) {
        Intent ring = new Intent(this, RingActivity.class);
        ring.putExtra(AlarmScheduler.EXTRA_ID, alarmId);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent ringPi = PendingIntent.getActivity(
                this, 0, ring, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action snooze = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_alarm),
                getString(R.string.snooze), actionIntent(ACTION_SNOOZE, 1)).build();
        Notification.Action dismiss = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_alarm),
                getString(R.string.dismiss), actionIntent(ACTION_DISMISS, 2)).build();

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(DateFormat.getTimeFormat(this).format(new Date()))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(ringPi)
                .setFullScreenIntent(ringPi, true)
                .addAction(snooze)
                .addAction(dismiss)
                .build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent i = new Intent(this, AlarmService.class);
        i.setAction(action);
        i.putExtra(AlarmScheduler.EXTRA_ID, alarmId);
        return PendingIntent.getService(
                this, requestCode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
