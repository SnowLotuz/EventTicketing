package com.capstone.eventticketing.ui.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Review;
import com.capstone.eventticketing.databinding.ItemReviewBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Renders the read-only reviews list on the event detail screen. Star rows are
 * built programmatically per review so each row reflects its own 1–5 rating.
 */
public class ReviewAdapter extends ListAdapter<Review, ReviewAdapter.VH> {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public ReviewAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding b = ItemReviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemReviewBinding b;
        VH(@NonNull ItemReviewBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull Review review) {
            String name = review.getUserName() != null ? review.getUserName() : "Anonymous";
            b.tvUserName.setText(name);
            b.tvInitial.setText(name.isEmpty()
                    ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
            b.tvComment.setText(review.getComment());

            if (review.getCreatedAt() != null) {
                b.tvDate.setText(DATE_FORMAT.format(review.getCreatedAt().toDate()));
            } else {
                b.tvDate.setText("");
            }

            // Comment row collapses if empty (rating-only review).
            b.tvComment.setVisibility(
                    review.getComment() == null || review.getComment().trim().isEmpty()
                            ? android.view.View.GONE : android.view.View.VISIBLE);

            buildStars(b.layoutStars, review.getRating());
        }

        /** Renders 5 small stars, filled up to the rating value. */
        private void buildStars(@NonNull LinearLayout container, int rating) {
            container.removeAllViews();
            Context ctx = container.getContext();
            int sizePx = (int) (16 * ctx.getResources().getDisplayMetrics().density);
            for (int i = 1; i <= 5; i++) {
                ImageView star = new ImageView(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
                lp.setMarginStart(2);
                star.setLayoutParams(lp);
                star.setImageResource(R.drawable.ic_star);
                star.setColorFilter(ctx.getColor(
                        i <= rating ? R.color.accent_blue : R.color.slate_200));
                container.addView(star);
            }
        }
    }

    private static final DiffUtil.ItemCallback<Review> DIFF =
            new DiffUtil.ItemCallback<Review>() {
                @Override
                public boolean areItemsTheSame(@NonNull Review a, @NonNull Review b) {
                    return a.getReviewId() != null && a.getReviewId().equals(b.getReviewId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Review a, @NonNull Review b) {
                    return a.getRating() == b.getRating()
                            && a.getComment() != null && a.getComment().equals(b.getComment());
                }
            };
}