package com.capstone.eventticketing.util;

/**
 * Pure pricing utility for the automatic "same-price discount" tiers.
 *
 * <p>A movie qualifies when all three conditions hold:
 * <ul>
 *   <li>base (lowest) price ≤ {@link #MAX_BASE_PRICE}</li>
 *   <li>booked seats ≤ {@link #MAX_BOOKED_SEATS}</li>
 *   <li>it is not a blockbuster</li>
 * </ul>
 *
 * <p>Qualifying prices are flattened into fixed buckets:
 * <pre>
 *   $5–7  → $5
 *   $8–10 → $8
 *   $11–13 → $11
 *   $14–15 → $12
 * </pre>
 *
 * <p>No Android or Firebase dependencies: the caller fetches the booked-seat
 * count (a Firestore aggregate) and passes it in. This keeps the rule testable
 * and lets both the detail screen and checkout share one source of truth. The
 * same-price discount is mutually exclusive with promo codes (enforced at
 * checkout): a discounted movie blocks promo entry.
 */
public final class PriceTierCalculator {

    /** Inclusive upper bound on base price for discount eligibility. */
    public static final double MAX_BASE_PRICE = 15.0;
    /** Inclusive upper bound on booked seats for discount eligibility. */
    public static final int MAX_BOOKED_SEATS = 30;

    private PriceTierCalculator() { }

    /** Immutable result of a tier evaluation. */
    public static final class Result {
        /** True if the movie qualifies and the price was discounted. */
        public final boolean discounted;
        /** The original base price (unchanged), for the struck-through display. */
        public final double originalPrice;
        /** The price to charge: the bucket price if discounted, else the original. */
        public final double finalPrice;

        Result(boolean discounted, double originalPrice, double finalPrice) {
            this.discounted = discounted;
            this.originalPrice = originalPrice;
            this.finalPrice = finalPrice;
        }
    }

    /**
     * Evaluates the same-price discount for a movie.
     *
     * @param basePrice        the movie's lowest seat price
     * @param bookedSeatCount  current number of booked seats (from the aggregate query)
     * @param isBlockbuster    whether the movie is flagged blockbuster
     * @return a {@link Result}; {@code discounted == false} (final == original) when
     *         the movie doesn't qualify or the price falls outside all buckets.
     */
    public static Result evaluate(double basePrice, int bookedSeatCount, boolean isBlockbuster) {
        // Gate: all three conditions must hold, and price must be positive.
        boolean qualifies = basePrice > 0
                && basePrice <= MAX_BASE_PRICE
                && bookedSeatCount <= MAX_BOOKED_SEATS
                && !isBlockbuster;

        if (!qualifies) {
            return new Result(false, basePrice, basePrice);
        }

        double bucket = bucketPrice(basePrice);
        if (bucket <= 0 || bucket >= basePrice) {
            // Price didn't land in a bucket, or the bucket isn't actually a
            // discount (e.g. base already at/under the floor) — charge original.
            return new Result(false, basePrice, basePrice);
        }
        return new Result(true, basePrice, bucket);
    }

    /**
     * Maps a qualifying base price to its bucket floor, or 0 if it falls in no
     * bucket. Buckets are inclusive ranges on the base price.
     */
    private static double bucketPrice(double basePrice) {
        if (basePrice >= 5 && basePrice <= 7)   return 5;
        if (basePrice >= 8 && basePrice <= 10)  return 8;
        if (basePrice >= 11 && basePrice <= 13) return 11;
        if (basePrice >= 14 && basePrice <= 15) return 12;
        return 0; // e.g. $1–4 — below the lowest bucket, no discount
    }

    /**
     * Convenience: true if a movie is currently discounted (used at checkout to
     * block promo codes, enforcing mutual exclusivity).
     */
    public static boolean isDiscounted(double basePrice, int bookedSeatCount, boolean isBlockbuster) {
        return evaluate(basePrice, bookedSeatCount, isBlockbuster).discounted;
    }
}