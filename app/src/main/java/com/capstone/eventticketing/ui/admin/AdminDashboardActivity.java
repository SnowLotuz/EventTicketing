package com.capstone.eventticketing.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.ActivityAdminDashboardBinding;
import com.capstone.eventticketing.ui.auth.AuthViewModel;
import com.capstone.eventticketing.ui.auth.LoginActivity;

/**
 * Entry point for users with the {@code admin} role. Hosts navigation to event
 * creation and (later) the user directory, plus high-level KPIs. View-only:
 * any data/auth work is delegated to ViewModels.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupToolbar();
        setupActions();
    }

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                authViewModel.logout();
                navigateToLogin();
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        binding.cardCreateEvent.setOnClickListener(v ->
                startActivity(new Intent(this, CreateEventActivity.class)));

        binding.cardUserDirectory.setOnClickListener(v ->
                startActivity(new Intent(this, AdminUsersActivity.class)));

        binding.cardManageEvents.setOnClickListener(v ->
                startActivity(new Intent(this, AdminEventsActivity.class)));

        binding.cardCheckin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminEventsActivity.class)));
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}