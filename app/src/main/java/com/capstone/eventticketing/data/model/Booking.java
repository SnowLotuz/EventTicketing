package com.capstone.eventticketing.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/** Mirrors a document in the {@code Bookings} collection. */
public class Booking {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @DocumentId
    private String bookingId;
    private String userId;
    private String eventId;
    private Timestamp bookingDate;
    private double subTotal;
    private String promoCode;       // nullable
    private double discountAmount;
    private double finalAmount;
    private String status;

    public Booking() { }

    public Booking(String userId, String eventId, double subTotal, String promoCode,
                   double discountAmount, double finalAmount) {
        this.userId = userId;
        this.eventId = eventId;
        this.bookingDate = Timestamp.now();
        this.subTotal = subTotal;
        this.promoCode = promoCode;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.status = STATUS_CONFIRMED;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Timestamp getBookingDate() { return bookingDate; }
    public void setBookingDate(Timestamp bookingDate) { this.bookingDate = bookingDate; }

    public double getSubTotal() { return subTotal; }
    public void setSubTotal(double subTotal) { this.subTotal = subTotal; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}