package com.capstone.eventticketing.ui.seat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.model.SeatStatus;
import com.capstone.eventticketing.data.repository.SeatRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs {@link SeatSelectionActivity}. Owns the live seat-map subscription and
 * orchestrates transactional hold/release via {@link SeatRepository}. Tracks the
 * seats this user currently holds so the UI can show selection state and a live
 * total without re-deriving them from the stream.
 */
public class SeatSelectionViewModel extends ViewModel {

    @NonNull private final SeatRepository seatRepository;
    @NonNull private final String eventId;

    private final MutableLiveData<Resource<List<Seat>>> seats = new MutableLiveData<>();

    /** seatId -> Seat, in selection order. The user's current holds. */
    private final Map<String, Seat> selectedSeats = new LinkedHashMap<>();
    private final MutableLiveData<List<Seat>> selectedSeatsLive = new MutableLiveData<>(new ArrayList<>());

    /** Transient one-shot error messages (e.g. "Seat is no longer available."). */
    private final MutableLiveData<String> actionError = new MutableLiveData<>();

    @Nullable private ListenerRegistration seatListener;

    /** The movie's blockbuster flag, loaded once for same-price discount eligibility. */
    private boolean isBlockbuster = false;

    public SeatSelectionViewModel(@NonNull SeatRepository seatRepository, @NonNull String eventId) {
        this.seatRepository = seatRepository;
        this.eventId = eventId;
        startListening();
        loadMovieFlags();
    }

    public LiveData<Resource<List<Seat>>> getSeats() { return seats; }
    public LiveData<List<Seat>> getSelectedSeats() { return selectedSeatsLive; }
    public LiveData<String> getActionError() { return actionError; }

    private void startListening() {
        seatListener = seatRepository.listenToSeats(eventId, seats);
    }

    /** Reads the movie's blockbuster flag once; defaults to false on any failure. */
    private void loadMovieFlags() {
        FirebaseFirestore.getInstance()
                .collection("movies")
                .document(eventId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists()) {
                        isBlockbuster = Boolean.TRUE.equals(snap.getBoolean("blockbuster"));
                    }
                });
        // On failure we leave isBlockbuster=false; the transaction re-reads the
        // authoritative flag anyway, so the charge is correct regardless.
    }

    /** @return current selected seat IDs, for the custom view's accent rendering. */
    @NonNull
    public List<String> getSelectedSeatIds() {
        return new ArrayList<>(selectedSeats.keySet());
    }

    /** @return running total of held seats' prices. */
    public double getSelectedTotal() {
        double total = 0d;
        for (Seat s : selectedSeats.values()) total += s.getPrice();
        return total;
    }

    /**
     * Toggles a seat: if the user already holds it, release it; otherwise attempt
     * to hold it. Booked or actively-held-by-others seats are ignored at the UI
     * layer (the transaction is still the authority).
     */
    public void onSeatTapped(@NonNull Seat seat) {
        String seatId = seat.getSeatId();
        if (seatId == null) return;

        if (selectedSeats.containsKey(seatId)) {
            release(seatId);
        } else {
            // Ignore taps on seats we can't take; the transaction re-verifies anyway.
            if (!seat.isSelectableBy(currentUidOrNull())) {
                return;
            }
            hold(seatId, seat);
        }
    }

    private void hold(@NonNull String seatId, @NonNull Seat tappedSeat) {
        LiveData<Resource<Seat>> source = seatRepository.holdSeat(eventId, seatId);
        source.observeForever(new Observer<Resource<Seat>>() {
            @Override
            public void onChanged(Resource<Seat> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                    selectedSeats.put(seatId, resource.data);
                    publishSelection();
                } else {
                    actionError.setValue(resource.message != null
                            ? resource.message : "Could not select seat.");
                }
            }
        });
    }

    private void release(@NonNull String seatId) {
        // Optimistically drop from selection so the UI feels instant.
        Seat removed = selectedSeats.remove(seatId);
        publishSelection();

        LiveData<Resource<Boolean>> source = seatRepository.releaseSeat(eventId, seatId);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                if (resource.status == Resource.Status.ERROR) {
                    // Roll back the optimistic removal on failure.
                    if (removed != null) {
                        selectedSeats.put(seatId, removed);
                        publishSelection();
                    }
                    actionError.setValue(resource.message != null
                            ? resource.message : "Could not release seat.");
                }
            }
        });
    }

    /** Releases every seat the user currently holds — used on back/exit. */
    public void releaseAllHolds() {
        for (String seatId : new ArrayList<>(selectedSeats.keySet())) {
            seatRepository.releaseSeat(eventId, seatId); // fire-and-forget on teardown
        }
        selectedSeats.clear();
        publishSelection();
    }

    private void publishSelection() {
        selectedSeatsLive.setValue(new ArrayList<>(selectedSeats.values()));
    }

    @Nullable
    private String currentUidOrNull() {
        return com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Critical: stop the live subscription so it doesn't read in the background.
        if (seatListener != null) {
            seatListener.remove();
            seatListener = null;
        }
    }

    /** Injects the repository and eventId so the View stays free of business logic. */
    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String eventId;

        public Factory(@NonNull String eventId) {
            this.eventId = eventId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(SeatSelectionViewModel.class)) {
                return (T) new SeatSelectionViewModel(new SeatRepository(), eventId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }

    /**
     * @return the earliest heldUntil across the user's current holds, in epoch
     * millis, or 0 if nothing is held. Drives the checkout countdown so
     * it reflects the real remaining hold window.
     */
    public long getEarliestHoldExpiryMillis() {
        long earliest = Long.MAX_VALUE;
        for (Seat s : selectedSeats.values()) {
            if (s.getHeldUntil() != null) {
                long t = s.getHeldUntil().toDate().getTime();
                if (t < earliest) earliest = t;
            }
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    /** @return the per-seat base price (flat cinema pricing), or 0 if nothing selected. */
    public double getBasePrice() {
        for (Seat s : selectedSeats.values()) {
            return s.getPrice(); // all seats share one price; first is representative
        }
        return 0d;
    }

    /** @return whether the movie is a blockbuster (same-price discount eligibility). */
    public boolean isBlockbuster() {
        return isBlockbuster;
    }

    /**
     * @return count of currently BOOKED seats, derived from the live snapshot, for
     * the same-price discount's ≤30 eligibility gate. Returns 0 if no snapshot yet.
     */
    public int getBookedCount() {
        Resource<List<Seat>> r = seats.getValue();
        if (r == null || r.data == null) return 0;
        int count = 0;
        for (Seat s : r.data) {
            if (SeatStatus.BOOKED.equals(s.getStatus())) count++;
        }
        return count;
    }
}