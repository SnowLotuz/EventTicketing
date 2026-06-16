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
import com.capstone.eventticketing.databinding.ItemMovieCardBinding;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * RecyclerView adapter for movie cards. Uses ListAdapter + DiffUtil for
 * efficient updates. Wishlist membership is tracked separately so heart icons
 * update without rebinding the whole list.
 */
public class MovieAdapter extends ListAdapter<Movie, MovieAdapter.MovieViewHolder> {

    /** Click + wishlist callbacks handled by the Fragment. */
    public interface OnMovieInteractionListener {
        void onMovieClick(@NonNull Movie movie);
        void onWishlistToggle(@NonNull Movie movie, boolean isCurrentlyWishlisted);
    }

    @NonNull private final OnMovieInteractionListener listener;
    @NonNull private final Set<String> wishlistedIds = new HashSet<>();

    public MovieAdapter(@NonNull OnMovieInteractionListener listener) {
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
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMovieCardBinding binding = ItemMovieCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MovieViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class MovieViewHolder extends RecyclerView.ViewHolder {
        private final ItemMovieCardBinding binding;

        MovieViewHolder(@NonNull ItemMovieCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Movie movie) {
            binding.tvTitle.setText(movie.getTitle());
            binding.chipGenre.setText(movie.getGenre());
            binding.tvDuration.setText(movie.getFormattedDuration());

            if (movie.getRating() != null && movie.getRating().getTotalReviews() > 0) {
                binding.tvRating.setText(String.format(java.util.Locale.getDefault(),
                        "%.1f", movie.getRating().getAverageScore()));
            } else {
                binding.tvRating.setText("—");
            }

            if (movie.getSeatMap() != null && movie.getSeatMap().getLowestPrice() > 0) {
                binding.tvPrice.setText(String.format(java.util.Locale.getDefault(),
                        "From $%.2f", movie.getSeatMap().getLowestPrice()));
            } else {
                binding.tvPrice.setText(R.string.price_free);
            }

            Glide.with(binding.ivPoster.getContext())
                    .load(movie.getPosterUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(binding.ivPoster);

            boolean isWishlisted = movie.getMovieId() != null && wishlistedIds.contains(movie.getMovieId());
            binding.btnWishlist.setIconResource(
                    isWishlisted ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

            binding.getRoot().setOnClickListener(v -> listener.onMovieClick(movie));
            binding.btnWishlist.setOnClickListener(v ->
                    listener.onWishlistToggle(movie, isWishlisted));
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
                    return oldItem.getTitle() != null
                            && oldItem.getTitle().equals(newItem.getTitle())
                            && oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };
}