package com.capstone.eventticketing.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Thin wrapper over SharedPreferences for small persistent flags. Centralizes
 * key names so they're never duplicated/mistyped across screens.
 */
public final class PrefsManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ONBOARDING_SEEN = "onboarding_seen";

    private final SharedPreferences prefs;

    public PrefsManager(@NonNull Context context) {
        // Application context to avoid leaking an Activity.
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingSeen() {
        return prefs.getBoolean(KEY_ONBOARDING_SEEN, false);
    }

    public void setOnboardingSeen(boolean seen) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply();
    }
}