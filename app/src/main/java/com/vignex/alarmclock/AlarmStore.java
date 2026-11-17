package com.vignex.alarmclock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists alarms as JSON in SharedPreferences. */
public final class AlarmStore {
    private static final String PREFS = "alarms";
    private static final String KEY_LIST = "list";
    private static final String KEY_NEXT_ID = "next_id";

    private AlarmStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Alarm> getAll(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // corrupt data: start empty
        }
        Collections.sort(out, (a, b) -> a.hour != b.hour ? a.hour - b.hour : a.minute - b.minute);
        return out;
    }

    public static synchronized Alarm get(Context c, int id) {
        for (Alarm a : getAll(c)) {
            if (a.id == id) return a;
        }
        return null;
    }

    public static synchronized void save(Context c, Alarm alarm) {
        List<Alarm> all = getAll(c);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == alarm.id) {
                all.set(i, alarm);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(alarm);
        write(c, all);
    }

    public static synchronized void delete(Context c, int id) {
        List<Alarm> all = getAll(c);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == id) {
                all.remove(i);
                break;
            }
        }
        write(c, all);
    }

    public static synchronized int newId(Context c) {
        SharedPreferences p = prefs(c);
        int id = p.getInt(KEY_NEXT_ID, 1);
        p.edit().putInt(KEY_NEXT_ID, id + 1).commit();
        return id;
    }

    private static void write(Context c, List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) arr.put(a.toJson());
        } catch (JSONException e) {
            return;
        }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).commit();
    }
}
