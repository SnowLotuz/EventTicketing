package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.data.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable set of search/filter criteria plus a pure function to apply them.
 * Filtering is done in memory (Firestore cannot do substring search), which for
 * a catalog of this size is instant and far simpler than a search-service.
 */
public final class EventFilter {

    public static final String CATEGORY_ALL = "All";

    @NonNull public final String query;          // title substring, may be empty
    @NonNull public final String category;       // CATEGORY_ALL or a specific category
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
     * new list. Each event must satisfy every active criterion (AND semantics).
     */
    @NonNull
    public List<Event> apply(@Nullable List<Event> source) {
        List<Event> out = new ArrayList<>();
        if (source == null) return out;

        String q = query.trim().toLowerCase(Locale.getDefault());

        for (Event e : source) {
            if (!matchesQuery(e, q)) continue;
            if (!matchesCategory(e)) continue;
            if (!matchesPrice(e)) continue;
            if (!matchesDate(e)) continue;
            out.add(e);
        }
        return out;
    }

    private boolean matchesQuery(@NonNull Event e, @NonNull String q) {
        if (q.isEmpty()) return true;
        String title = e.getTitle() != null ? e.getTitle().toLowerCase(Locale.getDefault()) : "";
        return title.contains(q);
    }

    private boolean matchesCategory(@NonNull Event e) {
        if (CATEGORY_ALL.equals(category)) return true;
        return category.equals(e.getCategory());
    }

    private boolean matchesPrice(@NonNull Event e) {
        double price = (e.getSeatMap() != null) ? e.getSeatMap().getLowestPrice() : 0d;
        return price >= minPrice && price <= maxPrice;
    }

    private boolean matchesDate(@NonNull Event e) {
        if (startDateMillis == 0L && endDateMillis == 0L) return true;
        if (e.getEventDate() == null) return false;
        long t = e.getEventDate().toDate().getTime();
        if (startDateMillis > 0L && t < startDateMillis) return false;
        if (endDateMillis > 0L && t > endDateMillis) return false;
        return true;
    }
}