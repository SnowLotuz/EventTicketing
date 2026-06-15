package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the current user's profile data. Bypasses Firebase Storage entirely
 * to avoid billing requirements by utilizing direct URL strings.
 */
public class UserRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ROLE = "role";
    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public UserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    /** Fetches the current user's profile document. */
    public LiveData<Resource<User>> getProfile() {
        MutableLiveData<Resource<User>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(snap -> {
                    User user = snap.toObject(User.class);
                    if (user == null) {
                        result.setValue(Resource.error("Profile not found."));
                    } else {
                        result.setValue(Resource.success(user));
                    }
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load profile.")));

        return result;
    }

    /** Updates the editable profile fields (name and/or avatarUrl). */
    public LiveData<Resource<Boolean>> updateProfile(@NonNull String name, String avatarUrl) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        if (avatarUrl != null) updates.put("avatarUrl", avatarUrl);

        firestore.collection(USERS_COLLECTION).document(uid)
                .update(updates)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to update profile.")));

        return result;
    }

    // =========================================================================
    // PHẦN THÊM MỚI CHO STEP 14: ADMIN USER DIRECTORY
    // =========================================================================

    /**
     * Fetches all registered users, ordered by name, for the admin directory.
     * Admin-only in practice (enforced by security rules on the read).
     *
     * @return LiveData emitting Loading then Success(list) or Error.
     */
    public LiveData<Resource<List<User>>> getAllUsers() {
        MutableLiveData<Resource<List<User>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(USERS_COLLECTION)
                .orderBy(FIELD_NAME)
                .get()
                .addOnSuccessListener(snap -> result.setValue(
                        Resource.success(snap.toObjects(User.class))))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load users.")));

        return result;
    }

    /**
     * Updates a target user's role. Permitted only for admins by security rules.
     *
     * @param targetUserId the user whose role changes.
     * @param newRole      "admin" or "user".
     * @return LiveData emitting Loading then Success(true) or Error.
     */
    public LiveData<Resource<Boolean>> updateUserRole(@NonNull String targetUserId,
                                                      @NonNull String newRole) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(USERS_COLLECTION).document(targetUserId)
                .update(FIELD_ROLE, newRole)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to update role.")));

        return result;
    }

    /** @return the current admin's own UID, to prevent self-demotion in the UI. */
    public String getCurrentUserId() {
        return currentUid();
    }
}