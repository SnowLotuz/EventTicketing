package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.data.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable set of search/filter criteria plus a pure function to apply them to
 * movies. Filtering is done in memory (Firestore cannot do substring search),
 * which for a catalog of this size is instant and far simpler than a search service.
 *
 * <p>Note: {@code category} maps to {@link Movie#getGenre()} and the date range
 * maps to {@link Movie#getReleaseDate()}, so the existing filter sheet UI keeps working.
 */
public final class EventFilter {

    public static final String CATEGORY_ALL = "All";
    /**
     * The single source of truth for selectable genres. Every screen that shows
     * genre chips or a genre dropdown (home, filter sheet, create, edit) reads
     * from here, so the lists can never drift out of sync — a drift would make
     * filtering silently return empty results.
     */
    public static final java.util.List<String> GENRES = java.util.Arrays.asList(
            "Action", "Comedy", "Drama", "Sci-Fi", "Horror",
            "Animation", "Thriller", "Romance");

    /** Genres prefixed with "All" — for filter UIs that include an All option. */
    public static final java.util.List<String> GENRES_WITH_ALL = java.util.Arrays.asList(
            CATEGORY_ALL, "Action", "Comedy", "Drama", "Sci-Fi", "Horror",
            "Animation", "Thriller", "Romance");
    @NonNull public final String query;          // title substring, may be empty
    @NonNull public final String category;       // CATEGORY_ALL or a specific genre
    public final double minPrice;                // inclusive
    public final double maxPrice;                // inclusive
    public final long startDateMillis;           // 0 = no lower bound
    public final long endDateMillis;             // 0 = no upper bound

    public EventFilter(@NonNull String query, @NonNull String category,
                       double minPrice, double maxPrice,
                       long startDateMillis, long endDateMillis) {
        this.query = query;
        this.category = category;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.startDateMillis = startDateMillis;
        this.endDateMillis = endDateMillis;
    }

    /** A no-op filter that matches everything (default state). */
    public static EventFilter none() {
        return new EventFilter("", CATEGORY_ALL, 0d, Double.MAX_VALUE, 0L, 0L);
    }

    /** Returns a copy with only the query changed (for real-time search typing). */
    public EventFilter withQuery(@NonNull String newQuery) {
        return new EventFilter(newQuery, category, minPrice, maxPrice, startDateMillis, endDateMillis);
    }

    /** True if any deep-filter (non-query) criterion is narrower than the default. */
    public boolean hasActiveDeepFilters() {
        return !CATEGORY_ALL.equals(category)
                || minPrice > 0d
                || maxPrice < Double.MAX_VALUE
                || startDateMillis > 0L
                || endDateMillis > 0L;
    }

    /**
     * Applies all criteria to a source list. Pure: no side effects, returns a
     * new list. Each movie must satisfy every active criterion (AND semantics).
     */
    @NonNull
    public List<Movie> apply(@Nullable List<Movie> source) {
        List<Movie> out = new ArrayList<>();
        if (source == null) return out;

        String q = query.trim().toLowerCase(Locale.getDefault());

        for (Movie m : source) {
            if (!matchesQuery(m, q)) continue;
            if (!matchesGenre(m)) continue;
            if (!matchesPrice(m)) continue;
            if (!matchesDate(m)) continue;
            out.add(m);
        }
        return out;
    }

    private boolean matchesQuery(@NonNull Movie m, @NonNull String q) {
        if (q.isEmpty()) return true;
        String title = m.getTitle() != null ? m.getTitle().toLowerCase(Locale.getDefault()) : "";
        return title.contains(q);
    }

    private boolean matchesGenre(@NonNull Movie m) {
        if (CATEGORY_ALL.equals(category)) return true;
        return category.equals(m.getGenre());
    }

    private boolean matchesPrice(@NonNull Movie m) {
        double price = (m.getSeatMap() != null) ? m.getSeatMap().getLowestPrice() : 0d;
        return price >= minPrice && price <= maxPrice;
    }

    private boolean matchesDate(@NonNull Movie m) {
        if (startDateMillis == 0L && endDateMillis == 0L) return true;
        if (m.getReleaseDate() == null) return false;
        long t = m.getReleaseDate().toDate().getTime();
        if (startDateMillis > 0L && t < startDateMillis) return false;
        if (endDateMillis > 0L && t > endDateMillis) return false;
        return true;
    }
}