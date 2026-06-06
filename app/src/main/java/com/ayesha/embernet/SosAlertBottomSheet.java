package com.ayesha.embernet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class SosAlertBottomSheet extends BottomSheetDialogFragment {

    public static SosAlertBottomSheet newInstance(
            double lat, double lon,
            int battery, int hops,
            String time, String deviceId) {

        SosAlertBottomSheet sheet = new SosAlertBottomSheet();
        Bundle args = new Bundle();
        args.putDouble("lat",      lat);
        args.putDouble("lon",      lon);
        args.putInt("battery",     battery);
        args.putInt("hops",        hops);
        args.putString("time",     time);
        args.putString("deviceId", deviceId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(
                R.layout.fragment_sos_alert_sheet, container, false
        );
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args    = requireArguments();
        double lat     = args.getDouble("lat");
        double lon     = args.getDouble("lon");
        int    battery = args.getInt("battery");
        int    hops    = args.getInt("hops");
        String time    = args.getString("time", "Unknown");
        String device  = args.getString("deviceId", "Unknown device");

        // Fixed: Call the local helper method 'findViewById(View, int, Class)' 
        // instead of 'view.findViewById(int, Class)' which does not exist.
        findViewById(view, R.id.sos_alert_device_id, TextView.class).setText(device);
        findViewById(view, R.id.sos_alert_coords, TextView.class).setText(
                String.format(Locale.getDefault(), "%.5f°N  %.5f°E", lat, lon));
        findViewById(view, R.id.sos_alert_battery, TextView.class).setText(
                String.format(Locale.getDefault(), "%d%%", battery));
        findViewById(view, R.id.sos_alert_hops, TextView.class).setText(
                String.format(Locale.getDefault(), "%d hops", hops));
        findViewById(view, R.id.sos_alert_time, TextView.class).setText(time);

        MaterialButton btnDismiss = view.findViewById(R.id.btn_dismiss_alert);
        btnDismiss.setOnClickListener(v -> dismiss());
    }

    // Helper extension to avoid repeated casting
    private <T extends View> T findViewById(View root, int id, Class<T> cls) {
        return cls.cast(root.findViewById(id));
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style
                .ThemeOverlay_Material3_BottomSheetDialog;
    }
}
