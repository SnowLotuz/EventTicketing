package com.capstone.eventticketing.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.databinding.ItemAdminEventBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Management list adapter. Each row exposes a "more" action menu via the host. */
public class AdminEventAdapter extends ListAdapter<Event, AdminEventAdapter.VH> {

    public interface OnEventActionListener {
        void onEventOptions(@NonNull Event event);
    }

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    @NonNull private final OnEventActionListener listener;

    public AdminEventAdapter(@NonNull OnEventActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminEventBinding b = ItemAdminEventBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemAdminEventBinding b;
        VH(@NonNull ItemAdminEventBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull Event event) {
            b.tvTitle.setText(event.getTitle());
            b.chipStatus.setText(event.getStatus());
            if (event.getEventDate() != null) {
                b.tvDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
            } else {
                b.tvDate.setText("");
            }
            Glide.with(b.ivThumb.getContext())
                    .load(event.getImageUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(b.ivThumb);

            b.btnMore.setOnClickListener(v -> listener.onEventOptions(event));
            b.getRoot().setOnClickListener(v -> listener.onEventOptions(event));
        }
    }

    private static final DiffUtil.ItemCallback<Event> DIFF =
            new DiffUtil.ItemCallback<Event>() {
                @Override
                public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                    return a.getEventId() != null && a.getEventId().equals(b.getEventId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                    return a.getTitle() != null && a.getTitle().equals(b.getTitle())
                            && a.getStatus() != null && a.getStatus().equals(b.getStatus());
                }
            };
}