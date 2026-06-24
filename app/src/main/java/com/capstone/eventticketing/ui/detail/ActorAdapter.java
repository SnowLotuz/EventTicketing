package com.capstone.eventticketing.ui.detail;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Actor;
import com.capstone.eventticketing.databinding.ItemActorBinding;

/** Horizontal cast list on the movie detail screen, in billing (array) order. */
public class ActorAdapter extends ListAdapter<Actor, ActorAdapter.VH> {

    public ActorAdapter() { super(DIFF); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActorBinding b = ItemActorBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemActorBinding b;
        VH(@NonNull ItemActorBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull Actor actor) {
            b.tvActorName.setText(actor.getName());
            Glide.with(b.ivActor.getContext())
                    .load(actor.getImageUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .centerCrop()
                    .into(b.ivActor);
        }
    }

    private static final DiffUtil.ItemCallback<Actor> DIFF =
            new DiffUtil.ItemCallback<Actor>() {
                @Override
                public boolean areItemsTheSame(@NonNull Actor a, @NonNull Actor b) {
                    return a.getName() != null && a.getName().equals(b.getName());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Actor a, @NonNull Actor b) {
                    return a.getName() != null && a.getName().equals(b.getName())
                            && a.getImageUrl() != null && a.getImageUrl().equals(b.getImageUrl());
                }
            };
}