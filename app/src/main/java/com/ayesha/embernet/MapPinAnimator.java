package com.ayesha.embernet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapPinAnimator {

    private final Context context;
    private final MapView mapView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Track the currently animated pin
    private Marker    animatedMarker;
    private Runnable  pulseRunnable;
    private boolean   pulseGrowing = true;
    private float     pulseRadius  = 30f;

    public MapPinAnimator(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    // Start pulsing animation on a marker
    public void startPulse(Marker marker) {
        stopPulse(); // stop any existing pulse first
        animatedMarker = marker;

        pulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (animatedMarker == null) return;

                // Grow and shrink the outer ring
                if (pulseGrowing) {
                    pulseRadius += 3f;
                    if (pulseRadius >= 50f) pulseGrowing = false;
                } else {
                    pulseRadius -= 3f;
                    if (pulseRadius <= 20f) pulseGrowing = true;
                }

                // Rebuild the marker icon with new ring size
                animatedMarker.setIcon(
                        buildPulsingIcon(pulseRadius)
                );
                mapView.invalidate();
                handler.postDelayed(this, 60);
            }
        };
        handler.post(pulseRunnable);
    }

    public void stopPulse() {
        if (pulseRunnable != null) {
            handler.removeCallbacks(pulseRunnable);
            pulseRunnable = null;
        }
        animatedMarker = null;
    }

    // Builds a bitmap icon with a pulsing ring around a center dot
    private android.graphics.drawable.BitmapDrawable buildPulsingIcon(
            float ringRadius) {

        int size = 120;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float cx = size / 2f;
        float cy = size / 2f;

        // Outer pulsing ring — semi-transparent ember
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.parseColor("#88E8521A"));
        ringPaint.setStrokeWidth(3f);
        canvas.drawCircle(cx, cy, ringRadius, ringPaint);

        // Inner fill ring
        Paint fillRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillRing.setStyle(Paint.Style.FILL);
        fillRing.setColor(Color.parseColor("#33E8521A"));
        canvas.drawCircle(cx, cy, ringRadius - 4f, fillRing);

        // White border on center dot
        Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 14f, whitePaint);

        // Center ember dot
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.parseColor("#E8521A"));
        canvas.drawCircle(cx, cy, 11f, dotPaint);

        return new android.graphics.drawable.BitmapDrawable(
                context.getResources(), bitmap);
    }
}