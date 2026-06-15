package com.capstone.eventticketing.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.databinding.ItemEventCardBinding;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * RecyclerView adapter for event cards. Uses ListAdapter + DiffUtil for
 * efficient updates. Wishlist membership is tracked separately so heart icons
 * update without rebinding the whole list.
 */
public class EventAdapter extends ListAdapter<Event, EventAdapter.EventViewHolder> {

    /** Click + wishlist callbacks handled by the Fragment (which talks to the ViewModel). */
    public interface OnEventInteractionListener {
        void onEventClick(@NonNull Event event);
        void onWishlistToggle(@NonNull Event event, boolean isCurrentlyWishlisted);
    }

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy · h:mm a", Locale.getDefault());

    @NonNull private final OnEventInteractionListener listener;
    @NonNull private final Set<String> wishlistedIds = new HashSet<>();

    public EventAdapter(@NonNull OnEventInteractionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setWishlistedIds(@NonNull Set<String> ids) {
        wishlistedIds.clear();
        wishlistedIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventCardBinding binding = ItemEventCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final ItemEventCardBinding binding;

        EventViewHolder(@NonNull ItemEventCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Event event) {
            binding.tvEventTitle.setText(event.getTitle());
            binding.tvEventVenue.setText(event.getVenue());
            binding.chipCategory.setText(event.getCategory());

            if (event.getEventDate() != null) {
                binding.tvEventDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
            } else {
                binding.tvEventDate.setText("");
            }

            if (event.getSeatMap() != null && event.getSeatMap().getLowestPrice() > 0) {
                binding.tvEventPrice.setText(String.format(Locale.getDefault(),
                        "From $%.2f", event.getSeatMap().getLowestPrice()));
            } else {
                binding.tvEventPrice.setText(R.string.price_free);
            }

            Glide.with(binding.ivEventImage.getContext())
                    .load(event.getImageUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(binding.ivEventImage);

            boolean isWishlisted = event.getEventId() != null && wishlistedIds.contains(event.getEventId());
            binding.btnWishlist.setIconResource(
                    isWishlisted ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

            binding.getRoot().setOnClickListener(v -> listener.onEventClick(event));
            binding.btnWishlist.setOnClickListener(v ->
                    listener.onWishlistToggle(event, isWishlisted));
        }
    }

    private static final DiffUtil.ItemCallback<Event> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Event>() {
                @Override
                public boolean areItemsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
                    return oldItem.getEventId() != null
                            && oldItem.getEventId().equals(newItem.getEventId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
                    return oldItem.getTitle() != null
                            && oldItem.getTitle().equals(newItem.getTitle())
                            && oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };
}