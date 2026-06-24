package com.capstone.eventticketing.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for {@code movies} data, the seats subcollection, and
 * wishlist mutations. Behaviorally identical to the previous EventRepository —
 * only the collection name ("events" → "movies") and domain type changed. The
 * {@code seats} subcollection name is unchanged.
 */
public class MovieRepository {

    private static final String MOVIES_COLLECTION = "movies";
    private static final String SEATS_SUBCOLLECTION = "seats";
    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_GENRE = "genre";
    private static final String FIELD_RELEASE_DATE = "releaseDate";
    private static final String FIELD_WISHLIST = "wishlistMovieIds";

    @NonNull private final FirebaseFirestore firestore;
    @NonNull private final FirebaseAuth firebaseAuth;

    public MovieRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    public LiveData<Resource<List<Movie>>> getMovies(String genre) {
        MutableLiveData<Resource<List<Movie>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        Query query = firestore.collection(MOVIES_COLLECTION)
                .orderBy(FIELD_RELEASE_DATE, Query.Direction.DESCENDING);

        if (genre != null && !genre.isEmpty() && !"All".equalsIgnoreCase(genre)) {
            query = query.whereEqualTo(FIELD_GENRE, genre);
        }

        query.get()
                .addOnSuccessListener(snapshot ->
                        result.setValue(Resource.success(snapshot.toObjects(Movie.class))))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load movies."))));

        return result;
    }

    public LiveData<Resource<Movie>> getMovieById(@NonNull String movieId) {
        MutableLiveData<Resource<Movie>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(MOVIES_COLLECTION).document(movieId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        result.setValue(Resource.error("Movie not found."));
                        return;
                    }
                    Movie movie = snapshot.toObject(Movie.class);
                    if (movie == null) {
                        result.setValue(Resource.error("Failed to parse movie."));
                    } else {
                        result.setValue(Resource.success(movie));
                    }
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load movie."))));

        return result;
    }

    public LiveData<Resource<String>> createMovie(@NonNull Movie movie) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String newId = firestore.collection(MOVIES_COLLECTION).document().getId();
        movie.setMovieId(newId);

        firestore.collection(MOVIES_COLLECTION).document(newId)
                .set(movie)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(newId)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to create movie."))));

        return result;
    }

    public LiveData<Resource<Boolean>> createSeatsForMovie(@NonNull String movieId,
                                                           @NonNull List<Seat> seats) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        if (seats.isEmpty()) {
            result.setValue(Resource.success(true));
            return result;
        }

        com.google.firebase.firestore.CollectionReference seatsRef =
                firestore.collection(MOVIES_COLLECTION).document(movieId).collection(SEATS_SUBCOLLECTION);

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

    public LiveData<Resource<Boolean>> toggleWishlist(@NonNull String movieId, boolean add) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        FieldValue update = add
                ? FieldValue.arrayUnion(movieId)
                : FieldValue.arrayRemove(movieId);

        firestore.collection(USERS_COLLECTION).document(uid)
                .update(FIELD_WISHLIST, update)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(add)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to update wishlist."))));

        return result;
    }

    public LiveData<Resource<Boolean>> updateMovie(@NonNull String movieId,
                                                   @NonNull Map<String, Object> updates) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(MOVIES_COLLECTION).document(movieId)
                .update(updates)
                .addOnSuccessListener(unused -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to update movie."))));

        return result;
    }

    public LiveData<Resource<Boolean>> deleteMovie(@NonNull String movieId) {
        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        com.google.firebase.firestore.DocumentReference movieRef =
                firestore.collection(MOVIES_COLLECTION).document(movieId);

        movieRef.collection(SEATS_SUBCOLLECTION).get()
                .addOnSuccessListener(seatSnap -> {
                    List<WriteBatch> batches = new ArrayList<>();
                    WriteBatch batch = firestore.batch();
                    batches.add(batch);
                    int opCount = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : seatSnap.getDocuments()) {
                        if (opCount == 499) {
                            batch = firestore.batch();
                            batches.add(batch);
                            opCount = 0;
                        }
                        batch.delete(doc.getReference());
                        opCount++;
                    }
                    batches.get(batches.size() - 1).delete(movieRef);

                    List<com.google.android.gms.tasks.Task<Void>> commits = new ArrayList<>();
                    for (WriteBatch b : batches) commits.add(b.commit());

                    com.google.android.gms.tasks.Tasks.whenAllComplete(commits)
                            .addOnSuccessListener(tasks -> {
                                for (com.google.android.gms.tasks.Task<?> t : tasks) {
                                    if (!t.isSuccessful()) {
                                        result.setValue(Resource.error("Failed to fully delete movie."));
                                        return;
                                    }
                                }
                                result.setValue(Resource.success(true));
                            })
                            .addOnFailureListener(e ->
                                    result.setValue(Resource.error(safeMessage(e, "Failed to delete movie."))));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to read seats for deletion."))));

        return result;
    }

    /**
     * Counts all movies for the admin KPI. Uses an aggregate count query so it
     * doesn't download every document.
     *
     * @return LiveData emitting Loading then Success(count) or Error.
     */
    public LiveData<Resource<Integer>> getMovieCount() {
        MutableLiveData<Resource<Integer>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(MOVIES_COLLECTION)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot ->
                        result.setValue(Resource.success((int) snapshot.getCount())))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to count movies."))));

        return result;
    }

    private String currentUid() {
        return firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    private static final int WHERE_IN_LIMIT = 30;

    /**
     * Resolves the current user's wishlisted movie IDs into full {@link Movie}
     * objects for the wishlist screen. Reads the user's wishlist array, then
     * batch-fetches the referenced movies via {@code whereIn} (chunked to respect
     * Firestore's limit). Returns an empty list if the wishlist is empty.
     *
     * @return LiveData emitting Loading then Success(list) or Error.
     */
    public LiveData<Resource<List<Movie>>> getWishlistMovies() {
        MutableLiveData<Resource<List<Movie>>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        String uid = currentUid();
        if (uid == null) {
            result.setValue(Resource.error("Not signed in."));
            return result;
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    @SuppressWarnings("unchecked")
                    List<String> ids = (List<String>) userDoc.get(FIELD_WISHLIST);
                    if (ids == null || ids.isEmpty()) {
                        result.setValue(Resource.success(new ArrayList<>()));
                        return;
                    }
                    resolveWishlistMovies(ids, result);
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load wishlist."))));

        return result;
    }

    /**
     * Counts BOOKED seats in a movie's {@code seats} subcollection — i.e. tickets
     * sold. Uses an aggregate count query so it never downloads the 150 seat docs.
     * Reused by the tickets-sold display and the same-price discount eligibility
     * check (≤ 30 booked qualifies).
     *
     * @return LiveData emitting Loading then Success(count) or Error.
     */
    public LiveData<Resource<Integer>> getBookedSeatCount(@NonNull String movieId) {
        MutableLiveData<Resource<Integer>> result = new MutableLiveData<>();
        result.setValue(Resource.loading());

        firestore.collection(MOVIES_COLLECTION)
                .document(movieId)
                .collection(SEATS_SUBCOLLECTION)
                .whereEqualTo("status", com.capstone.eventticketing.data.model.SeatStatus.BOOKED)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot ->
                        result.setValue(Resource.success((int) snapshot.getCount())))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to count booked seats."))));

        return result;
    }

    /** Batch-fetches movies for the given IDs and emits them, preserving wishlist order. */
    private void resolveWishlistMovies(@NonNull List<String> ids,
                                       @NonNull MutableLiveData<Resource<List<Movie>>> result) {
        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks =
                new ArrayList<>();
        for (int start = 0; start < ids.size(); start += WHERE_IN_LIMIT) {
            int end = Math.min(start + WHERE_IN_LIMIT, ids.size());
            tasks.add(firestore.collection(MOVIES_COLLECTION)
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(),
                            ids.subList(start, end))
                    .get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(snapshots -> {
                    // Map id -> Movie so we can re-order to match the wishlist array.
                    java.util.Map<String, Movie> byId = new java.util.HashMap<>();
                    for (Object snapObj : snapshots) {
                        com.google.firebase.firestore.QuerySnapshot snap =
                                (com.google.firebase.firestore.QuerySnapshot) snapObj;
                        for (Movie m : snap.toObjects(Movie.class)) {
                            if (m.getMovieId() != null) byId.put(m.getMovieId(), m);
                        }
                    }
                    // Preserve the user's wishlist order; skip any movie since deleted.
                    List<Movie> ordered = new ArrayList<>();
                    for (String id : ids) {
                        Movie m = byId.get(id);
                        if (m != null) ordered.add(m);
                    }
                    result.setValue(Resource.success(ordered));
                })
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(safeMessage(e, "Failed to load wishlist movies."))));
    }

    private String safeMessage(@NonNull Exception e, @NonNull String fallback) {
        return e.getMessage() != null ? e.getMessage() : fallback;
    }
}