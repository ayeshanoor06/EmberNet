package com.ayesha.embernet;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

public class SafeZoneBottomSheet
        extends BottomSheetDialogFragment {

    private static final String ARG_NAME     = "name";
    private static final String ARG_TYPE     = "type";
    private static final String ARG_LAT      = "lat";
    private static final String ARG_LON      = "lon";
    private static final String ARG_CAPACITY = "capacity";
    private static final String ARG_USER_LAT = "user_lat";
    private static final String ARG_USER_LON = "user_lon";

    public static SafeZoneBottomSheet newInstance(
            MapOverlayManager.SafeZone zone,
            Location userLocation) {

        SafeZoneBottomSheet sheet =
                new SafeZoneBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_NAME,     zone.name);
        args.putString(ARG_TYPE,     zone.type);
        args.putDouble(ARG_LAT,      zone.lat);
        args.putDouble(ARG_LON,      zone.lon);
        args.putInt(ARG_CAPACITY,    zone.capacity);

        if (userLocation != null) {
            args.putDouble(ARG_USER_LAT,
                    userLocation.getLatitude());
            args.putDouble(ARG_USER_LON,
                    userLocation.getLongitude());
        }
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(
                R.layout.fragment_map_bottom_sheet,
                container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args    = requireArguments();
        String name    = args.getString(ARG_NAME,
                "Safe Zone");
        String type    = args.getString(ARG_TYPE,
                "Shelter");
        double lat     = args.getDouble(ARG_LAT);
        double lon     = args.getDouble(ARG_LON);
        int capacity   = args.getInt(ARG_CAPACITY, 100);
        double userLat = args.getDouble(ARG_USER_LAT, 0);
        double userLon = args.getDouble(ARG_USER_LON, 0);

        // Bind views
        TextView badgeView =
                view.findViewById(R.id.zone_type_badge);
        TextView nameView =
                view.findViewById(R.id.zone_name);
        TextView coordsView =
                view.findViewById(R.id.zone_coords);
        TextView distanceView =
                view.findViewById(R.id.zone_distance);
        TextView statusView =
                view.findViewById(R.id.zone_status);
        TextView capacityView =
                view.findViewById(R.id.zone_capacity);
        TextView verifiedView =
                view.findViewById(R.id.zone_verified);

        badgeView.setText(type);
        nameView.setText(name);
        coordsView.setText(String.format(
                "%.4f°N  %.4f°E", lat, lon));
        capacityView.setText(capacity + " people");

        // Color badge based on type
        int badgeColor;
        if (type.equals("Medical")) {
            badgeColor = requireContext().getColor(
                    R.color.signal_green);
        } else if (type.equals("Rescue")) {
            badgeColor = requireContext().getColor(
                    R.color.signal_blue);
        } else {
            badgeColor = requireContext().getColor(
                    R.color.signal_yellow);
        }
        badgeView.setTextColor(badgeColor);

        // Calculate distance
        if (userLat != 0 && userLon != 0) {
            float[] results = new float[1];
            Location.distanceBetween(
                    userLat, userLon, lat, lon, results);
            float distMetres = results[0];
            if (distMetres < 1000) {
                distanceView.setText(
                        Math.round(distMetres) + " m away");
            } else {
                distanceView.setText(String.format(
                        "%.1f km away",
                        distMetres / 1000));
            }
        } else {
            distanceView.setText("Distance unknown");
        }

        statusView.setText("Open");
        verifiedView.setText("Yes");

        // ── Navigate button ───────────────────────────

        MaterialButton btnNavigate =
                view.findViewById(R.id.btn_navigate_zone);

        btnNavigate.setOnClickListener(v -> {
            try {
                // Direct Google Maps navigation URL
                String googleMapsUrl =
                        "https://www.google.com/maps/dir/"
                                + "?api=1"
                                + "&destination=" + lat + "," + lon
                                + "&travelmode=walking";

                android.content.Intent intent =
                        new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                        googleMapsUrl));

                // Try Google Maps app first
                intent.setPackage(
                        "com.google.android.apps.maps");

                if (intent.resolveActivity(
                        requireContext()
                                .getPackageManager())
                        != null) {
                    startActivity(intent);
                } else {
                    // Fallback — open in browser
                    // works on every Android phone
                    android.content.Intent browser =
                            new android.content.Intent(
                                    android.content.Intent
                                            .ACTION_VIEW,
                                    android.net.Uri.parse(
                                            googleMapsUrl));
                    startActivity(browser);
                }
                dismiss();

            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "Could not open navigation",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // ── Share via mesh button

        MaterialButton btnShare =
                view.findViewById(R.id.btn_share_zone);

        btnShare.setOnClickListener(v -> {
            try {
                // Build SOSMessage JSON with safe zone
                // coordinates so nearby devices see it
                org.json.JSONObject obj =
                        new org.json.JSONObject();
                obj.put("id",
                        "SZ" + (System.currentTimeMillis()
                                % 100000));
                obj.put("dev", "SAFEZONE");
                obj.put("typ", "SOS");
                obj.put("lat", lat);
                obj.put("lon", lon);
                obj.put("acc", 0);
                obj.put("bat", 100);
                obj.put("ts",
                        new java.text.SimpleDateFormat(
                                "HH:mm:ss",
                                java.util.Locale.getDefault())
                                .format(new java.util.Date()));
                obj.put("hop", 0);

                // Broadcast to nearby devices via mesh
                android.content.Intent alert =
                        new android.content.Intent(
                                "com.ayesha.embernet"
                                        + ".SHOW_ALERT");
                alert.putExtra(
                        "message_json", obj.toString());
                alert.setPackage(
                        requireContext().getPackageName());
                requireContext().sendBroadcast(alert);

                Toast.makeText(requireContext(),
                        "Safe zone location shared "
                                + "to nearby devices via mesh",
                        Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "Share failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            dismiss();
        });
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style
                .ThemeOverlay_Material3_BottomSheetDialog;
    }
}