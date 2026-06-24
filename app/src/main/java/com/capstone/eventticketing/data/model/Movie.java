package com.capstone.eventticketing.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Mirrors a document in the Firestore {@code movies} collection. Nested
 * {@code seatMap} and {@code rating} objects are preserved from the previous
 * Event model so the booking, checkout, and rating pipelines consume Movie with
 * no change to their transaction logic.
 */
public class Movie {

    // Showing status — kept analogous to the old event status so any status-gated
    // logic (e.g. post-viewing ratings) maps over directly.
    public static final String STATUS_NOW_SHOWING = "NOW_SHOWING";
    public static final String STATUS_COMING_SOON = "COMING_SOON";
    public static final String STATUS_ENDED = "ENDED";

    @DocumentId
    private String movieId;
    private String title;
    private String description;
    private String genre;
    private int durationMinutes;
    private String posterUrl;
    private com.google.firebase.Timestamp releaseDate;
    private String status;
    private SeatMap seatMap;
    private Rating rating;

    // --- New in Milestone 1 ---

    /** Backend-only flag. Set via the create form's checkbox; never shown to users
     * directly. Drives the "Must-Watch" banner and excludes the movie from
     * same-price discounts. */
    private boolean blockbuster;

    /** Optional trailer link (e.g. a YouTube URL). Launched externally on tap. */
    private String trailerUrl;

    /** Cast in billing order. Never null after construction. */
    private List<Actor> actors = new ArrayList<>();

    /** Required empty constructor for Firestore deserialization. */
    public Movie() { }

    public Movie(String title, String description, String genre, int durationMinutes,
                 String posterUrl, com.google.firebase.Timestamp releaseDate, String status) {
        this.title = title;
        this.description = description;
        this.genre = genre;
        this.durationMinutes = durationMinutes;
        this.posterUrl = posterUrl;
        this.releaseDate = releaseDate;
        this.status = status;
    }

    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = movieId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public com.google.firebase.Timestamp getReleaseDate() { return releaseDate; }
    public void setReleaseDate(com.google.firebase.Timestamp releaseDate) { this.releaseDate = releaseDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public SeatMap getSeatMap() { return seatMap; }
    public void setSeatMap(SeatMap seatMap) { this.seatMap = seatMap; }

    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }

    // --- Getters & Setters for Milestone 1 fields ---

    @com.google.firebase.firestore.PropertyName("blockbuster")
    public boolean isBlockbuster() { return blockbuster; }

    @com.google.firebase.firestore.PropertyName("blockbuster")
    public void setBlockbuster(boolean blockbuster) { this.blockbuster = blockbuster; }

    public String getTrailerUrl() { return trailerUrl; }
    public void setTrailerUrl(String trailerUrl) { this.trailerUrl = trailerUrl; }

    public List<Actor> getActors() { return actors; }

    public void setActors(List<Actor> actors) {
        this.actors = (actors != null) ? actors : new ArrayList<>();
    }

    /** Convenience for UI gating — true when there's at least one trailer link. */
    @com.google.firebase.firestore.Exclude
    public boolean hasTrailer() {
        return trailerUrl != null && !trailerUrl.trim().isEmpty();
    }

    @Exclude
    public boolean isEnded() {
        return STATUS_ENDED.equals(status);
    }

    /** Formats the runtime as "2h 15m" for display. */
    @Exclude
    public String getFormattedDuration() {
        if (durationMinutes <= 0) return "";
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }

    /** Nested {@code seatMap} object — identical structure to the prior model. */
    public static class SeatMap {
        private int totalCapacity;
        private Map<String, Double> pricingTiers = new HashMap<>();

        public SeatMap() { }

        public int getTotalCapacity() { return totalCapacity; }
        public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }

        public Map<String, Double> getPricingTiers() { return pricingTiers; }
        public void setPricingTiers(Map<String, Double> pricingTiers) { this.pricingTiers = pricingTiers; }

        @Exclude
        public double getLowestPrice() {
            if (pricingTiers == null || pricingTiers.isEmpty()) return 0d;
            double min = Double.MAX_VALUE;
            for (Double price : pricingTiers.values()) {
                if (price != null && price < min) min = price;
            }
            return min == Double.MAX_VALUE ? 0d : min;
        }
    }

    /** Nested {@code rating} object — identical to the prior model. */
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