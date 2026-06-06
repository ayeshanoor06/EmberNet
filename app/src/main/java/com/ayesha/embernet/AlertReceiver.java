package com.ayesha.embernet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlertReceiver extends BroadcastReceiver {

    private static final String TAG = "AlertReceiver";

    public static final String ACTION_SHOW_ALERT =
            "com.ayesha.embernet.SHOW_ALERT";
    public static final String EXTRA_MESSAGE_JSON =
            "message_json";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (!ACTION_SHOW_ALERT.equals(action)) return;

        String json = intent.getStringExtra(
                EXTRA_MESSAGE_JSON);
        if (json == null) {
            Log.e(TAG, "No message JSON in intent");
            return;
        }

        try {
            SOSMessage message =
                    SOSMessage.fromJson(json);

            Log.d(TAG, "Alert received: "
                    + message.deviceId
                    + " hops=" + message.hopCount);

            // Show system notification
            showNotification(context, message);

            // Launch MainActivity to show in-app overlay
            Intent launch = new Intent(
                    context, MainActivity.class);
            launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launch.putExtra(
                    "show_alert_json", message.toJson());
            context.startActivity(launch);

        } catch (Exception e) {
            Log.e(TAG,
                    "Alert processing failed: "
                            + e.getMessage());
        }
    }

    private void showNotification(
            Context context, SOSMessage message) {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE);

            // Create channel
            android.app.NotificationChannel channel =
                    new android.app.NotificationChannel(
                            "embernet_alert",
                            "EmberNet SOS Alerts",
                            android.app.NotificationManager
                                    .IMPORTANCE_MAX);
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(
                    android.app.Notification
                            .VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);

            android.app.PendingIntent openApp =
                    android.app.PendingIntent.getActivity(
                            context, 0,
                            new Intent(context,
                                    MainActivity.class)
                                    .putExtra("open_sos", true),
                            android.app.PendingIntent
                                    .FLAG_UPDATE_CURRENT
                                    | android.app.PendingIntent
                                    .FLAG_IMMUTABLE);

            android.app.Notification notification =
                    new androidx.core.app
                            .NotificationCompat
                            .Builder(context, "embernet_alert")
                            .setContentTitle(
                                    "SOS — Device "
                                            + message.deviceId)
                            .setContentText(
                                    "Location: "
                                            + message.getFormattedCoords()
                                            + " | Battery: "
                                            + message.battery + "%")
                            .setSmallIcon(R.drawable.ic_sos)
                            .setAutoCancel(true)
                            .setPriority(androidx.core.app
                                    .NotificationCompat.PRIORITY_MAX)
                            .setVibrate(new long[]{
                                    0, 400, 200, 400, 200, 800})
                            .setVisibility(androidx.core.app
                                    .NotificationCompat
                                    .VISIBILITY_PUBLIC)
                            .setContentIntent(openApp)
                            .setDefaults(androidx.core.app
                                    .NotificationCompat.DEFAULT_ALL)
                            .build();

            nm.notify(3001, notification);
            Log.d(TAG, "Notification shown");

        } catch (Exception e) {
            Log.e(TAG,
                    "Notification failed: "
                            + e.getMessage());
        }
    }
}