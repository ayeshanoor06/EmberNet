package com.ayesha.embernet;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.AnimationSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ayesha.embernet.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AlertManager {

    public interface AlertActionListener {
        void onShowOnMap(SOSMessage message);
        void onDismiss(SOSMessage message);
    }

    private final Context             context;
    private final ViewGroup           rootContainer;
    private       View                alertOverlay;
    private       AlertActionListener listener;
    private final List<SOSMessage>    pendingAlerts = new ArrayList<>();
    private       boolean             isShowing     = false;

    public AlertManager(Context context, ViewGroup rootContainer) {
        this.context       = context;
        this.rootContainer = rootContainer;
    }

    public void setListener(AlertActionListener listener) {
        this.listener = listener;
    }

    // ── Show alert ────────────────────────────────────────────────────────

    public void showAlert(SOSMessage message) {
        // Queue if already showing one
        if (isShowing) {
            pendingAlerts.add(message);
            return;
        }
        displayAlert(message);
    }

    private void displayAlert(SOSMessage message) {
        isShowing = true;

        // Inflate the alert overlay into the root container
        alertOverlay = LayoutInflater.from(context)
                .inflate(R.layout.alert_incoming, rootContainer, false);
        rootContainer.addView(alertOverlay);
        alertOverlay.setVisibility(View.VISIBLE);

        // Bind data
        bindAlertData(alertOverlay, message);

        // Slide up animation
        View card = alertOverlay.findViewById(R.id.alert_card);
        slideUp(card);

        // Pulse the red dot
        View dot = alertOverlay.findViewById(R.id.alert_pulse_dot);
        pulseDot(dot);

        // Vibrate device — SOS pattern: long-short-short
        vibrate();

        // Wire buttons
        MaterialButton btnMap =
                alertOverlay.findViewById(R.id.btn_alert_show_map);
        MaterialButton btnDismiss =
                alertOverlay.findViewById(R.id.btn_alert_dismiss);

        btnMap.setOnClickListener(v -> {
            if (listener != null) listener.onShowOnMap(message);
            dismissAlert(message);
        });

        btnDismiss.setOnClickListener(v -> dismissAlert(message));
    }

    private void bindAlertData(View view, SOSMessage message) {
        ((TextView) view.findViewById(R.id.alert_device_id))
                .setText("DEVICE " + message.deviceId);

        ((TextView) view.findViewById(R.id.alert_coords))
                .setText(message.getFormattedCoords());

        TextView battView = view.findViewById(R.id.alert_battery);
        battView.setText(message.battery + "%");
        battView.setTextColor(context.getColor(
                message.battery <= 20 ? R.color.danger :
                        message.battery <= 40 ? R.color.signal_yellow :
                                R.color.signal_green));

        ((TextView) view.findViewById(R.id.alert_hop_count))
                .setText(message.hopCount + " hop"
                        + (message.hopCount == 1 ? "" : "s"));

        ((TextView) view.findViewById(R.id.alert_time))
                .setText(message.getFormattedTime());

        buildHopChain(view, message.hopCount);
    }

    // ── Hop chain visual ──────────────────────────────────────────────────

    private void buildHopChain(View parent, int hops) {
        LinearLayout chain = parent.findViewById(R.id.alert_hop_chain);
        chain.removeAllViews();

        int totalNodes = Math.min(hops + 2, 6); // origin + relays + you

        for (int i = 0; i < totalNodes; i++) {
            // Node circle
            TextView node = new TextView(context);
            LinearLayout.LayoutParams nodeParams =
                    new LinearLayout.LayoutParams(28, 28);
            node.setLayoutParams(nodeParams);
            node.setGravity(android.view.Gravity.CENTER);
            node.setTextSize(8f);

            if (i == 0) {
                // Origin — ember orange
                node.setBackground(context.getDrawable(
                        R.drawable.circle_dot_red));
                node.setText("A");
                node.setTextColor(context.getColor(R.color.cream));
            } else if (i == totalNodes - 1) {
                // You — green
                node.setBackground(context.getDrawable(
                        R.drawable.circle_dot));
                node.setText("Y");
                node.setTextColor(context.getColor(R.color.coal));
            } else {
                // Relay — yellow
                node.setBackgroundResource(
                        R.drawable.hop_node_relay);
                node.setText("R" + i);
                node.setTextColor(context.getColor(R.color.coal));
            }

            chain.addView(node);

            // Arrow between nodes (except after last)
            if (i < totalNodes - 1) {
                TextView arrow = new TextView(context);
                LinearLayout.LayoutParams arrowParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                arrowParams.setMargins(4, 0, 4, 0);
                arrow.setLayoutParams(arrowParams);
                arrow.setText("→");
                arrow.setTextColor(context.getColor(R.color.warm_gray));
                arrow.setTextSize(12f);
                chain.addView(arrow);
            }
        }
    }

    // ── Dismiss ───────────────────────────────────────────────────────────

    private void dismissAlert(SOSMessage message) {
        if (alertOverlay == null) return;

        if (listener != null) listener.onDismiss(message);

        // Slide down and remove
        View card = alertOverlay.findViewById(R.id.alert_card);
        slideDown(card, () -> {
            rootContainer.removeView(alertOverlay);
            alertOverlay = null;
            isShowing    = false;

            // Show next queued alert if any
            if (!pendingAlerts.isEmpty()) {
                SOSMessage next = pendingAlerts.remove(0);
                displayAlert(next);
            }
        });
    }

    public void dismissAll() {
        pendingAlerts.clear();
        if (alertOverlay != null) {
            rootContainer.removeView(alertOverlay);
            alertOverlay = null;
            isShowing    = false;
        }
    }

    // ── Animations ────────────────────────────────────────────────────────

    private void slideUp(View view) {
        TranslateAnimation slide = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 1f,
                Animation.RELATIVE_TO_SELF, 0f
        );
        slide.setDuration(350);
        slide.setInterpolator(new android.view.animation
                .DecelerateInterpolator());

        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(350);

        AnimationSet set = new AnimationSet(true);
        set.addAnimation(slide);
        set.addAnimation(fade);
        view.startAnimation(set);
    }

    private void slideDown(View view, Runnable onComplete) {
        TranslateAnimation slide = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 1f
        );
        slide.setDuration(250);

        AlphaAnimation fade = new AlphaAnimation(1f, 0f);
        fade.setDuration(250);

        AnimationSet set = new AnimationSet(true);
        set.addAnimation(slide);
        set.addAnimation(fade);
        set.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                if (onComplete != null) onComplete.run();
            }
        });
        view.startAnimation(set);
    }

    private void pulseDot(View dot) {
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.2f);
        pulse.setDuration(600);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        dot.startAnimation(pulse);
    }

    // ── Vibration ─────────────────────────────────────────────────────────

    private void vibrate() {
        long[] pattern = {0, 300, 150, 300, 150, 600};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ — use VibratorManager
            android.os.VibratorManager vm =
                    (android.os.VibratorManager) context
                            .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vm.getDefaultVibrator().vibrate(
                        VibrationEffect.createWaveform(pattern, -1));
            }
        } else {
            // API 26–30 — use deprecated Vibrator directly
            android.os.Vibrator v =
                    (android.os.Vibrator) context
                            .getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            }
        }
    }
}
