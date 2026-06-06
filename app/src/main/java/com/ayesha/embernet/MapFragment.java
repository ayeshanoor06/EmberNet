package com.ayesha.embernet;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

public class MapFragment extends Fragment implements LocationTracker.LocationListener {

    private MapView          mapView;
    private LocationTracker  locationTracker;
    private MapOverlayManager overlayManager;
    private TextView         coordsText;
    private TextView         statusText;
    private View             statusDot;
    private boolean          mapCenteredOnUser = false;

    // Default center — layyah
    private static final double DEFAULT_LAT  = 30.9625;
    private static final double DEFAULT_LON  = 70.9394;
    private static final double DEFAULT_ZOOM = 16.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);

    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView    = view.findViewById(R.id.map_view);
        coordsText = view.findViewById(R.id.map_coords_text);
        statusText = view.findViewById(R.id.map_status_text);
        statusDot  = view.findViewById(R.id.map_status_dot);

        setupMap();

        overlayManager  = new MapOverlayManager(requireContext(), mapView);
        locationTracker = new LocationTracker(requireContext());
        locationTracker.setListener(this);

        overlayManager.addSafeZones();
        setupMarkerListeners();   // ← add this line
        setupButtons(view);
    }

    private void setupMap() {
        // Change tile source to one that shows
        // buildings, shops, hospitals, roads,
        // landmarks — everything like Google Maps
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController
                        .Visibility.NEVER);

        // Zoom 16 shows street names, shops,
        // buildings, mosques, schools, everything
        mapView.getController().setZoom(16.0);
        mapView.getController().setCenter(
                new GeoPoint(DEFAULT_LAT, DEFAULT_LON));

        // REMOVE this line completely — it hides details
        // mapView.getOverlayManager()
        //     .getTilesOverlay()
        //     .setColorFilter(TilesOverlay.INVERT_COLORS);

        mapView.setTilesScaledToDpi(true);
        mapView.setUseDataConnection(true);
    }

    private void setupButtons(@NonNull View view) {
        MaterialButton btnLocation = view.findViewById(R.id.btn_my_location);
        btnLocation.setOnClickListener(v -> {
            Location loc = locationTracker.getLastKnownLocation();
            if (loc != null) {
                mapView.getController().animateTo(
                        new GeoPoint(loc.getLatitude(), loc.getLongitude())
                );
                mapView.getController().setZoom(17.0);
            } else {
                Toast.makeText(requireContext(),
                        "GPS not locked yet", Toast.LENGTH_SHORT).show();
            }
        });

        MaterialButton btnDownload = view.findViewById(R.id.btn_download_map);
        btnDownload.setOnClickListener(v -> downloadOfflineMap());
    }



    private void setupMarkerListeners() {
        // Safe zone marker tapped → show bottom sheet
        overlayManager.setOnSafeZoneTapped(zone -> {
            Location userLoc = locationTracker.getLastKnownLocation();
            SafeZoneBottomSheet sheet =
                    SafeZoneBottomSheet.newInstance(zone, userLoc);
            sheet.show(getChildFragmentManager(), "safe_zone");
        });
    }

    // Call this from Phase 4 MeshService when an SOS arrives
    public void showSosAlertOnMap(double lat, double lon,
                                  int battery, int hops, String time, String deviceId) {

        if (!isAdded() || mapView == null) return;

        requireActivity().runOnUiThread(() -> {
            overlayManager.addSosAlertMarker(
                    lat, lon, deviceId, hops, battery, time);

            SosAlertBottomSheet sheet = SosAlertBottomSheet.newInstance(
                    lat, lon, battery, hops, time, deviceId);
            sheet.show(getChildFragmentManager(), "sos_alert");
        });
    }
    // ── LocationTracker.LocationListener callbacks ────────────────────────

    @Override
    public void onLocationUpdated(Location location) {
        // Update coordinates text
        coordsText.setText(LocationTracker.formatCoords(location));
        statusText.setText("GPS locked");

        // Change dot to green once we have a fix
        statusDot.setBackgroundResource(R.drawable.circle_dot);

        // Move marker on map
        overlayManager.updateUserLocation(location);

        // First fix — animate map to user's real position
        if (!mapCenteredOnUser) {
            mapView.getController().animateTo(
                    new GeoPoint(location.getLatitude(), location.getLongitude())
            );
            mapView.getController().setZoom(18.0);
            mapCenteredOnUser = true;
        }
    }

    @Override
    public void onLocationUnavailable() {
        coordsText.setText("Location unavailable");
        statusText.setText("No GPS");
    }

    // ── Download offline map ──────────────────────────────────────────────

    private void downloadOfflineMap() {
        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) requireContext()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();

        if (netInfo == null || !netInfo.isConnected()) {
            Toast.makeText(requireContext(),
                    "No internet. Connect to Wi-Fi first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        OfflineMapManager manager = new OfflineMapManager(requireContext());

        if (manager.isMapSaved()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                    requireContext())
                    .setTitle("Re-download map?")
                    .setMessage("An offline map is already saved. Download a fresh copy?")
                    .setPositiveButton("Re-download",
                            (d, w) -> startDownload(center))
                    .setNegativeButton("Keep existing", null)
                    .show();
        } else {
            startDownload(center);
        }
    }

    private void startDownload(GeoPoint center) {
        MaterialButton btnDownload =
                requireView().findViewById(R.id.btn_download_map);
        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading…");

        new DownloadMapDialog(requireContext(), center,
                new DownloadMapDialog.OnDownloadComplete() {
                    @Override
                    public void onComplete() {
                        btnDownload.setEnabled(true);
                        btnDownload.setText(R.string.map_saved);
                        statusText.setText("Offline map saved");
                        Toast.makeText(requireContext(),
                                "Map saved! Works offline now.",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCancelled() {
                        btnDownload.setEnabled(true);
                        btnDownload.setText(R.string.download_map);
                    }
                }).show();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        locationTracker.startTracking();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        locationTracker.stopTracking();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (overlayManager != null) overlayManager.stopAnimations();
        locationTracker.stopTracking();
        mapView.onDetach();
    }

}