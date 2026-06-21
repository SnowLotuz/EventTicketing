package com.capstone.eventticketing.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ItemCarouselEventBinding;

/** Featured-movies carousel adapter for the home ViewPager2. */
public class CarouselAdapter extends ListAdapter<Movie, CarouselAdapter.CarouselViewHolder> {

    public interface OnCarouselClickListener {
        void onCarouselClick(@NonNull Movie movie);
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

        void bind(@NonNull Movie movie) {
            binding.tvCarouselTitle.setText(movie.getTitle());
            Glide.with(binding.ivCarouselImage.getContext())
                    .load(movie.getPosterUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(binding.ivCarouselImage);
            binding.getRoot().setOnClickListener(v -> listener.onCarouselClick(movie));
        }
    }

    private static final DiffUtil.ItemCallback<Movie> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Movie>() {
                @Override
                public boolean areItemsTheSame(@NonNull Movie oldItem, @NonNull Movie newItem) {
                    return oldItem.getMovieId() != null
                            && oldItem.getMovieId().equals(newItem.getMovieId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Movie oldItem, @NonNull Movie newItem) {
                    return oldItem.getTitle() != null && oldItem.getTitle().equals(newItem.getTitle());
                }
            };
}