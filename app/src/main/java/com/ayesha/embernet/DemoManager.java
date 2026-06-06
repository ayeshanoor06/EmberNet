package com.ayesha.embernet;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class DemoManager {

    private static final String TAG        = "DemoManager";
    private static final String PREFS_NAME = "embernet_demo";
    private static final String KEY_DEMO   = "demo_mode_on";

    public interface DemoListener {
        void onDemoSosReceived(SOSMessage message);
        void onDemoStep(String stepDescription);
        void onDemoComplete();
    }

    private final Context        context;
    private final Handler        handler;
    private final SharedPreferences prefs;
    private       DemoListener   listener;
    private       boolean        isRunning = false;

    public DemoManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.prefs   = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setListener(DemoListener listener) {
        this.listener = listener;
    }

    public boolean isDemoModeEnabled() {
        return prefs.getBoolean(KEY_DEMO, false);
    }

    public void setDemoMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEMO, enabled).apply();
        Log.d(TAG, "Demo mode: " + (enabled ? "ON" : "OFF"));
    }

    // ── Full demo sequence ────────────────────────────────────────────────
    // Simulates a two-phone mesh relay on a single device
    // Used when the teacher only has one phone available

    public void runFullDemo(Location currentLocation) {
        if (isRunning) return;
        isRunning = true;

        Log.d(TAG, "Starting full demo sequence");

        // Step 1 — announce demo start
        step(0, "Demo started — simulating Phone A sending SOS…");

        // Step 2 — build a fake SOS from "Phone A"
        handler.postDelayed(() -> {
            step(1, "Phone A broadcasting SOS over Bluetooth LE…");
        }, 1500);

        // Step 3 — simulate relay hop
        handler.postDelayed(() -> {
            step(2, "Relay device detected — forwarding message…");
        }, 3000);

        // Step 4 — deliver to this device as Phone B
        handler.postDelayed(() -> {
            SOSMessage fake = buildFakeSOSMessage(currentLocation);
            step(3, "SOS received on this device after 2 hops!");
            if (listener != null) {
                listener.onDemoSosReceived(fake);
            }
        }, 5000);

        // Step 5 — done
        handler.postDelayed(() -> {
            isRunning = false;
            if (listener != null) listener.onDemoComplete();
            Log.d(TAG, "Demo sequence complete");
        }, 6000);
    }

    private void step(int num, String description) {
        Log.d(TAG, "Demo step " + num + ": " + description);
        if (listener != null) {
            listener.onDemoStep(description);
        }
    }

    // Builds a realistic fake SOS from a nearby coordinate
    private SOSMessage buildFakeSOSMessage(Location location) {
        // Offset slightly from current location to simulate
        // a victim 200 metres away
        double lat = location != null
                ? location.getLatitude()  + 0.0018 : 33.6938;
        double lon = location != null
                ? location.getLongitude() + 0.0012 : 73.0651;

        SOSMessage msg = new SOSMessage(
                "DEMO01",   // deviceId
                lat, lon,
                8.0f,       // accuracy
                67          // battery
        );

        // Manually set hop count to 2 to show relay chain
        msg.hopCount = 2;
        return msg;
    }

    public void cancel() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isRunning() { return isRunning; }
}