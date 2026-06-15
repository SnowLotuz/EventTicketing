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
import com.capstone.eventticketing.databinding.ItemCarouselEventBinding;

/** Featured-events carousel adapter for the home ViewPager2. */
public class CarouselAdapter extends ListAdapter<Event, CarouselAdapter.CarouselViewHolder> {

    public interface OnCarouselClickListener {
        void onCarouselClick(@NonNull Event event);
    }

    @NonNull private final OnCarouselClickListener listener;

    public CarouselAdapter(@NonNull OnCarouselClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCarouselEventBinding binding = ItemCarouselEventBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CarouselViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class CarouselViewHolder extends RecyclerView.ViewHolder {
        private final ItemCarouselEventBinding binding;

        CarouselViewHolder(@NonNull ItemCarouselEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Event event) {
            binding.tvCarouselTitle.setText(event.getTitle());
            Glide.with(binding.ivCarouselImage.getContext())
                    .load(event.getImageUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(binding.ivCarouselImage);
            binding.getRoot().setOnClickListener(v -> listener.onCarouselClick(event));
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
                    return oldItem.getTitle() != null && oldItem.getTitle().equals(newItem.getTitle());
                }
            };
}