package com.ayesha.embernet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class SosFragment extends Fragment

        implements SosForegroundService.ServiceCallback {
    private AlertHistoryManager historyManager;
    private LinearLayout        historyContainer;
    private TextView            historyCount;
    private TextView            historyEmpty;

    // Views
    private TextView       gpsValue;
    private TextView       accuracyValue;
    private TextView       batteryValue;
    private TextView       statusText;
    private TextView       hopCountView;
    private TextView       peersReachedView;
    private TextView       broadcastCountView;
    private MaterialButton btnSos;
    private MaterialButton btnCancel;
    private View           pulseRing;
    private View           midRing;
    private View           statsCard;

    // Service binding
    private SosForegroundService boundService;
    private boolean              serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            SosForegroundService.SosBinder sosBinder =
                    (SosForegroundService.SosBinder) binder;
            boundService = sosBinder.getService();
            boundService.setCallback(SosFragment.this);
            serviceBound = true;

            // Sync UI if service is already broadcasting
            // (e.g. user left the tab and came back)
            if (boundService.isBroadcasting()) {
                applyBroadcastingUI(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            boundService = null;
        }
    };

    // ── Fragment lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        updateBatteryDisplay();
        setupButtons();
        startLocationDisplay();
        setupAlertHistory();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to service (starts it if not running)
        Intent intent = new Intent(requireContext(),
                SosForegroundService.class);
        requireContext().bindService(
                intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            boundService.setCallback(null);
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopLocationDisplay();
    }

    // ── View binding

    private void bindViews(View view) {
        gpsValue           = view.findViewById(R.id.sos_gps_value);
        accuracyValue      = view.findViewById(R.id.sos_accuracy_value);
        batteryValue       = view.findViewById(R.id.sos_battery_value);
        statusText         = view.findViewById(R.id.sos_status_text);
        hopCountView       = view.findViewById(R.id.sos_hop_count);
        peersReachedView   = view.findViewById(R.id.sos_peers_reached);
        broadcastCountView = view.findViewById(R.id.sos_broadcast_count);
        btnSos             = view.findViewById(R.id.btn_sos);
        btnCancel          = view.findViewById(R.id.btn_sos_cancel);
        pulseRing          = view.findViewById(R.id.sos_pulse_ring);
        midRing            = view.findViewById(R.id.sos_mid_ring);
        statsCard          = view.findViewById(R.id.sos_stats_card);
    }

    // ── Button setup

    private void setupButtons() {
        btnSos.setOnClickListener(v -> {
            if (serviceBound && !boundService.isBroadcasting()) {
                // Press-down scale animation
                android.animation.ObjectAnimator scaleX =
                        android.animation.ObjectAnimator.ofFloat(
                                btnSos, "scaleX", 1f, 0.92f, 1f);
                android.animation.ObjectAnimator scaleY =
                        android.animation.ObjectAnimator.ofFloat(
                                btnSos, "scaleY", 1f, 0.92f, 1f);
                android.animation.AnimatorSet press =
                        new android.animation.AnimatorSet();
                press.playTogether(scaleX, scaleY);
                press.setDuration(180);
                press.setInterpolator(
                        new android.view.animation.OvershootInterpolator(2f));
                press.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator a) {
                        SosForegroundService.start(requireContext());
                        applyBroadcastingUI(true);
                    }
                });
                press.start();
            }
        });

        btnCancel.setOnClickListener(v -> {
            SosForegroundService.stop(requireContext());
            applyBroadcastingUI(false);
        });




    }

    // ── UI state

    private void applyBroadcastingUI(boolean broadcasting) {
        if (!isAdded()) return;

        if (broadcasting) {
            btnSos.setText(getString(R.string.sos_broadcasting));
            btnSos.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            requireContext().getColor(R.color.ember_dark)));
            btnCancel.setVisibility(View.VISIBLE);
            statsCard.setVisibility(View.VISIBLE);
            statusText.setText("Broadcasting SOS to nearby devices…");
            statusText.setTextColor(
                    requireContext().getColor(R.color.ember));
            startPulseAnimation();
        } else {
            btnSos.setText("SOS");
            btnSos.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            requireContext().getColor(R.color.ember)));
            btnCancel.setVisibility(View.GONE);
            statsCard.setVisibility(View.GONE);
            statusText.setText(getString(R.string.sos_waiting));
            statusText.setTextColor(
                    requireContext().getColor(R.color.warm_gray));
            stopPulseAnimation();
            resetStats();
        }
    }

    private void resetStats() {
        hopCountView.setText("0");
        peersReachedView.setText("0");
        broadcastCountView.setText("0");
    }

    // ── SosForegroundService.ServiceCallback ─────────────────────────────

    @Override
    public void onBroadcastTick(SOSMessage message, int count) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            broadcastCountView.setText(String.valueOf(count));
            gpsValue.setText(message.getFormattedCoords());
            batteryValue.setText(message.battery + "%");
            colorBattery(message.battery);
        });
    }

    @Override
    public void onRelayReceived(SOSMessage message) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // Save to history
            if (historyManager != null) {
                historyManager.save(message);
                refreshHistoryList();
            }

            // Update hop count
            int currentHops = Integer.parseInt(
                    hopCountView.getText().toString());
            hopCountView.setText(String.valueOf(
                    Math.max(currentHops, message.hopCount)));

            // Increment peers reached
            int currentPeers = Integer.parseInt(
                    peersReachedView.getText().toString());
            peersReachedView.setText(
                    String.valueOf(currentPeers + 1));

            // Flash status
            statusText.setText(
                    "Relay confirmed — " + message.deviceId);
            statusText.setTextColor(
                    requireContext().getColor(R.color.signal_green));

            new android.os.Handler().postDelayed(() -> {
                if (!isAdded()) return;
                statusText.setText(
                        "Broadcasting SOS to nearby devices…");
                statusText.setTextColor(
                        requireContext().getColor(R.color.ember));
            }, 2000);
        });
    }
    @Override
    public void onGpsLocked(Location location) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            gpsValue.setText(LocationTracker.formatCoordsShort(location));
            accuracyValue.setText(
                    "± " + Math.round(location.getAccuracy()) + " m");
            gpsValue.setTextColor(
                    requireContext().getColor(R.color.cream));
        });
    }

    // ── Location display (independent of broadcasting) ────────────────────

    private LocationTracker displayTracker;

    private void startLocationDisplay() {
        displayTracker = new LocationTracker(requireContext());
        displayTracker.setListener(new LocationTracker.LocationListener() {
            @Override
            public void onLocationUpdated(Location location) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    gpsValue.setText(
                            LocationTracker.formatCoordsShort(location));
                    accuracyValue.setText(
                            "± " + Math.round(location.getAccuracy()) + " m");
                });
            }
            @Override
            public void onLocationUnavailable() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        gpsValue.setText("GPS unavailable"));
            }
        });
        displayTracker.startTracking();
    }

    private void stopLocationDisplay() {
        if (displayTracker != null) displayTracker.stopTracking();
    }

    // ── Battery display ───────────────────────────────────────────────────

    private void updateBatteryDisplay() {
        android.content.IntentFilter filter =
                new android.content.IntentFilter(
                        android.content.Intent.ACTION_BATTERY_CHANGED);
        android.content.Intent intent =
                requireContext().registerReceiver(null, filter);
        if (intent == null) return;

        int level = intent.getIntExtra(
                android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(
                android.os.BatteryManager.EXTRA_SCALE, -1);
        int pct = (int) ((level / (float) scale) * 100);

        batteryValue.setText(pct + "%");
        colorBattery(pct);
    }

    private void colorBattery(int pct) {
        if (!isAdded()) return;
        int color;
        if      (pct <= 20) color = R.color.danger;
        else if (pct <= 40) color = R.color.signal_yellow;
        else                color = R.color.signal_green;
        batteryValue.setTextColor(requireContext().getColor(color));
    }

    // ── Pulse animation ───────────────────────────────────────────────────

    private void startPulseAnimation() {
        pulseRing.setVisibility(View.VISIBLE);
        midRing.setVisibility(View.VISIBLE);
        pulseRing.startAnimation(buildPulseAnim(1.4f, 0.6f, 1800, 0));
        midRing.startAnimation(buildPulseAnim(1.2f, 0.4f, 1800, 300));
    }

    private void stopPulseAnimation() {
        pulseRing.clearAnimation();
        midRing.clearAnimation();
        pulseRing.setAlpha(0f);
        midRing.setAlpha(0f);
    }

    private AnimationSet buildPulseAnim(float scaleTo,
                                        float alphaFrom, long dur, long delay) {
        ScaleAnimation scale = new ScaleAnimation(
                (float) 1.0, scaleTo, (float) 1.0, scaleTo,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(dur);
        scale.setRepeatCount(Animation.INFINITE);
        scale.setRepeatMode(Animation.RESTART);
        scale.setStartOffset(delay);

        AlphaAnimation alpha = new AlphaAnimation(alphaFrom, (float) 0.0);
        alpha.setDuration(dur);
        alpha.setRepeatCount(Animation.INFINITE);
        alpha.setRepeatMode(Animation.RESTART);
        alpha.setStartOffset(delay);

        AnimationSet set = new AnimationSet(true);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        return set;
    }



    private void setupAlertHistory() {
        historyManager   = new AlertHistoryManager(requireContext());
        View historySection;
        historySection = requireView().findViewById(R.id.sos_history_section);
        historyContainer = requireView().findViewById(
                R.id.alert_history_container);
        historyCount     = requireView().findViewById(
                R.id.alert_history_count);
        historyEmpty     = requireView().findViewById(
                R.id.alert_history_empty);

        refreshHistoryList();
    }

    private void refreshHistoryList() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();

        java.util.List<SOSMessage> alerts =
                historyManager.getAll();

        // Update count badge
        historyCount.setText(
                String.valueOf(alerts.size()));

        // Show or hide clear all button
        View clearAllBtn = requireView()
                .findViewById(R.id.btn_clear_all_alerts);
        if (clearAllBtn != null) {
            clearAllBtn.setVisibility(
                    alerts.isEmpty()
                            ? android.view.View.GONE
                            : android.view.View.VISIBLE);

            clearAllBtn.setOnClickListener(v -> {
                // Confirm before deleting all
                new com.google.android.material.dialog
                        .MaterialAlertDialogBuilder(
                        requireContext())
                        .setTitle("Clear all alerts?")
                        .setMessage(
                                "This will permanently delete "
                                        + "all " + alerts.size()
                                        + " received alerts.")
                        .setPositiveButton("Clear all",
                                (d, w) -> {
                                    historyManager.clear();
                                    refreshHistoryList();
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        if (alerts.isEmpty()) {
            historyEmpty.setVisibility(
                    android.view.View.VISIBLE);
            return;
        }

        historyEmpty.setVisibility(
                android.view.View.GONE);

        for (SOSMessage msg : alerts) {
            View row = android.view.LayoutInflater
                    .from(requireContext())
                    .inflate(
                            R.layout.item_alert_history,
                            historyContainer, false);

            // Coords
            ((android.widget.TextView) row.findViewById(
                    R.id.history_item_coords))
                    .setText(msg.getFormattedCoords());

            // Device
            ((android.widget.TextView) row.findViewById(
                    R.id.history_item_device))
                    .setText("Device " + msg.deviceId);

            // Hops
            ((android.widget.TextView) row.findViewById(
                    R.id.history_item_hops))
                    .setText(msg.hopCount + " hop"
                            + (msg.hopCount == 1 ? "" : "s"));

            // Time
            ((android.widget.TextView) row.findViewById(
                    R.id.history_item_time))
                    .setText(msg.getFormattedTime());

            // Battery with color
            android.widget.TextView battView =
                    row.findViewById(R.id.history_item_battery);
            battView.setText(msg.battery + "%");
            battView.setTextColor(
                    requireContext().getColor(
                            msg.battery <= 20
                                    ? R.color.danger
                                    : msg.battery <= 40
                                    ? R.color.signal_yellow
                                    : R.color.signal_green));

            // Tap row — show on map
            row.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity())
                            .onShowOnMap(msg);
                }
            });

            // Delete button — removes this one alert
            android.widget.TextView deleteBtn =
                    row.findViewById(R.id.history_item_delete);
            deleteBtn.setOnClickListener(v -> {
                new com.google.android.material.dialog
                        .MaterialAlertDialogBuilder(
                        requireContext())
                        .setTitle("Delete alert?")
                        .setMessage(
                                "Remove this alert from "
                                        + "Device " + msg.deviceId
                                        + "?")
                        .setPositiveButton("Delete",
                                (d, w) -> {
                                    // Delete this specific alert
                                    historyManager.deleteById(
                                            msg.messageId);
                                    // Refresh the list
                                    refreshHistoryList();
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            historyContainer.addView(row);
        }
    }
}