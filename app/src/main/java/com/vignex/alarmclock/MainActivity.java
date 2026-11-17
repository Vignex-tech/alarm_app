package com.vignex.alarmclock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int[] DAY_IDS = {
            R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6
    };
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private LinearLayout listLayout;
    private TextView emptyView;
    private TextView nextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listLayout = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);
        nextView = findViewById(R.id.next);
        findViewById(R.id.add).setOnClickListener(v -> showEditor(null));
        installCrashHandler();
        showPendingCrash();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        listLayout.removeAllViews();
        List<Alarm> alarms = AlarmStore.getAll(this);
        emptyView.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);

        for (final Alarm a : alarms) {
            final View row = getLayoutInflater().inflate(R.layout.item_alarm, listLayout, false);
            TextView time = row.findViewById(R.id.row_time);
            TextView desc = row.findViewById(R.id.row_desc);
            Switch sw = row.findViewById(R.id.row_switch);

            time.setText(formatTime(a.hour, a.minute));
            String d = describeDays(a.days);
            desc.setText(a.label.isEmpty() ? d : a.label + " · " + d);
            row.setAlpha(a.enabled ? 1f : 0.5f);

            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener((button, checked) -> {
                a.enabled = checked;
                AlarmStore.save(this, a);
                if (checked) {
                    AlarmScheduler.schedule(this, a);
                    Toast.makeText(this, ringsIn(a.nextTrigger(System.currentTimeMillis())),
                            Toast.LENGTH_SHORT).show();
                } else {
                    AlarmScheduler.cancel(this, a.id);
                }
                row.setAlpha(checked ? 1f : 0.5f);
                updateNext(AlarmStore.getAll(this));
            });

            row.setOnClickListener(v -> showEditor(a));
            listLayout.addView(row);
        }
        updateNext(alarms);
    }

    private void updateNext(List<Alarm> alarms) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (Alarm a : alarms) {
            if (a.enabled) best = Math.min(best, a.nextTrigger(now));
        }
        nextView.setText(best == Long.MAX_VALUE
                ? getString(R.string.no_alarm_set) : ringsIn(best));
    }

    private void showEditor(final Alarm existing) {
        View v = getLayoutInflater().inflate(R.layout.dialog_edit, null);
        final TimePicker picker = v.findViewById(R.id.time_picker);
        final EditText label = v.findViewById(R.id.label);
        picker.setIs24HourView(DateFormat.is24HourFormat(this));

        final int[] days = {existing != null ? existing.days : 0};
        if (existing != null) {
            picker.setHour(existing.hour);
            picker.setMinute(existing.minute);
            label.setText(existing.label);
        }

        for (int i = 0; i < 7; i++) {
            final int bit = i;
            final TextView dayView = v.findViewById(DAY_IDS[i]);
            dayView.setSelected((days[0] & (1 << bit)) != 0);
            dayView.setOnClickListener(view -> {
                days[0] ^= (1 << bit);
                dayView.setSelected((days[0] & (1 << bit)) != 0);
            });
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        picker.clearFocus();
                        Alarm a = existing != null ? existing : new Alarm();
                        if (existing == null) a.id = AlarmStore.newId(this);
                        a.hour = picker.getHour();
                        a.minute = picker.getMinute();
                        a.days = days[0];
                        a.label = label.getText().toString().trim();
                        a.enabled = true;
                        AlarmStore.save(this, a);
                        AlarmScheduler.schedule(this, a);
                        Toast.makeText(this, ringsIn(a.nextTrigger(System.currentTimeMillis())),
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    } catch (Throwable t) {
                        showError(t);
                    }
                });
        if (existing != null) {
            b.setNeutralButton(R.string.delete, (dialog, which) -> {
                AlarmScheduler.cancel(this, existing.id);
                AlarmStore.delete(this, existing.id);
                refresh();
            });
        }
        b.show();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            getSharedPreferences("crash", MODE_PRIVATE).edit()
                    .putString("trace", stackTrace(throwable)).commit();
            if (prev != null) prev.uncaughtException(thread, throwable);
        });
    }

    private void showPendingCrash() {
        String trace = getSharedPreferences("crash", MODE_PRIVATE).getString("trace", null);
        if (trace == null) return;
        getSharedPreferences("crash", MODE_PRIVATE).edit().remove("trace").commit();
        showText("The app crashed last time", trace);
    }

    private void showError(Throwable t) {
        showText("Something went wrong", stackTrace(t));
    }

    private void showText(String title, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setPadding(32, 16, 32, 16);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title).setView(sv).setPositiveButton("OK", null).show();
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String out = sw.toString();
        return out.length() > 3000 ? out.substring(0, 3000) : out;
    }

    private String formatTime(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return DateFormat.getTimeFormat(this).format(new Date(c.getTimeInMillis()));
    }

    private String describeDays(int days) {
        if (days == 0) return getString(R.string.once);
        if (days == 0x7F) return getString(R.string.every_day);
        if (days == 0x1F) return getString(R.string.weekdays);
        if (days == 0x60) return getString(R.string.weekends);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(DAY_NAMES[i]);
            }
        }
        return sb.toString();
    }

    private String ringsIn(long triggerAt) {
        long mins = Math.max(1, (triggerAt - System.currentTimeMillis() + 59_999) / 60_000);
        long d = mins / 1440, h = (mins % 1440) / 60, m = mins % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0 || d > 0) sb.append(h).append("h ");
        sb.append(m).append("m");
        return getString(R.string.rings_in, sb.toString());
    }
}
