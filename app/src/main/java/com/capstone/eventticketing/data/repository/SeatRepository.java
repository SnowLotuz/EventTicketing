package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.model.SeatStatus;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Owns all access to the {@code events/{eventId}/seats} subcollection: the live
 * seat-map stream and the transactional hold / release / book operations that
 * enforce single-occupancy under concurrency.
 *
 * <p>Correctness model: the SnapshotListener drives UI rendering only. The
 * authoritative AVAILABLE/HELD/BOOKED decision is made inside Firestore
 * Transactions, which re-run on contention so two users can never hold the same
 * seat. Holds carry an expiry so abandoned selections self-heal with no server.
 */
public class SeatRepository {

    private static final String EVENTS_COLLECTION = "events";
    private static final String SEATS_SUBCOLLECTION = "seats";
    private static final String FIELD_ROW = "row";
    private static final String FIELD_COLUMN = "column";

    /** How long a seat stays held after selection — matches the checkout timer. */
    public static final long HOLD_DURATION_MS = 10 * 60 * 1000L; // 10 minutes

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public SeatRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    private DocumentReference seatRef(@NonNull String eventId, @NonNull String seatId) {
        return firestore.collection(EVENTS_COLLECTION).document(eventId)
                .collection(SEATS_SUBCOLLECTION).document(seatId);
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    /**
     * Subscribes to live seat-map updates ordered by row then column. The returned
     * {@link ListenerRegistration} MUST be removed by the caller (ViewModel) when
     * the screen is torn down to avoid leaks and background reads.
     *
     * @param seatsLiveData target LiveData the listener pushes seat lists into.
     * @return the registration handle for later removal.
     */
    public ListenerRegistration listenToSeats(@NonNull String eventId,
                                              @NonNull MutableLiveData<Resource<List<Seat>>> seatsLiveData) {
        seatsLiveData.setValue(Resource.loading());

        Query query = firestore.collection(EVENTS_COLLECTION).document(eventId)
                .collection(SEATS_SUBCOLLECTION)
                .orderBy(FIELD_ROW)
                .orderBy(FIELD_COLUMN);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                seatsLiveData.setValue(Resource.error(
                        error.getMessage() != null ? error.getMessage() : "Failed to load seats."));
                return;
            }
            if (snapshot == null) {
                seatsLiveData.setValue(Resource.success(new ArrayList<>()));
                return;
            }
            List<Seat> seats = snapshot.toObjects(Seat.class);
            seatsLiveData.setValue(Resource.success(seats));
        });
    }

    /**
     * Atomically holds a seat for the current user. Succeeds only if the seat is
     * AVAILABLE, already held by this user, or held by another whose hold expired.
     * Runs in a transaction so concurrent attempts on the same seat cannot both win.
     *
     * @return LiveData emitting Loading then Success(the held Seat) or Error.
     */
    public LiveData<Resource<Seat>> holdSeat(@NonNull String eventId, @NonNull String seatId) {
        MutableLiveData<Resource<Seat>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        final DocumentReference ref = seatRef(eventId, seatId);

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(ref);
                    if (!snap.exists()) {
                        throw new FirebaseFirestoreException("Seat no longer exists.",
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    Seat seat = snap.toObject(Seat.class);
                    if (seat == null) {
                        throw new FirebaseFirestoreException("Seat could not be read.",
                                FirebaseFirestoreException.Code.ABORTED);
                    }

                    String status = seat.getStatus();
                    boolean available = SeatStatus.AVAILABLE.equals(status);
                    boolean ownHold = SeatStatus.HELD.equals(status)
                            && uid.equals(seat.getHeldBy());
                    boolean lapsedHold = SeatStatus.HELD.equals(status)
                            && seat.getHeldUntil() != null
                            && seat.getHeldUntil().toDate().getTime() <= System.currentTimeMillis();

                    if (!(available || ownHold || lapsedHold)) {
                        // Booked, or actively held by someone else — refuse.
                        throw new FirebaseFirestoreException("Seat is no longer available.",
                                FirebaseFirestoreException.Code.ABORTED);
                    }

                    Timestamp expiry = new Timestamp(new Date(System.currentTimeMillis() + HOLD_DURATION_MS));
                    transaction.update(ref,
                            "status", SeatStatus.HELD,
                            "heldBy", uid,
                            "heldUntil", expiry,
                            "bookingId", null);

                    // Reflect the new state in the returned object for the caller.
                    seat.setStatus(SeatStatus.HELD);
                    seat.setHeldBy(uid);
                    seat.setHeldUntil(expiry);
                    seat.setBookingId(null);
                    return seat;
                }).addOnSuccessListener(seat -> result.setValue(Resource.success(seat)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Could not hold seat.")));

        return result;
    }

    /**
     * Releases a hold the current user owns, returning the seat to AVAILABLE.
     * Used when a user deselects a seat or the checkout timer expires. No-op
     * (treated as success) if the seat is not currently held by this user, so
     * releasing an already-reclaimed seat never errors.
     *
     * @return LiveData emitting Loading then Success(true) or Error.
     */
    public LiveData<Resource<Boolean>> releaseSeat(@NonNull String eventId, @NonNull String seatId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        final DocumentReference ref = seatRef(eventId, seatId);

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(ref);
                    if (!snap.exists()) return true; // already gone; nothing to release
                    Seat seat = snap.toObject(Seat.class);
                    if (seat == null) return true;

                    boolean ownHold = SeatStatus.HELD.equals(seat.getStatus())
                            && uid.equals(seat.getHeldBy());
                    if (!ownHold) {
                        // Not ours to release (expired/reclaimed/booked) — leave it untouched.
                        return true;
                    }

                    transaction.update(ref,
                            "status", SeatStatus.AVAILABLE,
                            "heldBy", null,
                            "heldUntil", null,
                            "bookingId", null);
                    return true;
                }).addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Could not release seat.")));

        return result;
    }
}