package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;

import com.capstone.eventticketing.data.model.Seat;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a list of {@link Seat}s for a venue of a given capacity, arranged into
 * rows of a fixed width. Pure function — no Firestore access — so it is trivially
 * unit-testable and is reused by both seat-grid creation and any preview UI.
 */
public final class SeatMapGenerator {

    private SeatMapGenerator() { /* no instances */ }

    /** Seats per row. 10 gives a clean, readable grid for typical venue sizes. */
    public static final int SEATS_PER_ROW = 10;

    /**
     * Generates {@code capacity} seats labelled A1..A10, B1..B10, ... assigning
     * every seat the supplied {@code tier} and {@code price}.
     *
     * @param capacity total number of seats to generate (must be > 0).
     * @param tier     pricing tier name (e.g. "Standard").
     * @param price    price for each seat in this tier.
     * @return ordered list of fresh AVAILABLE seats.
     */
    @NonNull
    public static List<Seat> generate(int capacity, @NonNull String tier, double price) {
        List<Seat> seats = new ArrayList<>();
        if (capacity <= 0) return seats;

        for (int i = 0; i < capacity; i++) {
            int rowIndex = i / SEATS_PER_ROW;       // 0 -> A, 1 -> B, ...
            int columnIndex = (i % SEATS_PER_ROW) + 1; // 1-based column
            String row = rowLabel(rowIndex);
            String seatId = row + columnIndex;       // e.g. "A1", "B7"
            seats.add(new Seat(seatId, row, columnIndex, tier, price));
        }
        return seats;
    }

    /**
     * Converts a zero-based row index to a spreadsheet-style label:
     * 0->A .. 25->Z, 26->AA, 27->AB, ... so capacity is effectively unbounded.
     */
    @NonNull
    private static String rowLabel(int rowIndex) {
        StringBuilder sb = new StringBuilder();
        int n = rowIndex;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = (n / 26) - 1;
        }
        return sb.toString();
    }
}