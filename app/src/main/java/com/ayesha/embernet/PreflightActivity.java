package com.ayesha.embernet;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class PreflightActivity extends AppCompatActivity {

    // Holds a reference to each checklist row view
    private final List<View> checkRows = new ArrayList<>();

    // ── Checklist ─────────────────────────────────────────────────────────

    private static final int IDX_BT = 0;
    private static final int IDX_LOCATION = 1;
    private static final int IDX_GPS = 2;
    private static final int IDX_MAP = 3;
    private static final int IDX_MESH = 4;
    private static final int IDX_PERMS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preflight);

        buildChecklist();
        runChecks();

        findViewById(R.id.btn_close_preflight).setOnClickListener(v -> finish());
    }

    private void buildChecklist() {
        String[][] items = {
                {"Bluetooth", "Required for BLE mesh"},
                {"Location perm", "Required for BLE scanning"},
                {"GPS signal", "Attaches coords to SOS"},
                {"Offline map", "Works without internet"},
                {"Mesh service", "BLE scanner active"},
                {"All permissions", "BT + Location granted"},
                {"Notifications", "Required for SOS alerts"},
        };

        ViewGroup checklistContainer = findViewById(R.id.checklist_container);

        for (String[] item : items) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_checklist,
                            checklistContainer, false);
            ((TextView) row.findViewById(R.id.check_label))
                    .setText(item[0]);
            ((TextView) row.findViewById(R.id.check_sub))
                    .setText(item[1]);
            checklistContainer.addView(row);
            checkRows.add(row);
        }
    }

    private void runChecks() {
        // Bluetooth
        BluetoothManager bm = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter =
                bm != null ? bm.getAdapter() : null;
        boolean btOn = adapter != null && adapter.isEnabled();
        setCheck(IDX_BT, btOn,
                btOn ? "ON" : "OFF — enable Bluetooth");

        // Location permission
        boolean locPerm = PermissionHelper.locationGranted(this);
        setCheck(IDX_LOCATION, locPerm,
                locPerm ? "GRANTED" : "DENIED");

        // GPS signal
        LocationManager lm = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = lm != null
                && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        setCheck(IDX_GPS, gpsEnabled,
                gpsEnabled ? "ENABLED" : "OFF — enable GPS");

        // Offline map
        OfflineMapManager mapManager =
                new OfflineMapManager(this);
        boolean mapSaved = mapManager.isMapSaved();
        setCheck(IDX_MAP, mapSaved,
                mapSaved ? "SAVED" : "NOT SAVED — download first");

        // Mesh service
        boolean meshActive =
                MeshService.getInstance(this).isActive();
        setCheck(IDX_MESH, meshActive,
                meshActive ? "ACTIVE" : "INACTIVE — tap Mesh tab");

        // All permissions
        boolean allPerms = PermissionHelper.allGranted(this);
        setCheck(IDX_PERMS, allPerms,
                allPerms ? "ALL GRANTED" : "MISSING — check settings");
        // Notifications permission (API 33+ Samsung critical)
        boolean notifGranted =
                PermissionHelper.notificationsGranted(this);
        setCheck(6, notifGranted,
                notifGranted
                        ? "ALLOWED"
                        : "BLOCKED — tap to allow");
    }

    @SuppressLint("SetTextI18n")
    private void setCheck(int index, boolean pass,
                          String statusText) {
        if (index >= checkRows.size()) return;
        View row = checkRows.get(index);

        TextView icon = row.findViewById(R.id.check_icon);
        TextView status = row.findViewById(R.id.check_status);
        TextView label = row.findViewById(R.id.check_label);

        if (pass) {
            icon.setText("✓");
            icon.setTextColor(getColor(R.color.signal_green));
            status.setText("OK");
            status.setTextColor(getColor(R.color.signal_green));
        } else {
            icon.setText("✗");
            icon.setTextColor(getColor(R.color.danger));
            status.setText(statusText);
            status.setTextColor(getColor(R.color.danger));
            label.setTextColor(getColor(R.color.danger));
        }
    }
}
