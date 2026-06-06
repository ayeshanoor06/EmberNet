package com.ayesha.embernet;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        LottieAnimationView lottieView = findViewById(R.id.lottie_splash);

        // Listen for when the animation completes
        lottieView.addAnimatorListener(new android.animation.Animator.AnimatorListener() {

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Small pause after animation ends, then go to main
                new Handler().postDelayed(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    // Slide transition: new screen slides in from right
                    overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                    );
                    finish();
                }, 300);
            }

            @Override public void onAnimationStart(android.animation.Animator animation) {}
            @Override public void onAnimationCancel(android.animation.Animator animation) {}
            @Override public void onAnimationRepeat(android.animation.Animator animation) {}
        });

        // Safety fallback — if animation never fires the listener, go after 4 seconds
        new Handler().postDelayed(() -> {
            if (!isFinishing()) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, 4000);
    }
}