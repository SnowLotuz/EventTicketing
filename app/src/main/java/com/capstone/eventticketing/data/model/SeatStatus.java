package com.capstone.eventticketing.data.model;

/**
 * Canonical seat state values stored in the {@code events/{id}/seats} subcollection.
 * Centralized to avoid magic strings across the booking pipeline.
 *
 * <p>State machine:
 * <pre>
 * AVAILABLE --(user selects)--> HELD --(payment confirmed)--> BOOKED
 * ^                          |
 * +----(hold expires)--------+
 * </pre>
 */
public final class SeatStatus {

    private SeatStatus() { /* no instances */ }

    public static final String AVAILABLE = "AVAILABLE";
    public static final String HELD = "HELD";
    public static final String BOOKED = "BOOKED";
}