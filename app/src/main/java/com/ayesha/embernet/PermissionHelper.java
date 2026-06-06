package com.ayesha.embernet;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    public static final int REQUEST_CODE = 1001;

    public static String[] getRequiredPermissions() {
        List<String> perms = new ArrayList<>();

        // Location — required on ALL versions for BLE scanning
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // Bluetooth — split by Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ Android 12+
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        // API 30 and below — BLUETOOTH + BLUETOOTH_ADMIN
        // are normal permissions granted at install, no runtime request

        // Notifications — required on Android 13+ API 33
        // Without this Samsung silently blocks ALL notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return perms.toArray(new String[0]);
    }

    public static boolean allGranted(Activity activity) {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("PermissionHelper",
                        "Missing: " + perm);
                return false;
            }
        }
        return true;
    }

    public static void requestAll(Activity activity) {
        List<String> missing = new ArrayList<>();
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }
        if (!missing.isEmpty()) {
            android.util.Log.d("PermissionHelper",
                    "Requesting " + missing.size() + " permissions");
            ActivityCompat.requestPermissions(
                    activity,
                    missing.toArray(new String[0]),
                    REQUEST_CODE);
        }
    }

    public static boolean locationGranted(Activity activity) {
        return ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean bluetoothGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean notificationsGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Below API 33 notifications are always allowed
        return true;
    }
}