package com.capstone.eventticketing.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.ui.admin.AdminDashboardActivity;
import com.capstone.eventticketing.ui.auth.AuthViewModel;
import com.capstone.eventticketing.ui.auth.LoginActivity;
import com.capstone.eventticketing.ui.main.MainActivity;
import com.capstone.eventticketing.ui.onboarding.OnboardingActivity;
import com.capstone.eventticketing.util.PrefsManager;
import com.capstone.eventticketing.util.Resource;

/**
 * Entry point. After a brief Lottie splash, routes to the correct destination:
 * first launch → Onboarding; logged out → Login; logged in → role-resolved
 * Home/Admin. All app-entry routing lives here.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 2500L;

    @NonNull
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable routeRunnable = this::route;

    private PrefsManager prefsManager;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        prefsManager = new PrefsManager(this);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        splashHandler.postDelayed(routeRunnable, SPLASH_DURATION_MS);
    }

    private void route() {
        // 1) First launch → onboarding.
        if (!prefsManager.isOnboardingSeen()) {
            startAndFinish(new Intent(this, OnboardingActivity.class));
            return;
        }
        // 2) Not signed in → login.
        if (!authViewModel.isUserLoggedIn()) {
            startAndFinish(new Intent(this, LoginActivity.class));
            return;
        }
        // 3) Signed in → resolve role, then route to Home or Admin.
        observeRouteAndResolve();
    }

    private void observeRouteAndResolve() {
        authViewModel.getRouteState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                Intent target = (resource.data == AuthViewModel.RouteDestination.ADMIN)
                        ? new Intent(this, AdminDashboardActivity.class)
                        : new Intent(this, MainActivity.class);
                startAndFinish(target);
            } else if (resource.status == Resource.Status.ERROR) {
                // Profile read failed — fall back to the standard user app.
                startAndFinish(new Intent(this, MainActivity.class));
            }
        });
        authViewModel.resolveRoute();
    }

    private void startAndFinish(@NonNull Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        splashHandler.removeCallbacks(routeRunnable);
        super.onDestroy();
    }
}