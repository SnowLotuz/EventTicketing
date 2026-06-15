package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.data.model.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, in-memory filter criteria for the event catalog. Evaluates an event
 * against an optional search query, category, date range, and price range.
 */
public class EventFilter {

    @Nullable public final String query;
    @Nullable public final String category;
    public final long dateStartMillis; // 0 = no lower bound
    public final long dateEndMillis;   // Long.MAX_VALUE = no upper bound
    public final double maxPrice;      // Double.MAX_VALUE = no upper bound

    public EventFilter(@Nullable String query, @Nullable String category,
                       long dateStartMillis, long dateEndMillis, double maxPrice) {
        this.query = query != null ? query.toLowerCase().trim() : null;
        this.category = category;
        this.dateStartMillis = dateStartMillis;
        this.dateEndMillis = dateEndMillis;
        this.maxPrice = maxPrice;
    }

    /** @return true if the event satisfies every defined criteria in this filter. */
    public boolean matches(@NonNull Event event) {
        // Category (exact match)
        if (category != null && !category.equals(event.getCategory())) {
            return false;
        }

        // Date range
        long eventTime = event.getEventDate() != null ? event.getEventDate().toDate().getTime() : 0L;
        if (eventTime < dateStartMillis || eventTime > dateEndMillis) {
            return false;
        }

        // Price ceiling (evaluates the lowest available tier)
        double price = event.getSeatMap() != null ? event.getSeatMap().getLowestPrice() : 0d;
        if (price > maxPrice) {
            return false;
        }

        // Text search (substring match on title or venue, case-insensitive)
        if (query != null && !query.isEmpty()) {
            String title = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
            String venue = event.getVenue() != null ? event.getVenue().toLowerCase() : "";
            if (!title.contains(query) && !venue.contains(query)) {
                return false;
            }
        }

        return true;
    }

    /** Applies this filter to a list of events. */
    @NonNull
    public List<Event> apply(@NonNull List<Event> events) {
        List<Event> filtered = new ArrayList<>();
        for (Event e : events) {
            if (matches(e)) filtered.add(e);
        }
        return filtered;
    }
}