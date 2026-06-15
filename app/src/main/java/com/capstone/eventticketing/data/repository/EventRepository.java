package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentReference;
import com.capstone.eventticketing.data.model.Seat;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for {@code Events} data and wishlist mutations on the
 * current user's {@code Users} document. All Firestore access for discovery
 * lives here; ViewModels never query Firestore directly.
 */
public class EventRepository {

    private static final String EVENTS_COLLECTION = "events";
    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_EVENT_DATE = "eventDate";
    private static final String FIELD_WISHLIST = "wishlistEventIds";
    private static final String SEATS_SUBCOLLECTION = "seats";
    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public EventRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    /**
     * Fetches events ordered by date. When {@code category} is non-null and not
     * "All", results are filtered server-side.
     *
     * @return one-shot LiveData emitting Loading then Success/Error.
     */
    public LiveData<Resource<List<Event>>> getEvents(String category) {
        MutableLiveData<Resource<List<Event>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        Query query = firestore.collection(EVENTS_COLLECTION)
                .orderBy(FIELD_EVENT_DATE, Query.Direction.ASCENDING);

        if (category != null && !category.isEmpty() && !"All".equalsIgnoreCase(category)) {
            query = query.whereEqualTo(FIELD_CATEGORY, category);
        }

        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<Event> events = snapshot.toObjects(Event.class);
                    result.setValue(Resource.success(events));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load events."))));

        return result;
    }

    /** Fetches the current user's wishlisted event IDs to render heart states. */
    public LiveData<Resource<List<String>>> getWishlistIds() {
        MutableLiveData<Resource<List<String>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(doc -> {
                    @SuppressWarnings("unchecked")
                    List<String> ids = (List<String>) doc.get(FIELD_WISHLIST);
                    result.setValue(Resource.success(ids));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load wishlist."))));

        return result;
    }

    /**
     * Toggles an event in the current user's wishlist using atomic array
     * union/remove so concurrent updates from other devices don't clobber.
     *
     * @param add true to add, false to remove.
     * @return LiveData emitting the new membership state on success.
     */
    public LiveData<Resource<Boolean>> toggleWishlist(@NonNull String eventId, boolean add) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        FieldValue update = add
                ? FieldValue.arrayUnion(eventId)
                : FieldValue.arrayRemove(eventId);

        firestore.collection(USERS_COLLECTION).document(uid)
                .update(FIELD_WISHLIST, update)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(add)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to update wishlist."))));

        return result;
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : null;
    }

    private String safeMessage(@NonNull Exception e, @NonNull String fallback) {
        return e.getMessage() != null ? e.getMessage() : fallback;
    }
    /**
     * Fetches a single event document by ID.
     *
     * @return one-shot LiveData emitting Loading then Success (with the {@link Event})
     *         or Error. Emits Error if the document does not exist.
     */
    public LiveData<Resource<Event>> getEventById(@NonNull String eventId) {
        MutableLiveData<Resource<Event>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(EVENTS_COLLECTION).document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        result.setValue(Resource.error("Event not found."));
                        return;
                    }
                    Event event = snapshot.toObject(Event.class);
                    if (event == null) {
                        result.setValue(Resource.error("Failed to parse event."));
                    } else {
                        result.setValue(Resource.success(event));
                    }
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load event."))));

        return result;
    }

    /**
     * Checks whether a specific event is in the current user's wishlist.
     *
     * @return LiveData emitting Success(true/false) or Error.
     */
    public LiveData<Resource<Boolean>> isEventWishlisted(@NonNull String eventId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(doc -> {
                    @SuppressWarnings("unchecked")
                    List<String> ids = (List<String>) doc.get(FIELD_WISHLIST);
                    result.setValue(Resource.success(ids != null && ids.contains(eventId)));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load wishlist."))));

        return result;
    }
    /**
     * Persists a new event. Generates the document ID up-front and writes it
     * into the model so {@code eventId} is always populated client-side,
     * avoiding null-ID crashes downstream.
     *
     * @return LiveData emitting Loading then Success(eventId) or Error.
     */
    public LiveData<Resource<String>> createEvent(@NonNull Event event) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String newId = firestore.collection(EVENTS_COLLECTION).document().getId();
        event.setEventId(newId);

        firestore.collection(EVENTS_COLLECTION).document(newId)
                .set(event)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(newId)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to create event."))));

        return result;
    }
    /**
     * Seeds the {@code events/{eventId}/seats} subcollection with the supplied
     * seats. Uses WriteBatch(es) so each batch commits atomically. Firestore
     * limits a batch to 500 writes, so larger seat lists are chunked.
     *
     * @return LiveData emitting Loading then Success(true) once all batches commit, or Error.
     */
    public LiveData<Resource<Boolean>> createSeatsForEvent(@NonNull String eventId,
                                                           @NonNull List<Seat> seats) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        if (seats.isEmpty()) {
            result.setValue(Resource.success(true));
            return result;
        }

        com.google.firebase.firestore.CollectionReference seatsRef =
                firestore.collection(EVENTS_COLLECTION).document(eventId).collection(SEATS_SUBCOLLECTION);

        final int batchLimit = 500;
        List<com.google.android.gms.tasks.Task<Void>> commitTasks = new ArrayList<>();

        for (int start = 0; start < seats.size(); start += batchLimit) {
            int end = Math.min(start + batchLimit, seats.size());
            WriteBatch batch = firestore.batch();
            for (int i = start; i < end; i++) {
                Seat seat = seats.get(i);
                batch.set(seatsRef.document(seat.getSeatId()), seat);
            }
            commitTasks.add(batch.commit());
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(commitTasks)
                .addOnSuccessListener(tasks -> {
                    for (com.google.android.gms.tasks.Task<?> t : tasks) {
                        if (!t.isSuccessful()) {
                            result.setValue(Resource.error("Failed to create some seats."));
                            return;
                        }
                    }
                    result.setValue(Resource.success(true));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to create seats."))));

        return result;
    }

    /**
     * Updates mutable fields of an existing event. Does not touch the seats
     * subcollection (capacity changes would require seat regeneration, which is
     * intentionally out of scope here to avoid invalidating existing bookings).
     *
     * @return LiveData emitting Loading then Success(true) or Error.
     */
    public LiveData<Resource<Boolean>> updateEvent(@NonNull String eventId,
                                                   @NonNull Map<String, Object> updates) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(EVENTS_COLLECTION).document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to update event."))));

        return result;
    }

    /**
     * Deletes an event and its entire {@code seats} subcollection. Firestore does
     * not cascade subcollection deletes, so seats are fetched and removed in the
     * same batch(es) as the event document to avoid orphaned data.
     *
     * @return LiveData emitting Loading then Success(true) or Error.
     */
    public LiveData<Resource<Boolean>> deleteEvent(@NonNull String eventId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        DocumentReference eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId);

        // First read all seat docs, then batch-delete seats + event together.
        eventRef.collection(SEATS_SUBCOLLECTION).get()
                .addOnSuccessListener(seatSnap -> {
                    WriteBatch batch = firestore.batch();
                    int opCount = 0;
                    List<WriteBatch> batches = new ArrayList<>();
                    batches.add(batch);

                    for (com.google.firebase.firestore.DocumentSnapshot doc : seatSnap.getDocuments()) {
                        if (opCount == 499) { // leave room; start a fresh batch near the 500 cap
                            batch = firestore.batch();
                            batches.add(batch);
                            opCount = 0;
                        }
                        batch.delete(doc.getReference());
                        opCount++;
                    }
                    // Delete the event doc in the last batch.
                    batches.get(batches.size() - 1).delete(eventRef);

                    List<com.google.android.gms.tasks.Task<Void>> commits = new ArrayList<>();
                    for (WriteBatch b : batches) commits.add(b.commit());

                    com.google.android.gms.tasks.Tasks.whenAllComplete(commits)
                            .addOnSuccessListener(tasks -> {
                                for (com.google.android.gms.tasks.Task<?> t : tasks) {
                                    if (!t.isSuccessful()) {
                                        result.setValue(Resource.error("Failed to fully delete event."));
                                        return;
                                    }
                                }
                                result.setValue(Resource.success(true));
                            })
                            .addOnFailureListener(e ->
                                    result.setValue(Resource.error(safeMessage(e, "Failed to delete event."))));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to read seats for deletion."))));

        return result;
    }

}