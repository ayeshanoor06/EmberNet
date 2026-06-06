package com.ayesha.embernet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class RelayEngine {

    private static final String TAG = "RelayEngine";

    private static final long SEEN_TTL_MS     =
            10 * 60 * 1000;
    private static final long RELAY_DELAY_MIN = 200;
    private static final long RELAY_DELAY_MAX = 800;

    public interface RelayListener {
        void onNewSosReceived(SOSMessage message);
        void onRelaying(SOSMessage relayed);
    }

    private final Context       context;
    private final Handler       handler;
    private       RelayListener listener;
    private       MeshService   meshService;

    private final Map<String, Long> seenMessages =
            new HashMap<>();
    private int relayedCount = 0;

    public RelayEngine(Context context) {
        this.context = context.getApplicationContext();
        this.handler =
                new Handler(Looper.getMainLooper());
    }

    public void setListener(RelayListener listener) {
        this.listener = listener;
    }

    public void setMeshService(
            MeshService meshService) {
        this.meshService = meshService;
    }

    // ── Main entry point ──────────────────────────────────────────────

    public void handleIncomingPacket(byte[] rawBytes) {
        SOSMessage incoming;
        try {
            incoming = SOSMessage.fromBytes(rawBytes);
        } catch (Exception e) {
            Log.d(TAG, "Parse failed: "
                    + e.getMessage());
            return;
        }

        Log.d(TAG, "Incoming: id="
                + incoming.messageId
                + " type=" + incoming.type
                + " from=" + incoming.deviceId
                + " hops=" + incoming.hopCount
                + " lat=" + incoming.latitude
                + " lon=" + incoming.longitude);

        // ── DROP 1: BEACON packets ────────────────────
        // These are passive mesh presence signals
        // not real emergencies — never show alert
        if (incoming.isBeacon()) {
            Log.d(TAG, "BEACON packet — dropping, "
                    + "not an emergency");
            return;
        }

        // ── DROP 2: SAFEZONE shares ───────────────────
        if ("SAFEZONE".equals(incoming.deviceId)) {
            Log.d(TAG, "Safe zone share — skipping");
            scheduleRelay(incoming);
            return;
        }

        // ── DROP 3: Own messages ──────────────────────
        if (isOwnMessage(incoming)) {
            Log.d(TAG, "OWN MESSAGE — dropping: "
                    + incoming.deviceId);
            return;
        }

        // ── DROP 4: Duplicates ────────────────────────
        if (isDuplicate(incoming.messageId)) {
            Log.d(TAG, "DUPLICATE — dropping "
                    + incoming.messageId);
            return;
        }

        // ── DROP 5: Hop limit ─────────────────────────
        if (incoming.isExpired()) {
            Log.d(TAG,
                    "HOP LIMIT — dropping hops="
                            + incoming.hopCount);
            return;
        }

        markSeen(incoming.messageId);

        Log.d(TAG, "REAL SOS from "
                + incoming.deviceId
                + " lat=" + incoming.latitude
                + " lon=" + incoming.longitude
                + " — showing alert");

        // Notify listener — show alert in UI
        if (listener != null) {
            handler.post(() ->
                    listener.onNewSosReceived(incoming));
        } else {
            routeAlertDirectly(incoming);
        }

        // Relay forward
        scheduleRelay(incoming);
    }

    // ── Own device check ──────────────────────────────────────────────

    private boolean isOwnMessage(SOSMessage msg) {
        try {
            String androidId =
                    android.provider.Settings.Secure
                            .getString(
                                    context.getContentResolver(),
                                    android.provider.Settings
                                            .Secure.ANDROID_ID);
            if (androidId == null) return false;

            String ownId = androidId
                    .substring(0, 6)
                    .toUpperCase().trim();
            String inId = msg.deviceId
                    .toUpperCase().trim();

            return ownId.startsWith(inId)
                    || inId.startsWith(ownId);

        } catch (Exception e) {
            return false;
        }
    }

    // ── Relay ─────────────────────────────────────────────────────────

    private void scheduleRelay(SOSMessage original) {
        long jitter = RELAY_DELAY_MIN
                + (long)(Math.random()
                * (RELAY_DELAY_MAX - RELAY_DELAY_MIN));

        handler.postDelayed(() -> {
            SOSMessage relayed = original.asRelayed();
            if (meshService != null) {
                meshService.send(relayed.toBytes());
            }
            relayedCount++;
            if (listener != null) {
                listener.onRelaying(relayed);
            }
        }, jitter);
    }

    // ── Direct routing ────────────────────────────────────────────────

    private void routeAlertDirectly(
            SOSMessage message) {
        handler.post(() -> {
            try {
                android.content.Intent alert =
                        new android.content.Intent(
                                "com.ayesha.embernet"
                                        + ".SHOW_ALERT");
                alert.putExtra(
                        "message_json",
                        message.toJson());
                alert.setPackage(
                        context.getPackageName());
                context.sendBroadcast(alert);
            } catch (Exception e) {
                Log.e(TAG, "Direct route: "
                        + e.getMessage());
            }
        });
    }

    // ── Deduplication ─────────────────────────────────────────────────

    private boolean isDuplicate(String messageId) {
        pruneExpiredEntries();
        return seenMessages.containsKey(messageId);
    }

    private void markSeen(String messageId) {
        seenMessages.put(messageId,
                System.currentTimeMillis());
    }

    private void pruneExpiredEntries() {
        long now = System.currentTimeMillis();
        seenMessages.entrySet().removeIf(
                e -> (now - e.getValue()) > SEEN_TTL_MS);
    }

    public int getRelayedCount() {
        return relayedCount;
    }

    public int getSeenCount() {
        pruneExpiredEntries();
        return seenMessages.size();
    }

    public void reset() {
        seenMessages.clear();
        relayedCount = 0;
    }
}