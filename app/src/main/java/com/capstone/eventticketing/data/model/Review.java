package com.capstone.eventticketing.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/** Mirrors a document in the {@code Reviews} collection. */
public class Review {

    @DocumentId
    private String reviewId;
    private String eventId;
    private String userId;
    private String userName;
    private int rating;       // 1–5
    private String comment;
    private Timestamp createdAt;

    public Review() { }

    public Review(String eventId, String userId, String userName, int rating, String comment) {
        this.eventId = eventId;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}