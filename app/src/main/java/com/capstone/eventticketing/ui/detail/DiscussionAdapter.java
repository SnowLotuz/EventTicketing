package com.capstone.eventticketing.ui.detail;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.data.model.Discussion;
import com.capstone.eventticketing.databinding.ItemDiscussionBinding;

/** Flat chronological list of discussion comments on the detail screen. */
public class DiscussionAdapter extends ListAdapter<Discussion, DiscussionAdapter.VH> {

    public DiscussionAdapter() { super(DIFF); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDiscussionBinding b = ItemDiscussionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemDiscussionBinding b;
        VH(@NonNull ItemDiscussionBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull Discussion d) {
            b.tvAuthor.setText(d.getUserName());
            b.tvComment.setText(d.getComment());

            long millis = d.getCreatedAtMillis();
            if (millis > 0) {
                b.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                        millis, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS));
                b.tvTime.setVisibility(android.view.View.VISIBLE);
            } else {
                // createdAt not yet confirmed by server (just posted).
                b.tvTime.setText(com.capstone.eventticketing.R.string.discussion_just_now);
                b.tvTime.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    private static final DiffUtil.ItemCallback<Discussion> DIFF =
            new DiffUtil.ItemCallback<Discussion>() {
                @Override
                public boolean areItemsTheSame(@NonNull Discussion a, @NonNull Discussion b) {
                    return a.getDiscussionId() != null
                            && a.getDiscussionId().equals(b.getDiscussionId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Discussion a, @NonNull Discussion b) {
                    return a.getComment() != null && a.getComment().equals(b.getComment())
                            && a.getCreatedAtMillis() == b.getCreatedAtMillis();
                }
            };
}