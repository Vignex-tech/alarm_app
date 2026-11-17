package com.vignex.alarmclock;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** One alarm. {@code days} is a bitmask: bit 0 = Monday ... bit 6 = Sunday, 0 = ring once. */
public class Alarm {
    public int id;
    public int hour;
    public int minute;
    public int days;
    public String label = "";
    public boolean enabled = true;

    /** Next time (epoch millis) strictly after {@code now} that this alarm should ring. */
    public long nextTrigger(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            int bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0 ... Sun=6
            boolean dayOk = days == 0 || (days & (1 << bit)) != 0;
            if (dayOk && c.getTimeInMillis() > now) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("days", days);
        o.put("label", label);
        o.put("enabled", enabled);
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id");
        a.hour = o.optInt("hour");
        a.minute = o.optInt("minute");
        a.days = o.optInt("days");
        a.label = o.optString("label", "");
        a.enabled = o.optBoolean("enabled", true);
        return a;
    }
}
