package com.capstone.eventticketing.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ItemBannerPageBinding;

/** Pages of the featured-blockbuster banner at the top of Home. */
public class BannerAdapter extends ListAdapter<Movie, BannerAdapter.VH> {

    @NonNull private final HomeInteractionListener listener;

    public BannerAdapter(@NonNull HomeInteractionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBannerPageBinding b = ItemBannerPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemBannerPageBinding b;
        private final HomeInteractionListener listener;

        VH(@NonNull ItemBannerPageBinding b, @NonNull HomeInteractionListener listener) {
            super(b.getRoot());
            this.b = b;
            this.listener = listener;
        }

        void bind(@NonNull Movie movie) {
            b.tvBannerTitle.setText(movie.getTitle());
            b.tvBannerSubtitle.setText(subtitle(movie));
            Glide.with(b.ivBannerPoster.getContext())
                    .load(movie.getPosterUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(b.ivBannerPoster);
            b.getRoot().setOnClickListener(v -> listener.onMovieClick(movie));
        }

        private String subtitle(@NonNull Movie movie) {
            String status = Movie.STATUS_NOW_SHOWING.equals(movie.getStatus())
                    ? "Now Showing"
                    : Movie.STATUS_COMING_SOON.equals(movie.getStatus())
                    ? "Coming Soon" : "";
            String genre = movie.getGenre() != null ? movie.getGenre() : "";
            if (status.isEmpty()) return genre;
            if (genre.isEmpty()) return status;
            return status + " · " + genre;
        }
    }

    private static final DiffUtil.ItemCallback<Movie> DIFF =
            new DiffUtil.ItemCallback<Movie>() {
                @Override
                public boolean areItemsTheSame(@NonNull Movie a, @NonNull Movie b) {
                    return a.getMovieId() != null && a.getMovieId().equals(b.getMovieId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Movie a, @NonNull Movie b) {
                    return a.getMovieId() != null && a.getMovieId().equals(b.getMovieId())
                            && eq(a.getTitle(), b.getTitle())
                            && eq(a.getStatus(), b.getStatus())
                            && eq(a.getPosterUrl(), b.getPosterUrl());
                }
                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}