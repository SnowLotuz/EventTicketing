package com.capstone.eventticketing.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

/**
 * Mirrors a document in the {@code Promotions} collection. Validation logic is
 * intentionally NOT here (a model shouldn't decide business rules); see
 * {@link com.capstone.eventticketing.util.PromoValidator}.
 */
public class Promotion {

    public static final String TYPE_PERCENTAGE = "PERCENTAGE";
    public static final String TYPE_FIXED = "FIXED";

    @DocumentId
    private String promoId;
    private String code;
    private String discountType;
    private double discountValue;
    private double minPurchase;
    private Timestamp expiryDate;
    private int usageLimit;
    private int timesUsed;

    public Promotion() { }

    public String getPromoId() { return promoId; }
    public void setPromoId(String promoId) { this.promoId = promoId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }

    public double getMinPurchase() { return minPurchase; }
    public void setMinPurchase(double minPurchase) { this.minPurchase = minPurchase; }

    public Timestamp getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Timestamp expiryDate) { this.expiryDate = expiryDate; }

    public int getUsageLimit() { return usageLimit; }
    public void setUsageLimit(int usageLimit) { this.usageLimit = usageLimit; }

    public int getTimesUsed() { return timesUsed; }
    public void setTimesUsed(int timesUsed) { this.timesUsed = timesUsed; }

    @Exclude
    public boolean isExhausted() {
        return usageLimit > 0 && timesUsed >= usageLimit;
    }
}