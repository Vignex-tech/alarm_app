package com.vignex.alarmclock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

/** Full-screen "alarm is ringing" screen shown over the lock screen. */
public class RingActivity extends Activity {
    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_ring);

        final int id = getIntent().getIntExtra(AlarmScheduler.EXTRA_ID, -1);
        Alarm alarm = AlarmStore.get(this, id);
        TextView label = findViewById(R.id.ring_label);
        label.setText(alarm != null && !alarm.label.isEmpty() ? alarm.label : getString(R.string.alarm));

        findViewById(R.id.btn_snooze).setOnClickListener(v -> {
            sendToService(AlarmService.ACTION_SNOOZE, id);
            finish();
        });
        findViewById(R.id.btn_dismiss).setOnClickListener(v -> {
            sendToService(AlarmService.ACTION_DISMISS, id);
            finish();
        });

        IntentFilter filter = new IntentFilter(AlarmService.ACTION_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }
    }

    private void sendToService(String action, int id) {
        Intent i = new Intent(this, AlarmService.class);
        i.setAction(action);
        i.putExtra(AlarmScheduler.EXTRA_ID, id);
        startService(i);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(stopReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Back must not silently hide a ringing alarm; use Snooze or Dismiss.
    }
}
