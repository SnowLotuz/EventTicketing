package com.capstone.eventticketing.data.model;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

/**
 * Outcome of a check-in scan. Carries enough context for the UI to show the
 * right feedback (green/valid, red/used, red/invalid) and details.
 */
public class CheckInResult {

    public enum Type { VALID, ALREADY_USED, INVALID }

    public final Type type;
    public final String message;
    @Nullable public final String seatNumber;       // for VALID / ALREADY_USED
    @Nullable public final Timestamp checkInTime;    // when it was (first) used

    private CheckInResult(Type type, String message,
                          @Nullable String seatNumber, @Nullable Timestamp checkInTime) {
        this.type = type;
        this.message = message;
        this.seatNumber = seatNumber;
        this.checkInTime = checkInTime;
    }

    public static CheckInResult valid(String seatNumber, Timestamp checkInTime) {
        return new CheckInResult(Type.VALID, "Valid ticket", seatNumber, checkInTime);
    }

    public static CheckInResult alreadyUsed(String seatNumber, @Nullable Timestamp firstCheckIn) {
        return new CheckInResult(Type.ALREADY_USED, "Ticket already checked in", seatNumber, firstCheckIn);
    }

    public static CheckInResult invalid(String message) {
        return new CheckInResult(Type.INVALID, message, null, null);
    }
}