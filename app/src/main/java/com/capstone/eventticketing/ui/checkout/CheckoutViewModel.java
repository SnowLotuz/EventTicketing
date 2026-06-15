package com.capstone.eventticketing.ui.checkout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Promotion;
import com.capstone.eventticketing.data.repository.BookingRepository;
import com.capstone.eventticketing.data.repository.SeatRepository;
import com.capstone.eventticketing.util.PromoValidator;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link CheckoutActivity}. Holds the order summary, validates promo codes
 * (preview), and runs the booking. Promo preview and the final charge both use
 * {@link PromoValidator}, so what the user sees is what they pay.
 */
public class CheckoutViewModel extends ViewModel {

    @NonNull private final BookingRepository bookingRepository;
    @NonNull private final SeatRepository seatRepository;

    @NonNull private final String eventId;
    @NonNull private final List<String> seatIds;
    private final double subTotal;

    // Applied promo state (null until a valid code is applied).
    @Nullable private Promotion appliedPromo;
    private double discountAmount = 0d;
    private double finalAmount;

    private final MutableLiveData<PromoUiState> promoState = new MutableLiveData<>();
    private final MutableLiveData<OrderTotals> totals = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> bookingState = new MutableLiveData<>();

    public CheckoutViewModel(@NonNull BookingRepository bookingRepository,
                             @NonNull SeatRepository seatRepository,
                             @NonNull String eventId,
                             @NonNull List<String> seatIds,
                             double subTotal) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.eventId = eventId;
        this.seatIds = seatIds;
        this.subTotal = subTotal;
        this.finalAmount = subTotal;
        publishTotals();
    }

    public LiveData<PromoUiState> getPromoState() { return promoState; }
    public LiveData<OrderTotals> getTotals() { return totals; }
    public LiveData<Resource<String>> getBookingState() { return bookingState; }

    public double getSubTotal() { return subTotal; }
    @NonNull public List<String> getSeatIds() { return seatIds; }

    /** Validates and applies a promo code, updating the preview totals. */
    public void applyPromo(@Nullable String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            promoState.setValue(PromoUiState.error("Enter a promo code."));
            return;
        }
        final String code = rawCode.trim();
        promoState.setValue(PromoUiState.loading());

        LiveData<Resource<Promotion>> source = bookingRepository.findPromoByCode(code);
        source.observeForever(new Observer<Resource<Promotion>>() {
            @Override
            public void onChanged(Resource<Promotion> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);

                if (resource.status == Resource.Status.ERROR) {
                    promoState.setValue(PromoUiState.error(
                            resource.message != null ? resource.message : "Could not check code."));
                    return;
                }

                Promotion promo = resource.data; // may be null if not found
                PromoValidator.Result vr = PromoValidator.validate(promo, subTotal);
                if (!vr.valid) {
                    clearAppliedPromo();
                    promoState.setValue(PromoUiState.error(vr.errorMessage));
                } else {
                    appliedPromo = promo;
                    discountAmount = vr.discountAmount;
                    finalAmount = vr.finalAmount;
                    promoState.setValue(PromoUiState.applied(promo.getCode(), discountAmount));
                    publishTotals();
                }
            }
        });
    }

    public void removePromo() {
        clearAppliedPromo();
        promoState.setValue(PromoUiState.removed());
        publishTotals();
    }

    private void clearAppliedPromo() {
        appliedPromo = null;
        discountAmount = 0d;
        finalAmount = subTotal;
        publishTotals();
    }

    private void publishTotals() {
        totals.setValue(new OrderTotals(subTotal, discountAmount, finalAmount));
    }

    /** Runs the booking transaction with the currently applied promo (if any). */
    public void confirmBooking() {
        String promoId = (appliedPromo != null) ? appliedPromo.getPromoId() : null;
        bookingState.setValue(Resource.loading());

        LiveData<Resource<String>> source = bookingRepository.createBooking(eventId, seatIds, promoId);
        source.observeForever(new Observer<Resource<String>>() {
            @Override
            public void onChanged(Resource<String> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                bookingState.setValue(resource);
            }
        });
    }

    /** Releases all held seats — called when the timer expires or the user backs out. */
    public void releaseHolds() {
        for (String seatId : seatIds) {
            seatRepository.releaseSeat(eventId, seatId); // fire-and-forget
        }
    }

    // --- Lightweight UI state holders ---

    public static class OrderTotals {
        public final double subTotal;
        public final double discount;
        public final double finalAmount;
        OrderTotals(double subTotal, double discount, double finalAmount) {
            this.subTotal = subTotal;
            this.discount = discount;
            this.finalAmount = finalAmount;
        }
    }

    public static class PromoUiState {
        public enum Type { LOADING, APPLIED, ERROR, REMOVED }
        public final Type type;
        public final String code;
        public final double discount;
        public final String message;

        private PromoUiState(Type type, String code, double discount, String message) {
            this.type = type;
            this.code = code;
            this.discount = discount;
            this.message = message;
        }
        static PromoUiState loading() { return new PromoUiState(Type.LOADING, null, 0, null); }
        static PromoUiState applied(String code, double discount) {
            return new PromoUiState(Type.APPLIED, code, discount, null);
        }
        static PromoUiState error(String message) { return new PromoUiState(Type.ERROR, null, 0, message); }
        static PromoUiState removed() { return new PromoUiState(Type.REMOVED, null, 0, null); }
    }

    /** Injects repositories and the order context so the View stays logic-free. */
    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String eventId;
        @NonNull private final List<String> seatIds;
        private final double subTotal;

        public Factory(@NonNull String eventId, @NonNull List<String> seatIds, double subTotal) {
            this.eventId = eventId;
            this.seatIds = seatIds;
            this.subTotal = subTotal;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CheckoutViewModel.class)) {
                return (T) new CheckoutViewModel(new BookingRepository(), new SeatRepository(),
                        eventId, seatIds, subTotal);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}