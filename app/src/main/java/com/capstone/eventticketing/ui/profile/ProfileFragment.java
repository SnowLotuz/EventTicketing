package com.capstone.eventticketing.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.databinding.FragmentProfileBinding;
import com.capstone.eventticketing.ui.auth.LoginActivity;
import com.capstone.eventticketing.util.Resource;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String currentName = "";
    private String currentAvatarUrl = "";

    public ProfileFragment() { super(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        binding.btnChangeAvatar.setOnClickListener(v -> showChangeAvatarDialog());
        binding.cardAvatar.setOnClickListener(v -> showChangeAvatarDialog());
        binding.rowEditProfile.setOnClickListener(v -> showEditNameDialog());

        // Cập nhật Step 15: Chuyển sang màn hình Lịch sử đặt vé
        binding.rowHistory.setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(), BookingHistoryActivity.class)));

        binding.rowLogout.setOnClickListener(v -> confirmLogout());

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getProfile().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                bindProfile(resource.data);
            }
        });

        viewModel.getUpdateState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                toast(getString(R.string.profile_updated));
                viewModel.loadProfile(); // Reload to capture the latest server state safely
            } else if (resource.status == Resource.Status.ERROR) {
                toast(resource.message);
            }
        });
    }

    private void bindProfile(@NonNull User user) {
        currentName = user.getName() != null ? user.getName() : "";
        currentAvatarUrl = user.getAvatarUrl() != null ? user.getAvatarUrl() : "";
        binding.tvName.setText(currentName);
        binding.tvEmail.setText(user.getEmail());
        loadAvatar(currentAvatarUrl);
    }

    private void loadAvatar(String url) {
        if (url == null || url.isEmpty()) {
            // Automatically generate a beautiful placeholder based on their name if no avatar exists
            url = generateDefaultAvatar(currentName);
        }
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .centerCrop()
                .into(binding.ivAvatar);
    }

    private String generateDefaultAvatar(String name) {
        try {
            String encodedName = URLEncoder.encode(name, "UTF-8");
            // Generates a beautiful slate/blue colored initial circle avatar
            return String.format("https://ui-avatars.com/api/?name=%s&background=1D4ED8&color=ffffff&size=256", encodedName);
        } catch (UnsupportedEncodingException e) {
            return "https://ui-avatars.com/api/?name=User&background=1D4ED8&color=ffffff";
        }
    }

    private void showChangeAvatarDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Update Profile Picture");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(44, 24, 44, 24);

        final EditText input = new EditText(requireContext());
        input.setHint("Paste any image link URL from the web");
        input.setText(currentAvatarUrl.contains("ui-avatars.com") ? "" : currentAvatarUrl);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Save Link", (d, w) -> {
            String url = input.getText().toString().trim();
            viewModel.updateProfile(currentName, url);
        });

        builder.setNeutralButton("Auto-Generate From Name", (d, w) -> {
            String autoUrl = generateDefaultAvatar(currentName);
            viewModel.updateProfile(currentName, autoUrl);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showEditNameDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.profile_edit_name_hint);
        input.setText(currentName);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_edit)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        toast(getString(R.string.profile_edit_name_hint));
                        return;
                    }
                    // If they are using an auto-generated initial, adapt the seed url to the new name seamlessly
                    String updatedAvatarUrl = currentAvatarUrl;
                    if (currentAvatarUrl.isEmpty() || currentAvatarUrl.contains("ui-avatars.com")) {
                        updatedAvatarUrl = generateDefaultAvatar(newName);
                    }
                    viewModel.updateProfile(newName, updatedAvatarUrl);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.admin_logout)
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton(R.string.admin_logout, (d, w) -> {
                    viewModel.logout();
                    startActivity(new android.content.Intent(requireContext(), LoginActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    requireActivity().finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String msg) {
        if (msg != null && isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}