package com.capstone.eventticketing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.databinding.ActivityLoginBinding;
import com.capstone.eventticketing.ui.admin.AdminDashboardActivity;
import com.capstone.eventticketing.ui.main.MainActivity;
import com.capstone.eventticketing.util.Resource;

/**
 * Login screen. Strictly a View: observes {@link AuthViewModel} state and
 * renders Loading / Success / Error. Contains zero authentication logic.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Already authenticated → skip straight to home.
        if (authViewModel.isUserLoggedIn()) {
            setLoading(true);
            authViewModel.resolveRoute();
        }

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> authViewModel.login(
                getText(binding.etEmail),
                getText(binding.etPassword)));

        binding.tvForgotPassword.setOnClickListener(v ->
                authViewModel.sendPasswordReset(getText(binding.etEmail)));

        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void observeViewModel() {
        authViewModel.getLoginState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    // Auth done — now resolve role-based destination.
                    authViewModel.resolveRoute();
                    break;
                case ERROR:
                    setLoading(false);
                    showToast(resource.message);
                    break;
            }
        });

        authViewModel.getRouteState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    routeTo(resource.data);
                    break;
                case ERROR:
                    // Profile read failed — fall back to the standard user app.
                    setLoading(false);
                    routeTo(AuthViewModel.RouteDestination.USER);
                    break;
            }
        });

        authViewModel.getResetState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    showToast("Password reset email sent.");
                    break;
                case ERROR:
                    setLoading(false);
                    showToast(resource.message);
                    break;
            }
        });

        authViewModel.getValidationError().observe(this, this::showToast);
    }

    private void setLoading(boolean isLoading) {
        binding.lottieLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        binding.btnLogin.setEnabled(!isLoading);
    }

    private void routeTo(AuthViewModel.RouteDestination destination) {
        Intent intent = (destination == AuthViewModel.RouteDestination.ADMIN)
                ? new Intent(this, AdminDashboardActivity.class)
                : new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String getText(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void showToast(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}