package com.capstone.eventticketing.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

/**
 * Mirrors a document in the {@code events/{eventId}/seats} subcollection.
 * One document per physical seat — chosen so concurrent bookings of different
 * seats never contend on the same document, and a Firestore Transaction need
 * only lock the single seat being acquired.
 */
public class Seat {

    @DocumentId
    private String seatId;       // e.g. "A1"
    private String row;          // e.g. "A"
    private int column;          // e.g. 1
    private String tier;         // maps to Event.SeatMap.pricingTiers key
    private double price;        // denormalized from the tier price
    private String status;       // SeatStatus.AVAILABLE | HELD | BOOKED
    private String heldBy;       // userId currently holding the seat, or null
    private Timestamp heldUntil; // when the current hold expires, or null
    private String bookingId;    // set once BOOKED, else null

    /** Required empty constructor for Firestore deserialization. */
    public Seat() { }

    public Seat(String seatId, String row, int column, String tier, double price) {
        this.seatId = seatId;
        this.row = row;
        this.column = column;
        this.tier = tier;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
        this.bookingId = null;
    }

    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }

    public String getRow() { return row; }
    public void setRow(String row) { this.row = row; }

    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHeldBy() { return heldBy; }
    public void setHeldBy(String heldBy) { this.heldBy = heldBy; }

    public Timestamp getHeldUntil() { return heldUntil; }
    public void setHeldUntil(Timestamp heldUntil) { this.heldUntil = heldUntil; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    // --- Derived helpers (not persisted) ---

    @Exclude
    public boolean isBooked() {
        return SeatStatus.BOOKED.equals(status);
    }

    /**
     * @return true if this seat is held by someone else and the hold has not expired.
     * A held seat whose {@code heldUntil} is in the past is treated as
     * reclaimable (effectively available).
     */
    @Exclude
    public boolean isActivelyHeldByOther(String currentUserId) {
        if (!SeatStatus.HELD.equals(status)) return false;
        if (heldBy != null && heldBy.equals(currentUserId)) return false; // our own hold
        return heldUntil != null && heldUntil.toDate().getTime() > System.currentTimeMillis();
    }

    /**
     * @return true if this seat can be selected by {@code currentUserId} right now:
     * either genuinely AVAILABLE, or a HELD seat whose hold has expired.
     */
    @Exclude
    public boolean isSelectableBy(String currentUserId) {
        if (SeatStatus.AVAILABLE.equals(status)) return true;
        if (SeatStatus.BOOKED.equals(status)) return false;
        // HELD: selectable only if it's our own hold, or the hold has lapsed.
        if (heldBy != null && heldBy.equals(currentUserId)) return true;
        return heldUntil == null || heldUntil.toDate().getTime() <= System.currentTimeMillis();
    }
}