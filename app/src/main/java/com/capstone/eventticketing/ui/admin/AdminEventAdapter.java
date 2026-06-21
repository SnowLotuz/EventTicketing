package com.capstone.eventticketing.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ItemAdminEventBinding;

/** Management list adapter. Each row exposes a "more" action menu via the host. */
public class AdminEventAdapter extends ListAdapter<Movie, AdminEventAdapter.VH> {

    public interface OnEventActionListener {
        void onMovieOptions(@NonNull Movie movie);
    }

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

        void bind(@NonNull Movie movie) {
            b.tvTitle.setText(movie.getTitle());
            b.chipStatus.setText(movie.getStatus());

            String genre = movie.getGenre() != null ? movie.getGenre() : "";
            String duration = movie.getFormattedDuration();
            String meta = (!genre.isEmpty() && !duration.isEmpty())
                    ? genre + " · " + duration
                    : genre + duration;
            b.tvMeta.setText(meta);

            Glide.with(b.ivThumb.getContext())
                    .load(movie.getPosterUrl())
                    .placeholder(R.color.slate_200)
                    .error(R.color.slate_200)
                    .centerCrop()
                    .into(b.ivThumb);

            b.btnMore.setOnClickListener(v -> listener.onMovieOptions(movie));
            b.getRoot().setOnClickListener(v -> listener.onMovieOptions(movie));
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
                    return a.getTitle() != null && a.getTitle().equals(b.getTitle())
                            && a.getStatus() != null && a.getStatus().equals(b.getStatus());
                }
            };
}