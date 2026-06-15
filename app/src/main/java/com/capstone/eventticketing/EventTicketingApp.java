package com.capstone.eventticketing;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Application entry point. Configures Firestore's local persistent cache once,
 * before any other Firestore access, so the Digital Wallet (My Tickets) works
 * with zero connectivity at venues — a CRITICAL PRD requirement.
 */
public class EventTicketingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        configureFirestoreOfflineCache();
    }

    /**
     * Enables Firestore's modern persistent cache. Any document previously loaded
     * while online (e.g. the user's tickets) remains readable offline, and writes
     * made offline are queued and synced on reconnection.
     */
    private void configureFirestoreOfflineCache() {
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                                // Unlimited so tickets are never evicted before an event.
                                .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                .build())
                .build();

        // Must be set before any other use of the Firestore instance.
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }
}