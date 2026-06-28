package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Discussion;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * Owns the flat discussion comments for a movie. Reads are a live snapshot
 * (comments appear in real time as others post); writes denormalize the author's
 * display name onto the comment so the list renders without per-row user lookups.
 * The "movie not ENDED" gate is enforced by security rules, not here — this
 * repository surfaces the rule's rejection as an error Resource.
 */
public class DiscussionRepository {

    private static final String DISCUSSIONS_COLLECTION = "discussions";
    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_MOVIE_ID = "movieId";
    private static final String FIELD_CREATED_AT = "createdAt";

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public DiscussionRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    /**
     * Subscribes to a movie's comments, oldest-first (chronological). The caller
     * owns the returned registration and MUST remove it (e.g. in onCleared) to
     * stop the background listener.
     *
     * @param movieId the movie whose discussion to stream.
     * @param target  LiveData to push results into.
     * @return the listener registration, for lifecycle cleanup.
     */
    @NonNull
    public ListenerRegistration listenToComments(
            @NonNull String movieId,
            @NonNull MutableLiveData<Resource<List<Discussion>>> target) {

        target.setValue(Resource.loading());

        Query query = firestore.collection(DISCUSSIONS_COLLECTION)
                .whereEqualTo(FIELD_MOVIE_ID, movieId)
                .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING);

        return query.addSnapshotListener((snap, error) -> {
            if (error != null) {
                target.setValue(Resource.error(
                        error.getMessage() != null ? error.getMessage()
                                : "Failed to load discussion."));
                return;
            }
            if (snap == null) {
                target.setValue(Resource.success(new java.util.ArrayList<>()));
                return;
            }
            List<Discussion> comments = snap.toObjects(Discussion.class);
            target.setValue(Resource.success(comments));
        });
    }

    /**
     * Posts a comment. Looks up the user's display name to denormalize, then
     * writes the discussion doc. The status gate (movie not ENDED) is enforced
     * by security rules; a rejection surfaces here as an error Resource.
     *
     * @return LiveData emitting Loading then Success(discussionId) or Error.
     */
    public LiveData<Resource<String>> postComment(@NonNull String movieId,
                                                  @NonNull String comment) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }
        final String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            result.setValue(Resource.error("Comment can't be empty."));
            return result;
        }

        // Look up the author's display name to denormalize onto the comment.
        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(userSnap -> {
                    String userName = userSnap.getString("name");
                    if (userName == null || userName.trim().isEmpty()) {
                        userName = "User";
                    }

                    Discussion discussion = new Discussion(movieId, uid, userName, trimmed);
                    DocumentReference ref =
                            firestore.collection(DISCUSSIONS_COLLECTION).document();

                    ref.set(discussion)
                            .addOnSuccessListener(unused ->
                                    result.setValue(Resource.success(ref.getId())))
                            .addOnFailureListener(e -> result.setValue(Resource.error(
                                    e.getMessage() != null ? e.getMessage()
                                            : "Couldn't post comment.")));
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage()
                                : "Couldn't load your profile.")));

        return result;
    }
}