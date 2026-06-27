package com.capstone.eventticketing.ui.home;

import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.databinding.ItemHomeHeaderBinding;
import com.capstone.eventticketing.databinding.ItemHomeRailBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the idle Home screen's status sections as a vertical list of headers
 * and horizontal rails. The featured banner is handled separately by the
 * existing carousel, so this adapter intentionally has no banner view type.
 * Rail scroll positions are preserved across rebinds via {@link HomeSection.Rail#railId}.
 */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @NonNull private final HomeInteractionListener listener;
    @NonNull private List<HomeSection> sections = new ArrayList<>();

    /** Remembers each rail's horizontal scroll offset by railId, across rebinds. */
    @NonNull private final Map<String, Parcelable> railScrollState = new HashMap<>();

    public HomeAdapter(@NonNull HomeInteractionListener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<HomeSection> newSections) {
        this.sections = newSections;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return sections.get(position).type();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == HomeSection.TYPE_RAIL) {
            return new RailVH(
                    ItemHomeRailBinding.inflate(inflater, parent, false), listener, railScrollState);
        }
        // TYPE_HEADER (and any unexpected type) renders as a header.
        return new HeaderVH(ItemHomeHeaderBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HomeSection section = sections.get(position);
        if (holder instanceof RailVH && section instanceof HomeSection.Rail) {
            ((RailVH) holder).bind((HomeSection.Rail) section);
        } else if (holder instanceof HeaderVH && section instanceof HomeSection.Header) {
            ((HeaderVH) holder).bind((HomeSection.Header) section);
        }
        // Any mismatch (e.g. a stray Banner) is skipped rather than crashing.
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof RailVH) {
            ((RailVH) holder).saveScrollState();
        }
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    // ---- ViewHolders ----

    static class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemHomeHeaderBinding b;
        HeaderVH(@NonNull ItemHomeHeaderBinding b) { super(b.getRoot()); this.b = b; }
        void bind(@NonNull HomeSection.Header header) {
            b.tvSectionTitle.setText(header.title);
        }
    }

    static class RailVH extends RecyclerView.ViewHolder {
        private final ItemHomeRailBinding b;
        private final RailAdapter railAdapter;
        private final Map<String, Parcelable> scrollStore;
        private String currentRailId;

        RailVH(@NonNull ItemHomeRailBinding b, @NonNull HomeInteractionListener listener,
               @NonNull Map<String, Parcelable> scrollStore) {
            super(b.getRoot());
            this.b = b;
            this.scrollStore = scrollStore;
            this.railAdapter = new RailAdapter(listener);
            b.rvRail.setLayoutManager(new LinearLayoutManager(
                    b.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false));
            b.rvRail.setAdapter(railAdapter);
        }

        void bind(@NonNull HomeSection.Rail rail) {
            this.currentRailId = rail.railId;
            railAdapter.submitList(rail.movies);
            RecyclerView.LayoutManager lm = b.rvRail.getLayoutManager();
            Parcelable saved = scrollStore.get(rail.railId);
            if (lm != null && saved != null) {
                lm.onRestoreInstanceState(saved);
            } else if (lm != null) {
                lm.scrollToPosition(0);
            }
        }

        void saveScrollState() {
            RecyclerView.LayoutManager lm = b.rvRail.getLayoutManager();
            if (lm != null && currentRailId != null) {
                scrollStore.put(currentRailId, lm.onSaveInstanceState());
            }
        }
    }
}