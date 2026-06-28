package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Booking;
import com.capstone.eventticketing.data.model.Promotion;
import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.model.SeatStatus;
import com.capstone.eventticketing.util.PriceTierCalculator;
import com.capstone.eventticketing.util.PromoValidator;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns booking creation and promo lookup. The booking operation runs in a single
 * Firestore Transaction that atomically: re-verifies the user's seat holds,
 * re-validates and increments the promo, flips seats to BOOKED, and creates the
 * Booking and per-seat Ticket documents. Any failure aborts the whole operation.
 */
public class BookingRepository {

    private static final String EVENTS_COLLECTION = "movies";
    private static final String SEATS_SUBCOLLECTION = "seats";
    private static final String BOOKINGS_COLLECTION = "bookings";
    private static final String TICKETS_COLLECTION = "tickets";
    private static final String PROMOTIONS_COLLECTION = "promotions";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_BOOKING_DATE = "bookingDate";
    private static final int WHERE_IN_LIMIT = 30;
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_FINAL_AMOUNT = "finalAmount";

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public BookingRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    /**
     * Looks up a promo by its code (case-sensitive match on the stored value).
     *
     * @return LiveData emitting Success(Promotion) or Success(null) if not found, or Error.
     */
    public LiveData<Resource<Promotion>> findPromoByCode(@NonNull String code) {
        MutableLiveData<Resource<Promotion>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(PROMOTIONS_COLLECTION)
                .whereEqualTo(FIELD_CODE, code)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        result.setValue(Resource.success(null));
                    } else {
                        result.setValue(Resource.success(
                                snap.getDocuments().get(0).toObject(Promotion.class)));
                    }
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to look up promo.")));

        return result;
    }

    /**
     * Atomically completes a booking for the given seats.
     *
     * @param eventId     the event being booked.
     * @param seatIds     the seat IDs the user currently holds.
     * @param promoId     the validated promo's document ID, or null if none applied.
     * @param bookedCount the total number of currently booked seats (for tier evaluation).
     * @return LiveData emitting Loading then Success(bookingId) or Error.
     */
    public LiveData<Resource<String>> createBooking(@NonNull String eventId,
                                                    @NonNull List<String> seatIds,
                                                    String promoId,
                                                    int bookedCount) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }
        if (seatIds.isEmpty()) {
            result.setValue(Resource.error("No seats selected."));
            return result;
        }

        final DocumentReference eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId);
        final DocumentReference bookingRef = firestore.collection(BOOKINGS_COLLECTION).document();
        final DocumentReference promoRef = (promoId != null)
                ? firestore.collection(PROMOTIONS_COLLECTION).document(promoId)
                : null;

        firestore.runTransaction(transaction -> {
                    // ---------- PHASE 1: READS (all reads must come before any writes) ----------
                    List<Seat> heldSeats = new ArrayList<>();
                    List<DocumentReference> seatRefs = new ArrayList<>();
                    for (String seatId : seatIds) {
                        DocumentReference sRef = eventRef.collection(SEATS_SUBCOLLECTION).document(seatId);
                        DocumentSnapshot sSnap = transaction.get(sRef);
                        if (!sSnap.exists()) {
                            throw new FirebaseFirestoreException("A selected seat no longer exists.",
                                    FirebaseFirestoreException.Code.ABORTED);
                        }
                        Seat seat = sSnap.toObject(Seat.class);
                        if (seat == null) {
                            throw new FirebaseFirestoreException("A seat could not be read.",
                                    FirebaseFirestoreException.Code.ABORTED);
                        }
                        // Re-verify THIS user still holds the seat (hold may have expired/stolen).
                        boolean stillOurs = SeatStatus.HELD.equals(seat.getStatus())
                                && uid.equals(seat.getHeldBy());
                        if (!stillOurs) {
                            throw new FirebaseFirestoreException(
                                    "Your hold on seat " + seatId + " expired. Please reselect.",
                                    FirebaseFirestoreException.Code.ABORTED);
                        }
                        heldSeats.add(seat);
                        seatRefs.add(sRef);
                    }

                    Promotion promo = null;
                    if (promoRef != null) {
                        DocumentSnapshot pSnap = transaction.get(promoRef);
                        promo = pSnap.exists() ? pSnap.toObject(Promotion.class) : null;
                    }

                    // Read the movie for the blockbuster flag (same-price discount eligibility).
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    boolean isBlockbuster = Boolean.TRUE.equals(eventSnap.getBoolean("blockbuster"));

                    // ---------- PHASE 2: VALIDATE / COMPUTE (in memory) ----------
                    double subTotal = 0d;
                    for (Seat s : heldSeats) subTotal += s.getPrice();

                    // Per-seat base price (flat cinema pricing: all seats share one price).
                    double basePrice = heldSeats.isEmpty() ? 0d : heldSeats.get(0).getPrice();

                    // Same-price discount — evaluated at the point of truth, from the seat price,
                    // the passed booked count, and the movie's blockbuster flag.
                    PriceTierCalculator.Result tier =
                            PriceTierCalculator.evaluate(basePrice, bookedCount, isBlockbuster);

                    double discountAmount = 0d;
                    double finalAmount = subTotal;
                    String appliedCode = null;

                    if (tier.discounted) {
                        // Same-price discount owns the price; promos are mutually exclusive.
                        if (promoRef != null) {
                            throw new FirebaseFirestoreException(
                                    "Promo codes can't be combined with this discount.",
                                    FirebaseFirestoreException.Code.ABORTED);
                        }
                        double discountedPerSeat = tier.finalPrice;
                        finalAmount = discountedPerSeat * heldSeats.size();
                        discountAmount = subTotal - finalAmount;
                    } else if (promoRef != null) {
                        PromoValidator.Result vr = PromoValidator.validate(promo, subTotal);
                        if (!vr.valid) {
                            throw new FirebaseFirestoreException(
                                    vr.errorMessage != null ? vr.errorMessage : "Promo is no longer valid.",
                                    FirebaseFirestoreException.Code.ABORTED);
                        }
                        discountAmount = vr.discountAmount;
                        finalAmount = vr.finalAmount;
                        appliedCode = promo.getCode();
                    }

                    // ---------- PHASE 3: WRITES ----------
                    // Increment promo usage (rule-checked: only +1 on timesUsed).
                    if (promoRef != null && promo != null) {
                        transaction.update(promoRef, "timesUsed", promo.getTimesUsed() + 1);
                    }

                    // Create the booking document.
                    Booking booking = new Booking(uid, eventId, subTotal, appliedCode,
                            discountAmount, finalAmount);
                    transaction.set(bookingRef, booking);
                    String bookingId = bookingRef.getId();

                    // Flip each seat to BOOKED and create its Ticket.
                    for (int i = 0; i < seatRefs.size(); i++) {
                        DocumentReference sRef = seatRefs.get(i);
                        Seat seat = heldSeats.get(i);

                        transaction.update(sRef,
                                "status", SeatStatus.BOOKED,
                                "heldUntil", null,
                                "bookingId", bookingId);

                        DocumentReference ticketRef = firestore.collection(TICKETS_COLLECTION).document();
                        String qrData = bookingId + "|" + ticketRef.getId();
                        com.capstone.eventticketing.data.model.Ticket ticket =
                                new com.capstone.eventticketing.data.model.Ticket(
                                        bookingId, eventId, uid, seat.getSeatId(), qrData);
                        transaction.set(ticketRef, ticket);
                    }

                    return bookingId;
                }).addOnSuccessListener(bookingId -> result.setValue(Resource.success(bookingId)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Booking failed. Please try again.")));

        return result;
    }

    // =========================================================================
    // PHẦN THÊM MỚI CHO STEP 15: BOOKING HISTORY
    // =========================================================================

    /** A booking paired with its resolved event title for display. */
    public static class BookingWithEvent {
        @NonNull public final Booking booking;
        @NonNull public final String eventTitle;
        public BookingWithEvent(@NonNull Booking booking, @NonNull String eventTitle) {
            this.booking = booking;
            this.eventTitle = eventTitle;
        }
    }

    /**
     * Fetches the current user's bookings (newest first) and resolves each
     * booking's event title via batched {@code whereIn} lookups, so the history
     * list can show meaningful names rather than raw event IDs.
     *
     * @return LiveData emitting Loading then Success(list) or Error.
     */
    public LiveData<Resource<List<BookingWithEvent>>> getMyBookings() {
        MutableLiveData<Resource<List<BookingWithEvent>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        final String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo(FIELD_USER_ID, uid)
                .orderBy(FIELD_BOOKING_DATE, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(bookingSnap -> {
                    List<Booking> bookings = bookingSnap.toObjects(Booking.class);
                    if (bookings.isEmpty()) {
                        result.setValue(Resource.success(new ArrayList<>()));
                        return;
                    }
                    resolveEventTitles(bookings, result);
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load bookings.")));

        return result;
    }

    /** Resolves event titles for the bookings' distinct event IDs, then assembles the result. */
    private void resolveEventTitles(@NonNull List<Booking> bookings,
                                    @NonNull MutableLiveData<Resource<List<BookingWithEvent>>> result) {
        // Distinct event IDs.
        Set<String> idSet = new HashSet<>();
        for (Booking b : bookings) {
            if (b.getEventId() != null) idSet.add(b.getEventId());
        }
        List<String> ids = new ArrayList<>(idSet);

        // Chunk into whereIn-sized batches.
        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks =
                new ArrayList<>();
        for (int start = 0; start < ids.size(); start += WHERE_IN_LIMIT) {
            int end = Math.min(start + WHERE_IN_LIMIT, ids.size());
            List<String> chunk = ids.subList(start, end);
            tasks.add(firestore.collection(EVENTS_COLLECTION)
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(snapshots -> {
                    // Map eventId -> title.
                    Map<String, String> titles = new LinkedHashMap<>();
                    for (Object snapObj : snapshots) {
                        com.google.firebase.firestore.QuerySnapshot snap =
                                (com.google.firebase.firestore.QuerySnapshot) snapObj;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                            String title = doc.getString("title");
                            titles.put(doc.getId(), title != null ? title : "Event");
                        }
                    }
                    // Assemble in original booking order.
                    List<BookingWithEvent> out = new ArrayList<>();
                    for (Booking b : bookings) {
                        String title = (b.getEventId() != null && titles.containsKey(b.getEventId()))
                                ? titles.get(b.getEventId())
                                : "Movie no longer available";
                        out.add(new BookingWithEvent(b, title));
                    }
                    result.setValue(Resource.success(out));
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load event details.")));
    }

    /**
     * Sums {@code finalAmount} across all CONFIRMED bookings for the admin revenue
     * KPI. Admin-only in practice (enforced by security rules on the read).
     *
     * @return LiveData emitting Loading then Success(total) or Error.
     */
    public LiveData<Resource<Double>> getTotalRevenue() {
        MutableLiveData<Resource<Double>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo(FIELD_STATUS, Booking.STATUS_CONFIRMED)
                .get()
                .addOnSuccessListener(snap -> {
                    double total = 0d;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        Double amount = doc.getDouble(FIELD_FINAL_AMOUNT);
                        if (amount != null) total += amount;
                    }
                    result.setValue(Resource.success(total));
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Failed to load revenue.")));

        return result;
    }
}