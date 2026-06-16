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

    /** Seats per row (Original). */
    public static final int SEATS_PER_ROW = 10;

    /** Fixed cinema dimensions per spec. */
    public static final int CINEMA_ROWS = 10;       // A .. J
    public static final int CINEMA_COLS = 15;       // 1 .. 15
    public static final int CINEMA_CAPACITY = CINEMA_ROWS * CINEMA_COLS; // 150

    /**
     * Generates {@code capacity} seats labelled A1..A10, B1..B10, ... assigning
     * every seat the supplied {@code tier} and {@code price}.
     */
    @NonNull
    public static List<Seat> generate(int capacity, @NonNull String tier, double price) {
        List<Seat> seats = new ArrayList<>();
        if (capacity <= 0) return seats;

        for (int i = 0; i < capacity; i++) {
            int rowIndex = i / SEATS_PER_ROW;
            int columnIndex = (i % SEATS_PER_ROW) + 1;
            String row = rowLabel(rowIndex);
            String seatId = row + columnIndex;
            seats.add(new Seat(seatId, row, columnIndex, tier, price));
        }
        return seats;
    }

    /**
     * Generates the fixed 150-seat cinema layout: 10 rows (A–J) × 15 columns (1–15).
     * Every seat is assigned the supplied {@code tier} and {@code price}.
     *
     * @return ordered list of 150 fresh AVAILABLE seats (A1..A15, B1..B15, ... J15).
     */
    @NonNull
    public static List<Seat> generateCinema(@NonNull String tier, double price) {
        List<Seat> seats = new ArrayList<>(CINEMA_CAPACITY);
        for (int r = 0; r < CINEMA_ROWS; r++) {
            char rowLetter = (char) ('A' + r);     // 'A' .. 'J'
            String row = String.valueOf(rowLetter);
            for (int c = 1; c <= CINEMA_COLS; c++) {
                String seatId = row + c;            // "A1" .. "J15"
                seats.add(new Seat(seatId, row, c, tier, price));
            }
        }
        return seats;
    }

    /**
     * Converts a zero-based row index to a spreadsheet-style label.
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