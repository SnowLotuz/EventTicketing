package com.capstone.eventticketing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.databinding.ActivityRegisterBinding;
import com.capstone.eventticketing.ui.main.MainActivity;

/**
 * Registration screen. Strictly a View: observes {@link AuthViewModel} and
 * renders state. All validation and Firebase work happens upstream.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.btnRegister.setOnClickListener(v -> authViewModel.register(
                getText(binding.etName),
                getText(binding.etEmail),
                getText(binding.etPhone),
                getText(binding.etPassword),
                getText(binding.etConfirmPassword)));

        binding.tvGoLogin.setOnClickListener(v -> finish());
    }

    private void observeViewModel() {
        authViewModel.getRegisterState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    showToast("Account created successfully.");
                    navigateToMain();
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
        binding.btnRegister.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        binding.btnRegister.setEnabled(!isLoading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
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