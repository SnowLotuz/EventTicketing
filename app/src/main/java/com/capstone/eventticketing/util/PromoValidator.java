package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;

import com.capstone.eventticketing.data.model.Promotion;

/**
 * Pure, side-effect-free validation and discount computation for promo codes.
 * Used by both the checkout preview and the booking transaction so the
 * previewed discount and the charged discount can never diverge.
 */
public final class PromoValidator {

    private PromoValidator() { /* no instances */ }

    /** Outcome of validating a promo against a given subtotal. */
    public static final class Result {
        public final boolean valid;
        public final String errorMessage;   // null when valid
        public final double discountAmount;  // 0 when invalid
        public final double finalAmount;     // == subtotal when invalid

        private Result(boolean valid, String errorMessage, double discountAmount, double finalAmount) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
        }

        static Result invalid(@NonNull String message, double subtotal) {
            return new Result(false, message, 0d, subtotal);
        }

        static Result valid(double discount, double finalAmount) {
            return new Result(true, null, discount, finalAmount);
        }
    }

    /**
     * Validates a promotion against a subtotal and computes the discount.
     * Checks existence, expiry, usage limit, and minimum purchase, then applies
     * PERCENTAGE or FIXED discount, clamped so the total never goes below zero.
     *
     * @param promo    the fetched promotion (null = code not found).
     * @param subtotal the pre-discount total of selected seats.
     */
    @NonNull
    public static Result validate(Promotion promo, double subtotal) {
        if (promo == null) {
            return Result.invalid("Promo code not found.", subtotal);
        }
        if (promo.getExpiryDate() != null
                && promo.getExpiryDate().toDate().getTime() < System.currentTimeMillis()) {
            return Result.invalid("This promo code has expired.", subtotal);
        }
        if (promo.isExhausted()) {
            return Result.invalid("This promo code is no longer available.", subtotal);
        }
        if (subtotal < promo.getMinPurchase()) {
            return Result.invalid(String.format(
                    "Requires a minimum purchase of $%.2f.", promo.getMinPurchase()), subtotal);
        }

        double discount = computeDiscount(promo, subtotal);
        // Clamp: never discount more than the subtotal.
        if (discount > subtotal) discount = subtotal;
        double finalAmount = subtotal - discount;
        return Result.valid(discount, finalAmount);
    }

    /** Computes the raw discount amount for a valid promo (no clamping). */
    private static double computeDiscount(@NonNull Promotion promo, double subtotal) {
        if (Promotion.TYPE_PERCENTAGE.equals(promo.getDiscountType())) {
            return subtotal * (promo.getDiscountValue() / 100d);
        }
        if (Promotion.TYPE_FIXED.equals(promo.getDiscountType())) {
            return promo.getDiscountValue();
        }
        return 0d; // unknown type → no discount, fail safe
    }
}