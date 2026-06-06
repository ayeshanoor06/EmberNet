package com.ayesha.embernet;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class SOSMessage {

    public static final String TYPE_SOS    = "SOS";
    public static final String TYPE_RELAY  = "RELAY";
    public static final String TYPE_ACK    = "ACK";
    // BEACON type — used when mesh starts passively
    // RelayEngine ignores BEACON packets — no alert shown
    public static final String TYPE_BEACON = "BEACON";

    public static final int MAX_HOPS = 10;

    public final String messageId;
    public final String deviceId;
    public final String type;
    public final double latitude;
    public final double longitude;
    public final float  accuracy;
    public final int    battery;
    public final String timestamp;
    public       int    hopCount;

    // Primary constructor — new SOS from this device
    public SOSMessage(String deviceId,
                      double latitude, double longitude,
                      float accuracy, int battery) {
        this.messageId = UUID.randomUUID()
                .toString().substring(0, 8);
        this.deviceId  = deviceId;
        this.type      = TYPE_SOS;
        this.latitude  = latitude;
        this.longitude = longitude;
        this.accuracy  = accuracy;
        this.battery   = battery;
        this.hopCount  = 0;
        this.timestamp = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    // Full constructor — used by fromJson + asRelayed
    private SOSMessage(String messageId, String deviceId,
                       String type,
                       double latitude, double longitude,
                       float accuracy, int battery,
                       String timestamp, int hopCount) {
        this.messageId = messageId;
        this.deviceId  = deviceId;
        this.type      = type;
        this.latitude  = latitude;
        this.longitude = longitude;
        this.accuracy  = accuracy;
        this.battery   = battery;
        this.timestamp = timestamp;
        this.hopCount  = hopCount;
    }

    // Copy constructor — keeps messageId, refreshes fields
    public SOSMessage(String keepMessageId,
                      SOSMessage fresh) {
        this.messageId = keepMessageId;
        this.deviceId  = fresh.deviceId;
        this.type      = fresh.type;
        this.latitude  = fresh.latitude;
        this.longitude = fresh.longitude;
        this.accuracy  = fresh.accuracy;
        this.battery   = fresh.battery;
        this.timestamp = fresh.timestamp;
        this.hopCount  = fresh.hopCount;
    }

    // ── Serialization ─────────────────────────────────────────────────

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id",  messageId);
            obj.put("dev", deviceId);
            obj.put("typ", type);
            obj.put("lat", latitude);
            obj.put("lon", longitude);
            obj.put("acc", accuracy);
            obj.put("bat", battery);
            obj.put("ts",  timestamp);
            obj.put("hop", hopCount);
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public byte[] toBytes() {
        return toJson().getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
    }

    public static SOSMessage fromJson(String json)
            throws JSONException {
        JSONObject obj = new JSONObject(json);
        return new SOSMessage(
                obj.getString("id"),
                obj.getString("dev"),
                obj.getString("typ"),
                obj.getDouble("lat"),
                obj.getDouble("lon"),
                (float) obj.getDouble("acc"),
                obj.getInt("bat"),
                obj.getString("ts"),
                obj.getInt("hop")
        );
    }

    public static SOSMessage fromBytes(byte[] bytes)
            throws JSONException {
        String raw = new String(bytes,
                java.nio.charset.StandardCharsets.UTF_8);
        if (!raw.trim().endsWith("}")) {
            throw new JSONException(
                    "Truncated payload length="
                            + bytes.length);
        }
        return fromJson(raw);
    }

    // ── Factory helpers ───────────────────────────────────────────────

    // Builds a real SOS — type = SOS
    public static SOSMessage buildFromDevice(
            Context context, Location location) {
        String deviceId =
                android.provider.Settings.Secure
                        .getString(
                                context.getContentResolver(),
                                android.provider.Settings
                                        .Secure.ANDROID_ID)
                        .substring(0, 6).toUpperCase();

        int battery = getBatteryLevel(context);

        double lat = location != null
                ? location.getLatitude()  : 0.0;
        double lon = location != null
                ? location.getLongitude() : 0.0;
        float  acc = location != null
                ? location.getAccuracy()  : 0f;

        return new SOSMessage(
                deviceId, lat, lon, acc, battery);
    }

    // Builds a BEACON — type = BEACON
    // Used when mesh starts passively
    // RelayEngine drops BEACON packets — no alert shown
    public static SOSMessage buildBeacon(
            Context context) {
        String deviceId =
                android.provider.Settings.Secure
                        .getString(
                                context.getContentResolver(),
                                android.provider.Settings
                                        .Secure.ANDROID_ID)
                        .substring(0, 6).toUpperCase();

        int battery = getBatteryLevel(context);

        // Build with BEACON type explicitly
        SOSMessage beacon = new SOSMessage(
                UUID.randomUUID()
                        .toString().substring(0, 8),
                deviceId,
                TYPE_BEACON,   // <-- KEY: not SOS
                0.0, 0.0, 0f,  // no GPS needed for beacon
                battery,
                new SimpleDateFormat("HH:mm:ss",
                        Locale.getDefault())
                        .format(new Date()),
                0
        );
        return beacon;
    }

    public SOSMessage asRelayed() {
        return new SOSMessage(
                this.messageId,
                this.deviceId,
                TYPE_RELAY,
                this.latitude,
                this.longitude,
                this.accuracy,
                this.battery,
                this.timestamp,
                this.hopCount + 1
        );
    }

    // ── Checks ────────────────────────────────────────────────────────

    public boolean isExpired() {
        return hopCount >= MAX_HOPS;
    }

    // Returns true if this is a passive beacon
    // not a real emergency SOS
    public boolean isBeacon() {
        return TYPE_BEACON.equals(type);
    }

    public boolean isRealSOS() {
        return TYPE_SOS.equals(type)
                || TYPE_RELAY.equals(type);
    }

    public String getFormattedCoords() {
        // If GPS was unavailable show helpful message
        if (latitude == 0.0 && longitude == 0.0) {
            return "GPS unavailable";
        }
        return String.format(Locale.getDefault(),
                "%.5f N  %.5f E",
                latitude, longitude);
    }

    public String getFormattedTime() {
        return timestamp;
    }

    private static int getBatteryLevel(
            Context context) {
        IntentFilter filter = new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED);
        Intent status =
                context.registerReceiver(null, filter);
        if (status == null) return -1;
        int level = status.getIntExtra(
                BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(
                BatteryManager.EXTRA_SCALE, -1);
        return (int)((level / (float) scale) * 100);
    }
}