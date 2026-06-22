package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Ticket;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns access to the current user's {@code tickets}. Uses a SnapshotListener so
 * the wallet updates live (e.g. when a ticket is checked in) and resolves from
 * the local cache when offline. Each emitted ticket is paired with its movie
 * title, resolved via batched {@code whereIn} lookups against {@code movies}.
 * The returned registration must be removed by the caller on teardown.
 */
public class TicketRepository {

    private static final String TICKETS_COLLECTION = "tickets";
    private static final String MOVIES_COLLECTION = "movies";
    private static final String FIELD_USER_ID = "userId";
    private static final int WHERE_IN_LIMIT = 30;

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    /** eventId -> movie title, cached across snapshot emissions to avoid refetching. */
    @NonNull private final Map<String, String> titleCache = new HashMap<>();

    public TicketRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    /** A ticket paired with its resolved movie title, for display. */
    public static class TicketWithMovie {
        @NonNull public final Ticket ticket;
        @NonNull public final String movieTitle;
        public TicketWithMovie(@NonNull Ticket ticket, @NonNull String movieTitle) {
            this.ticket = ticket;
            this.movieTitle = movieTitle;
        }
    }

    /** Wraps the resolved ticket list with whether it was served from the offline cache. */
    public static class TicketResult {
        @NonNull public final List<TicketWithMovie> tickets;
        public final boolean isFromCache;
        public TicketResult(@NonNull List<TicketWithMovie> tickets, boolean isFromCache) {
            this.tickets = tickets;
            this.isFromCache = isFromCache;
        }
    }

    /**
     * Streams the current user's tickets (with resolved movie titles) into {@code target}.
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
                target.setValue(Resource.success(new TicketResult(new ArrayList<>(), false)));
                return;
            }

            List<Ticket> tickets = snapshot.toObjects(Ticket.class);
            boolean fromCache = snapshot.getMetadata().isFromCache();

            if (tickets.isEmpty()) {
                target.setValue(Resource.success(new TicketResult(new ArrayList<>(), fromCache)));
                return;
            }
            resolveTitlesThenEmit(tickets, fromCache, target);
        });
    }

    /**
     * Resolves any not-yet-cached movie titles for the tickets' distinct event IDs,
     * then assembles and emits the paired list. Titles already in {@link #titleCache}
     * are reused, so steady-state snapshot ticks issue no extra reads.
     */
    private void resolveTitlesThenEmit(
            @NonNull List<Ticket> tickets,
            boolean fromCache,
            @NonNull MutableLiveData<Resource<TicketResult>> target) {

        // Distinct event IDs not already cached.
        Set<String> needed = new HashSet<>();
        for (Ticket t : tickets) {
            if (t.getEventId() != null && !titleCache.containsKey(t.getEventId())) {
                needed.add(t.getEventId());
            }
        }

        // Nothing new to fetch — assemble straight from cache.
        if (needed.isEmpty()) {
            emitPaired(tickets, fromCache, target);
            return;
        }

        List<String> ids = new ArrayList<>(needed);
        List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += WHERE_IN_LIMIT) {
            int end = Math.min(start + WHERE_IN_LIMIT, ids.size());
            tasks.add(firestore.collection(MOVIES_COLLECTION)
                    .whereIn(FieldPath.documentId(), ids.subList(start, end))
                    .get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    for (Object o : results) {
                        QuerySnapshot snap = (QuerySnapshot) o;
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String title = doc.getString("title");
                            titleCache.put(doc.getId(), title != null ? title : "Movie");
                        }
                    }
                    emitPaired(tickets, fromCache, target);
                })
                .addOnFailureListener(e ->
                        // Title fetch failed (e.g. offline with uncached movies) —
                        // still show tickets, just without titles rather than erroring out.
                        emitPaired(tickets, fromCache, target));
    }

    /** Pairs each ticket with its (possibly cached) title and emits success. */
    private void emitPaired(
            @NonNull List<Ticket> tickets,
            boolean fromCache,
            @NonNull MutableLiveData<Resource<TicketResult>> target) {

        List<TicketWithMovie> out = new ArrayList<>();
        for (Ticket t : tickets) {
            String title = (t.getEventId() != null && titleCache.containsKey(t.getEventId()))
                    ? titleCache.get(t.getEventId())
                    : "Movie no longer available";
            out.add(new TicketWithMovie(t, title));
        }
        target.setValue(Resource.success(new TicketResult(out, fromCache)));
    }
}