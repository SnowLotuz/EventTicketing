package com.capstone.eventticketing.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.databinding.ItemAdminUserBinding;

import java.util.Locale;

/** Directory adapter. Tapping a row asks the host to present role actions. */
public class AdminUserAdapter extends ListAdapter<User, AdminUserAdapter.VH> {

    public interface OnUserClickListener {
        void onUserClick(@NonNull User user);
    }

    @NonNull private final OnUserClickListener listener;

    public AdminUserAdapter(@NonNull OnUserClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminUserBinding b = ItemAdminUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemAdminUserBinding b;
        VH(@NonNull ItemAdminUserBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull User user) {
            String name = user.getName() != null ? user.getName() : "Unknown";
            b.tvName.setText(name);
            b.tvEmail.setText(user.getEmail());
            b.tvInitial.setText(name.isEmpty()
                    ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault()));

            boolean isAdmin = user.isAdmin();
            b.chipRole.setText(isAdmin ? R.string.admin_role_admin : R.string.admin_role_user);
            b.chipRole.setChipBackgroundColorResource(
                    isAdmin ? R.color.accent_blue_light : R.color.slate_200);
            b.chipRole.setTextColor(b.getRoot().getContext().getColor(
                    isAdmin ? R.color.accent_blue : R.color.slate_600));

            b.getRoot().setOnClickListener(v -> listener.onUserClick(user));
        }
    }

    private static final DiffUtil.ItemCallback<User> DIFF =
            new DiffUtil.ItemCallback<User>() {
                @Override
                public boolean areItemsTheSame(@NonNull User a, @NonNull User b) {
                    return a.getUserId() != null && a.getUserId().equals(b.getUserId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull User a, @NonNull User b) {
                    return a.getName() != null && a.getName().equals(b.getName())
                            && a.getRole() != null && a.getRole().equals(b.getRole());
                }
            };
}