package com.capstone.eventticketing.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.databinding.ActivityAdminUsersBinding;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Admin user directory. Lists all users and allows promoting/demoting roles via
 * a confirmation dialog. Prevents an admin from changing their own role to avoid
 * self-lockout. View-only; data work is in {@link AdminUsersViewModel}.
 */
public class AdminUsersActivity extends AppCompatActivity
        implements AdminUserAdapter.OnUserClickListener {

    private ActivityAdminUsersBinding binding;
    private AdminUsersViewModel viewModel;
    private AdminUserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AdminUsersViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new AdminUserAdapter(this);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUsers.setAdapter(adapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getUsers().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmer.setVisibility(View.VISIBLE);
                    binding.shimmer.startShimmer();
                    binding.rvUsers.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    renderUsers(resource.data);
                    break;
                case ERROR:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    binding.rvUsers.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getRoleUpdateState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                Toast.makeText(this, R.string.admin_role_updated, Toast.LENGTH_SHORT).show();
            } else if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            binding.rvUsers.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.rvUsers.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(users);
        }
    }

    @Override
    public void onUserClick(@NonNull User user) {
        if (user.getUserId() == null) return;

        // Guard: an admin cannot change their own role (prevents self-lockout).
        if (user.getUserId().equals(viewModel.getCurrentUserId())) {
            Toast.makeText(this, R.string.admin_role_self, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isAdmin = user.isAdmin();
        String action = getString(isAdmin ? R.string.admin_demote : R.string.admin_promote);
        String newRole = isAdmin ? "user" : "admin";

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_role_dialog_title)
                .setMessage(action + " — " + user.getName() + "?")
                .setPositiveButton(action, (d, w) ->
                        viewModel.updateRole(user.getUserId(), newRole))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}