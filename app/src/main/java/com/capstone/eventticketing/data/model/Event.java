package com.capstone.eventticketing.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors a document in the Firestore {@code Events} collection.
 * Nested maps ({@code seatMap}, {@code rating}) are modeled as static inner
 * classes for type-safe (de)serialization via {@code toObject(Event.class)}.
 */
public class Event {

    // Status constants — avoids magic strings across the codebase.
    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_ENDED = "ENDED";

    @DocumentId
    private String eventId;
    private String title;
    private String description;
    private String category;
    private String imageUrl;
    private String venue;
    private com.google.firebase.Timestamp eventDate;
    private String status;
    private SeatMap seatMap;
    private Rating rating;

    /** Required empty constructor for Firestore deserialization. */
    public Event() { }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public com.google.firebase.Timestamp getEventDate() { return eventDate; }
    public void setEventDate(com.google.firebase.Timestamp eventDate) { this.eventDate = eventDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public SeatMap getSeatMap() { return seatMap; }
    public void setSeatMap(SeatMap seatMap) { this.seatMap = seatMap; }

    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }

    @Exclude
    public boolean isEnded() {
        return STATUS_ENDED.equals(status);
    }

    /** Nested {@code seatMap} object. */
    public static class SeatMap {
        private int totalCapacity;
        private Map<String, Double> pricingTiers = new HashMap<>();

        public SeatMap() { }

        public int getTotalCapacity() { return totalCapacity; }
        public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }

        public Map<String, Double> getPricingTiers() { return pricingTiers; }
        public void setPricingTiers(Map<String, Double> pricingTiers) { this.pricingTiers = pricingTiers; }

        /** @return the lowest tier price, used for the "from $X" card label. Returns 0 if none. */
        @Exclude
        public double getLowestPrice() {
            if (pricingTiers == null || pricingTiers.isEmpty()) return 0d;
            double min = Double.MAX_VALUE;

            // Dùng Object và Number để hứng dữ liệu, tránh lỗi ép kiểu ngầm của Firebase
            for (Object priceObj : pricingTiers.values()) {
                if (priceObj instanceof Number) {
                    double price = ((Number) priceObj).doubleValue();
                    if (price < min) {
                        min = price;
                    }
                }
            }
            return min == Double.MAX_VALUE ? 0d : min;
        }
    }

    /** Nested {@code rating} object. */
    public static class Rating {
        private double averageScore;
        private int totalReviews;

        public Rating() { }

        @PropertyName("averageScore")
        public double getAverageScore() { return averageScore; }
        @PropertyName("averageScore")
        public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

        @PropertyName("totalReviews")
        public int getTotalReviews() { return totalReviews; }
        @PropertyName("totalReviews")
        public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }
    }
}