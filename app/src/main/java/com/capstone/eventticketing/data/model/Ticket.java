package com.capstone.eventticketing.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/** Mirrors a document in the {@code Tickets} collection. */
public class Ticket {

    @DocumentId
    private String ticketId;
    private String bookingId;
    private String eventId;
    private String userId;
    private String seatNumber;
    private String qrCodeData;
    private boolean isCheckedIn;
    private Timestamp checkInTime;  // nullable

    public Ticket() { }

    public Ticket(String bookingId, String eventId, String userId, String seatNumber, String qrCodeData) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.qrCodeData = qrCodeData;
        this.isCheckedIn = false;
        this.checkInTime = null;
    }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    // Firestore maps the boolean field "isCheckedIn" via these accessors.
    public boolean isCheckedIn() { return isCheckedIn; }
    public void setCheckedIn(boolean checkedIn) { isCheckedIn = checkedIn; }

    public Timestamp getCheckInTime() { return checkInTime; }
    public void setCheckInTime(Timestamp checkInTime) { this.checkInTime = checkInTime; }
}