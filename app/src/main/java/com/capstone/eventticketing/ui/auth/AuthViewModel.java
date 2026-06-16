package com.capstone.eventticketing.ui.auth;

import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.data.repository.AuthRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseUser;

/**
 * Drives the Login / Register / Password-reset screens. Owns all input
 * validation and delegates network operations to {@link AuthRepository}.
 * The UI observes the exposed LiveData and never calls Firebase directly.
 */
public class AuthViewModel extends ViewModel {

    private static final int MIN_PASSWORD_LENGTH = 6;

    @NonNull private final AuthRepository authRepository;

    /** Where the UI should navigate after a successful auth + profile load. */
    public enum RouteDestination { ADMIN, USER } // Đã thêm Enum điều hướng

    // Triggers that swap in the underlying repository LiveData via switchMap.
    private final MutableLiveData<Credentials> loginTrigger = new MutableLiveData<>();
    private final MutableLiveData<RegistrationData> registerTrigger = new MutableLiveData<>();
    private final MutableLiveData<String> resetTrigger = new MutableLiveData<>();

    // Local validation errors surfaced to the UI without hitting the network.
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    private final LiveData<Resource<FirebaseUser>> loginState;
    private final LiveData<Resource<FirebaseUser>> registerState;
    private final LiveData<Resource<Boolean>> resetState;

    // Đã thêm state điều hướng
    private final MutableLiveData<Resource<RouteDestination>> routeState = new MutableLiveData<>();

    public AuthViewModel() {
        this.authRepository = new AuthRepository();

        loginState = Transformations.switchMap(loginTrigger,
                c -> authRepository.login(c.email, c.password));

        registerState = Transformations.switchMap(registerTrigger,
                r -> authRepository.register(r.name, r.email, r.phoneNumber, r.password));

        resetState = Transformations.switchMap(resetTrigger,
                authRepository::sendPasswordReset);
    }

    public LiveData<Resource<FirebaseUser>> getLoginState() { return loginState; }
    public LiveData<Resource<FirebaseUser>> getRegisterState() { return registerState; }
    public LiveData<Resource<Boolean>> getResetState() { return resetState; }
    public LiveData<String> getValidationError() { return validationError; }
    public LiveData<Resource<RouteDestination>> getRouteState() { return routeState; } // Đã thêm getter

    public boolean isUserLoggedIn() {
        return authRepository.getCurrentUser() != null;
    }

    /**
     * Resolves the post-login destination by reading the user's role from
     * Firestore. Call this once a FirebaseUser is available (fresh login or
     * an already-signed-in session).
     */
    public void resolveRoute() {
        FirebaseUser current = authRepository.getCurrentUser();
        if (current == null) {
            routeState.setValue(Resource.error("Not signed in."));
            return;
        }
        routeState.setValue(Resource.loading());

        LiveData<Resource<User>> source = authRepository.getUserProfile(current.getUid());
        source.observeForever(new androidx.lifecycle.Observer<Resource<User>>() {
            @Override
            public void onChanged(Resource<User> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    routeState.setValue(Resource.success(
                            resource.data.isAdmin() ? RouteDestination.ADMIN : RouteDestination.USER));
                } else {
                    // On profile-read failure, default to the safer USER route rather than blocking login.
                    routeState.setValue(Resource.error(
                            resource.message != null ? resource.message : "Failed to load profile."));
                }
                source.removeObserver(this);
            }
        });
    }

    /** Validates inputs locally; only fires the repository call when valid. */
    public void login(String email, String password) {
        if (isEmailInvalid(email)) {
            validationError.setValue("Please enter a valid email address.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            validationError.setValue("Password cannot be empty.");
            return;
        }
        loginTrigger.setValue(new Credentials(email.trim(), password));
    }

    // Đã cập nhật: Nhận thêm phoneNumber và thêm logic validate số điện thoại
    public void register(String name, String email, String phoneNumber, String password, String confirmPassword) {
        if (TextUtils.isEmpty(name) || name.trim().length() < 2) {
            validationError.setValue("Please enter your name.");
            return;
        }
        if (isEmailInvalid(email)) {
            validationError.setValue("Please enter a valid email address.");
            return;
        }
        if (isPhoneInvalid(phoneNumber)) {
            validationError.setValue("Please enter a valid phone number.");
            return;
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            validationError.setValue("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            validationError.setValue("Passwords do not match.");
            return;
        }
        registerTrigger.setValue(new RegistrationData(name.trim(), email.trim(), phoneNumber.trim(), password));
    }

    public void sendPasswordReset(String email) {
        if (isEmailInvalid(email)) {
            validationError.setValue("Enter your email to reset the password.");
            return;
        }
        resetTrigger.setValue(email.trim());
    }

    public void logout() {
        authRepository.logout();
    }

    private boolean isEmailInvalid(String email) {
        return TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    /**
     * Validates a phone number: non-empty and matching a reasonable phone pattern
     * (digits, optional leading +, spaces/dashes/parentheses allowed, 7–15 digits).
     */
    private boolean isPhoneInvalid(String phone) {
        if (TextUtils.isEmpty(phone)) return true;
        String trimmed = phone.trim();
        // Android's built-in pattern is permissive; pair it with a digit-count check.
        if (!android.util.Patterns.PHONE.matcher(trimmed).matches()) return true;
        int digits = trimmed.replaceAll("\\D", "").length();
        return digits < 7 || digits > 15;
    }

    // --- Lightweight input holders for switchMap triggers ---

    private static class Credentials {
        final String email;
        final String password;
        Credentials(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    // Đã cập nhật: Data class giờ có thêm trường phoneNumber
    private static class RegistrationData {
        final String name;
        final String email;
        final String phoneNumber;
        final String password;
        RegistrationData(String name, String email, String phoneNumber, String password) {
            this.name = name;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.password = password;
        }
    }
}