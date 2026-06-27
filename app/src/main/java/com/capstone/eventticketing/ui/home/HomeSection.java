package com.capstone.eventticketing.ui.home;

import androidx.annotation.NonNull;

import com.capstone.eventticketing.data.model.Movie;

import java.util.List;

/**
 * A single row in the Home screen's vertical list. Home is rendered as one
 * RecyclerView over a heterogeneous list of these descriptors; {@link HomeAdapter}
 * maps each concrete subtype to a view type and layout.
 *
 * <p>Subtypes are intentionally small, immutable carriers: the ViewModel builds
 * the ordered list of sections in memory from a single movie load, and the
 * adapter only renders them. This keeps all section-selection logic in one
 * testable place and out of the view layer.
 */
public abstract class HomeSection {

    /** Stable view-type ids, also used by the adapter's {@code getItemViewType}. */
    public static final int TYPE_BANNER = 0;
    public static final int TYPE_HEADER = 1;
    public static final int TYPE_RAIL = 2;

    public abstract int type();

    /** A horizontal pager of featured (blockbuster) movies at the top of Home. */
    public static final class Banner extends HomeSection {
        @NonNull public final List<Movie> movies;
        public Banner(@NonNull List<Movie> movies) { this.movies = movies; }
        @Override public int type() { return TYPE_BANNER; }
    }

    /** A section title row (e.g. "Now Showing"). */
    public static final class Header extends HomeSection {
        @NonNull public final String title;
        public Header(@NonNull String title) { this.title = title; }
        @Override public int type() { return TYPE_HEADER; }
    }

    /**
     * A horizontal rail of movie cards under a header. {@code railId} lets the
     * adapter keep each rail's horizontal scroll position independent when the
     * outer list rebinds.
     */
    public static final class Rail extends HomeSection {
        @NonNull public final String railId;
        @NonNull public final List<Movie> movies;
        public Rail(@NonNull String railId, @NonNull List<Movie> movies) {
            this.railId = railId;
            this.movies = movies;
        }
        @Override public int type() { return TYPE_RAIL; }
    }
}