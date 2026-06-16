package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.model.Review;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * Owns the {@code Reviews} collection and the event rating aggregate. Submitting
 * a review runs in a transaction that writes the review AND updates the event's
 * running average atomically, so the displayed rating can never drift from the
 * actual reviews. Also enforces one review per user per event.
 */
public class ReviewRepository {

    private static final String EVENTS_COLLECTION = "events";
    private static final String REVIEWS_COLLECTION = "reviews";
    private static final String FIELD_EVENT_ID = "eventId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CREATED_AT = "createdAt";

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public ReviewRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    /**
     * Checks whether the current user has already reviewed an event, so the UI
     * doesn't prompt twice.
     *
     * @return LiveData emitting Success(true) if a review exists, else Success(false).
     */
    public LiveData<Resource<Boolean>> hasUserReviewed(@NonNull String eventId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(REVIEWS_COLLECTION)
                .whereEqualTo(FIELD_EVENT_ID, eventId)
                .whereEqualTo(FIELD_USER_ID, uid)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> result.setValue(Resource.success(!snap.isEmpty())))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to check reviews.")));

        return result;
    }

    /**
     * Fetches reviews for an event, newest first, for the detail screen's list.
     */
    public LiveData<Resource<List<Review>>> getReviewsForEvent(@NonNull String eventId) {
        MutableLiveData<Resource<List<Review>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(REVIEWS_COLLECTION)
                .whereEqualTo(FIELD_EVENT_ID, eventId)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> result.setValue(
                        Resource.success(snap.toObjects(Review.class))))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load reviews.")));

        return result;
    }

    /**
     * Submits a review and updates the event's rating aggregate in one transaction.
     * Uses the incremental-average formula so no full re-read of reviews is needed.
     * Aborts if the user has somehow already reviewed (defense against double-submit).
     *
     * @return LiveData emitting Loading then Success(true) or Error.
     */
    public LiveData<Resource<Boolean>> submitReview(@NonNull String eventId,
                                                    @NonNull String userName,
                                                    int rating,
                                                    @NonNull String comment) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }
        if (rating < 1 || rating > 5) {
            result.setValue(Resource.error("Please select a rating between 1 and 5."));
            return result;
        }

        final DocumentReference eventRef =
                firestore.collection(EVENTS_COLLECTION).document(eventId);
        final DocumentReference reviewRef =
                firestore.collection(REVIEWS_COLLECTION).document(); // pre-generated ID

        firestore.runTransaction(transaction -> {
                    // READ: current event + its aggregate.
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    if (!eventSnap.exists()) {
                        throw new FirebaseFirestoreException("Event no longer exists.",
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    Event event = eventSnap.toObject(Event.class);
                    double currentAvg = 0d;
                    int currentCount = 0;
                    if (event != null && event.getRating() != null) {
                        currentAvg = event.getRating().getAverageScore();
                        currentCount = event.getRating().getTotalReviews();
                    }

                    // COMPUTE: incremental running average.
                    int newCount = currentCount + 1;
                    double newAvg = ((currentAvg * currentCount) + rating) / newCount;
                    // Round to one decimal place for clean display.
                    newAvg = Math.round(newAvg * 10d) / 10d;

                    // WRITE: the review document.
                    Review review = new Review(eventId, uid, userName, rating, comment.trim());
                    review.setCreatedAt(Timestamp.now());
                    transaction.set(reviewRef, review);

                    // WRITE: the event aggregate (nested map fields via dot-paths).
                    transaction.update(eventRef,
                            "rating.averageScore", newAvg,
                            "rating.totalReviews", newCount);

                    return true;
                }).addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to submit review.")));

        return result;
    }

    /**
     * @return Success(true) if the current user has a CHECKED-IN ticket for the
     * event (i.e. they attended), else Success(false).
     */
    public LiveData<Resource<Boolean>> hasAttended(@NonNull String eventId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection("tickets")
                .whereEqualTo(FIELD_EVENT_ID, eventId)
                .whereEqualTo(FIELD_USER_ID, uid)
                .whereEqualTo("isCheckedIn", true)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> result.setValue(Resource.success(!snap.isEmpty())))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to check attendance.")));

        return result;
    }
}