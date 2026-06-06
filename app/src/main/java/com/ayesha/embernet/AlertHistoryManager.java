package com.ayesha.embernet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class AlertHistoryManager {

    private static final String PREFS_NAME = "embernet_alerts";
    private static final String KEY_ALERTS = "alert_history";
    private static final int    MAX_ALERTS = 50;

    private final SharedPreferences prefs;

    public AlertHistoryManager(Context context) {
        prefs = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Save a new alert
    public void save(SOSMessage message) {
        List<SOSMessage> existing = getAll();

        // Avoid duplicates by messageId
        for (SOSMessage m : existing) {
            if (m.messageId.equals(message.messageId)) {
                return;
            }
        }

        existing.add(0, message); // newest first

        if (existing.size() > MAX_ALERTS) {
            existing = existing.subList(0, MAX_ALERTS);
        }

        saveList(existing);
    }

    // Load all alerts newest first
    public List<SOSMessage> getAll() {
        List<SOSMessage> list = new ArrayList<>();
        String raw = prefs.getString(KEY_ALERTS, null);
        if (raw == null) return list;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    list.add(SOSMessage.fromJson(
                            arr.getString(i)));
                } catch (JSONException ignored) {}
            }
        } catch (JSONException ignored) {}
        return list;
    }

    // Delete one alert by messageId
    public void deleteById(String messageId) {
        List<SOSMessage> existing = getAll();
        existing.removeIf(
                m -> m.messageId.equals(messageId));
        saveList(existing);
    }

    // Clear all alerts
    public void clear() {
        prefs.edit()
                .remove(KEY_ALERTS)
                .apply();
    }

    public int count() {
        return getAll().size();
    }

    private void saveList(List<SOSMessage> list) {
        JSONArray arr = new JSONArray();
        for (SOSMessage m : list) {
            arr.put(m.toJson());
        }
        prefs.edit()
                .putString(KEY_ALERTS, arr.toString())
                .apply();
    }
}