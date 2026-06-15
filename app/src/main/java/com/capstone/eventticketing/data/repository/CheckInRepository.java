package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.CheckInResult;
import com.capstone.eventticketing.data.model.Ticket;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Owns the admin check-in operation. Validation runs in a transaction so two
 * simultaneous scans of the same ticket cannot both succeed: exactly one sees an
 * un-used ticket and commits; the other re-runs and reports "already used".
 */
public class CheckInRepository {

    private static final String TICKETS_COLLECTION = "tickets";

    @NonNull private final FirebaseFirestore firestore;

    public CheckInRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Validates and checks in a scanned ticket for a specific event.
     *
     * @param eventId      the event the scanner is operating for.
     * @param scannedData  raw QR payload, expected as {@code bookingId|ticketId}.
     * @return LiveData emitting Loading then Success({@link CheckInResult}). Note:
     * an invalid/used ticket is still a "successful" operation that
     * returns an INVALID/ALREADY_USED result — only true errors (network)
     * surface as Resource.error.
     */
    public LiveData<Resource<CheckInResult>> checkIn(@NonNull String eventId,
                                                     @NonNull String scannedData) {
        MutableLiveData<Resource<CheckInResult>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        // Parse "bookingId|ticketId".
        String ticketId = parseTicketId(scannedData);
        if (ticketId == null) {
            result.setValue(Resource.success(
                    CheckInResult.invalid("Unrecognized code format.")));
            return result;
        }

        final DocumentReference ticketRef =
                firestore.collection(TICKETS_COLLECTION).document(ticketId);

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(ticketRef);

                    if (!snap.exists()) {
                        return CheckInResult.invalid("Ticket not found.");
                    }
                    Ticket ticket = snap.toObject(Ticket.class);
                    if (ticket == null) {
                        return CheckInResult.invalid("Ticket could not be read.");
                    }

                    // Scope check: the ticket must belong to THIS event.
                    if (ticket.getEventId() == null || !ticket.getEventId().equals(eventId)) {
                        return CheckInResult.invalid("Ticket is for a different event.");
                    }

                    // Already used → do not write; report when it was first used.
                    if (ticket.isCheckedIn()) {
                        return CheckInResult.alreadyUsed(ticket.getSeatNumber(), ticket.getCheckInTime());
                    }

                    // Valid → flip to checked-in atomically.
                    Timestamp now = Timestamp.now();
                    transaction.update(ticketRef,
                            "isCheckedIn", true,
                            "checkInTime", now);

                    return CheckInResult.valid(ticket.getSeatNumber(), now);

                }).addOnSuccessListener(checkInResult -> result.setValue(Resource.success(checkInResult)))
                .addOnFailureListener(e -> result.setValue(Resource.error(
                        e.getMessage() != null ? e.getMessage() : "Check-in failed. Try again.")));

        return result;
    }

    /** Extracts the ticketId from a {@code bookingId|ticketId} payload. */
    private String parseTicketId(@NonNull String scannedData) {
        String[] parts = scannedData.split("\\|");
        if (parts.length != 2) return null;
        String ticketId = parts[1].trim();
        return ticketId.isEmpty() ? null : ticketId;
    }
}