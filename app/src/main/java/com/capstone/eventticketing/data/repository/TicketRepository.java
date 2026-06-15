package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Ticket;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * Owns access to the current user's {@code Tickets}. Uses a SnapshotListener so
 * the wallet updates live (e.g. when a ticket is checked in) and resolves from
 * the local cache when offline. The returned registration must be removed by the
 * caller on teardown.
 */
public class TicketRepository {

    private static final String TICKETS_COLLECTION = "tickets";
    private static final String FIELD_USER_ID = "userId";

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public TicketRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    /** Wraps a ticket list with whether it was served from the offline cache. */
    public static class TicketResult {
        @NonNull public final List<Ticket> tickets;
        public final boolean isFromCache;
        public TicketResult(@NonNull List<Ticket> tickets, boolean isFromCache) {
            this.tickets = tickets;
            this.isFromCache = isFromCache;
        }
    }

    /**
     * Streams the current user's tickets into {@code target}.
     *
     * @return the listener registration; caller must remove it on teardown,
     * or null if no user is signed in.
     */
    public ListenerRegistration listenToMyTickets(
            @NonNull MutableLiveData<Resource<TicketResult>> target) {

        target.setValue(Resource.loading());

        String uid = firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            target.setValue(Resource.error("Not signed in."));
            return null;
        }

        Query query = firestore.collection(TICKETS_COLLECTION)
                .whereEqualTo(FIELD_USER_ID, uid);

        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                target.setValue(Resource.error(
                        error.getMessage() != null ? error.getMessage() : "Failed to load tickets."));
                return;
            }
            if (snapshot == null) {
                target.setValue(Resource.success(new TicketResult(new java.util.ArrayList<>(), false)));
                return;
            }
            List<Ticket> tickets = snapshot.toObjects(Ticket.class);
            boolean fromCache = snapshot.getMetadata().isFromCache();
            target.setValue(Resource.success(new TicketResult(tickets, fromCache)));
        });
    }
}