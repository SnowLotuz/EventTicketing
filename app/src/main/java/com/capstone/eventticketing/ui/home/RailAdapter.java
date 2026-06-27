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
import com.capstone.eventticketing.databinding.ItemRailCardBinding;

/** Movie cards inside a horizontal Home rail. */
public class RailAdapter extends ListAdapter<Movie, RailAdapter.VH> {

    @NonNull private final HomeInteractionListener listener;

    public RailAdapter(@NonNull HomeInteractionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRailCardBinding b = ItemRailCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemRailCardBinding b;
        private final HomeInteractionListener listener;

        VH(@NonNull ItemRailCardBinding b, @NonNull HomeInteractionListener listener) {
            super(b.getRoot());
            this.b = b;
            this.listener = listener;
        }

        void bind(@NonNull Movie movie) {
            b.tvTitle.setText(movie.getTitle());
            b.tvSubtitle.setText(movie.getGenre());
            Glide.with(b.ivPoster.getContext())
                    .load(movie.getPosterUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(b.ivPoster);
            b.getRoot().setOnClickListener(v -> listener.onMovieClick(movie));
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
                            && (a.getTitle() == null ? b.getTitle() == null
                            : a.getTitle().equals(b.getTitle()));
                }
            };
}