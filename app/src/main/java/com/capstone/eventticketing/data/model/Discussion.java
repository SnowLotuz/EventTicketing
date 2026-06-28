package com.capstone.eventticketing.data.model;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * A single flat discussion comment on a movie. Distinct from {@link Review}:
 * discussions are open while a movie is COMING_SOON or NOW_SHOWING and locked
 * once it has ENDED, whereas reviews are attendance-gated and post-event. The
 * author's display name is denormalized so the comment list renders without a
 * per-row user lookup.
 */
public class Discussion {

    @DocumentId
    private String discussionId;

    private String movieId;
    private String userId;
    private String userName;
    private String comment;

    @ServerTimestamp
    private Timestamp createdAt;

    /** Required no-arg constructor for Firestore deserialization. */
    public Discussion() { }

    public Discussion(@NonNull String movieId, @NonNull String userId,
                      @NonNull String userName, @NonNull String comment) {
        this.movieId = movieId;
        this.userId = userId;
        this.userName = userName;
        this.comment = comment;
        // createdAt is filled by @ServerTimestamp on write.
    }

    public String getDiscussionId() { return discussionId; }
    public void setDiscussionId(String discussionId) { this.discussionId = discussionId; }

    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = movieId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /** @return createdAt in epoch millis, or 0 if not yet set (pending server write). */
    @Exclude
    public long getCreatedAtMillis() {
        return createdAt != null ? createdAt.toDate().getTime() : 0L;
    }
}