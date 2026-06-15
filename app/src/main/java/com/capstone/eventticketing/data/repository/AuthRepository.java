package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Single source of truth for authentication operations. Wraps FirebaseAuth and
 * the Firestore {@code Users} document lifecycle. All Auth/Firestore SDK calls
 * are isolated here — ViewModels and UI never touch Firebase directly.
 */
public class AuthRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String DEFAULT_ROLE = "user";

    @NonNull private final FirebaseAuth firebaseAuth;
    @NonNull private final FirebaseFirestore firestore;

    public AuthRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    /** @return the currently signed-in user, or {@code null} if none. */
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    /**
     * Authenticates an existing user with email/password.
     *
     * @return LiveData emitting {@link Resource} states; success carries the {@link FirebaseUser}.
     */
    public LiveData<Resource<FirebaseUser>> login(@NonNull String email, @NonNull String password) {
        MutableLiveData<Resource<FirebaseUser>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> result.setValue(Resource.success(authResult.getUser())))
                .addOnFailureListener(e -> result.setValue(Resource.error(mapAuthError(e))));

        return result;
    }

    /**
     * Creates a new Auth account and provisions the matching Firestore {@code Users} document.
     * The operation only reports success once the profile document is written, so downstream
     * screens can safely assume the user record exists.
     *
     * @return LiveData emitting {@link Resource} states; success carries the {@link FirebaseUser}.
     */
    public LiveData<Resource<FirebaseUser>> register(@NonNull String name,
                                                     @NonNull String email,
                                                     @NonNull String password) {
        MutableLiveData<Resource<FirebaseUser>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        result.setValue(Resource.error("Registration failed: user is null."));
                        return;
                    }
                    createUserProfile(firebaseUser, name, email, result);
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(mapAuthError(e))));

        return result;
    }

    /** Writes the initial Users document. Rolls the emitted state forward to success/error. */
    private void createUserProfile(@NonNull FirebaseUser firebaseUser,
                                   @NonNull String name,
                                   @NonNull String email,
                                   @NonNull MutableLiveData<Resource<FirebaseUser>> result) {
        User user = new User(firebaseUser.getUid(), name, email, DEFAULT_ROLE);

        firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid())
                .set(user)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(firebaseUser)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Account created but profile setup failed: " + e.getMessage())));
    }

    /**
     * Sends a password reset email.
     *
     * @return LiveData emitting {@link Resource} states; success carries a {@link Boolean}.
     */
    public LiveData<Resource<Boolean>> sendPasswordReset(@NonNull String email) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(mapAuthError(e))));

        return result;
    }

    public void logout() {
        firebaseAuth.signOut();
    }

    /** Translates raw Firebase exceptions into user-friendly messages. */
    private String mapAuthError(@NonNull Exception e) {
        String message = e.getMessage();
        return message != null ? message : "Authentication failed. Please try again.";
    }

    /**
     * Fetches the current user's {@code Users} profile document. Required after
     * authentication to determine role-based routing (admin vs. standard user).
     *
     * @return LiveData emitting Loading then Success(User) or Error.
     */
    public LiveData<Resource<User>> getUserProfile(@NonNull String uid) {
        MutableLiveData<Resource<User>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        result.setValue(Resource.error("User profile not found."));
                        return;
                    }
                    User user = snapshot.toObject(User.class);
                    if (user == null) {
                        result.setValue(Resource.error("Failed to parse user profile."));
                    } else {
                        result.setValue(Resource.success(user));
                    }
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(mapAuthError(e))));

        return result;
    }
}