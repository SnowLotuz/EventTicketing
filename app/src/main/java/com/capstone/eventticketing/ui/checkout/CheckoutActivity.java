package com.capstone.eventticketing.ui.checkout;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.ActivityCheckoutBinding;
import com.capstone.eventticketing.util.Resource;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Checkout screen. Shows the order summary, a hold-expiry countdown, promo
 * application with a crossed-out original price, and a simulated payment that
 * triggers the booking transaction. Pure View — all logic is in
 * {@link CheckoutViewModel}.
 */
public class CheckoutActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_SEAT_IDS = "extra_seat_ids";
    public static final String EXTRA_BASE_PRICE = "extra_base_price";
    public static final String EXTRA_BOOKED_COUNT = "extra_booked_count";
    public static final String EXTRA_IS_BLOCKBUSTER = "extra_is_blockbuster";
    public static final String EXTRA_HOLD_EXPIRY = "extra_hold_expiry";

    /** Simulated payment-processing duration before the booking transaction runs. */
    private static final long PAYMENT_SIMULATION_MS = 2200L;

    private ActivityCheckoutBinding binding;
    private CheckoutViewModel viewModel;
    private CountDownTimer countDownTimer;
    private boolean bookingCompleted = false;

    public static Intent newIntent(@NonNull Context context, @NonNull String eventId,
                                   @NonNull ArrayList<String> seatIds,
                                   double basePrice, int bookedCount, boolean isBlockbuster,
                                   long holdExpiryMillis) {
        Intent intent = new Intent(context, CheckoutActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putStringArrayListExtra(EXTRA_SEAT_IDS, seatIds);
        intent.putExtra(EXTRA_BASE_PRICE, basePrice);
        intent.putExtra(EXTRA_BOOKED_COUNT, bookedCount);
        intent.putExtra(EXTRA_IS_BLOCKBUSTER, isBlockbuster);
        intent.putExtra(EXTRA_HOLD_EXPIRY, holdExpiryMillis);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        ArrayList<String> seatIds = getIntent().getStringArrayListExtra(EXTRA_SEAT_IDS);
        double basePrice = getIntent().getDoubleExtra(EXTRA_BASE_PRICE, 0d);
        int bookedCount = getIntent().getIntExtra(EXTRA_BOOKED_COUNT, 0);
        boolean isBlockbuster = getIntent().getBooleanExtra(EXTRA_IS_BLOCKBUSTER, false);
        long holdExpiry = getIntent().getLongExtra(EXTRA_HOLD_EXPIRY, 0L);

        if (eventId == null || seatIds == null || seatIds.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this,
                new CheckoutViewModel.Factory(eventId, seatIds, basePrice, bookedCount, isBlockbuster))
                .get(CheckoutViewModel.class);

        binding.tvSeats.setText(getString(R.string.checkout_seats_prefix) + " "
                + android.text.TextUtils.join(", ", seatIds));

        setupListeners();
        observeViewModel();

        // --- MỚI: Khóa ô nhập mã Promo nếu đơn hàng đã được áp dụng giảm giá tự động ---
        if (viewModel.isSamePriceDiscount()) {
            binding.etPromo.setEnabled(false);
            binding.tilPromo.setEnabled(false);
            binding.tvPromoFeedback.setVisibility(View.VISIBLE);
            binding.tvPromoFeedback.setTextColor(getColor(R.color.slate_600));
            binding.tvPromoFeedback.setText("Promo codes can't be combined with this discount.");
        }

        startCountdown(holdExpiry);
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> exitAndRelease());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitAndRelease();
            }
        });

        binding.tilPromo.setEndIconOnClickListener(v -> applyPromoFromInput());
        binding.etPromo.setOnEditorActionListener((tv, actionId, e) -> {
            applyPromoFromInput();
            return true;
        });

        binding.btnPay.setOnClickListener(v -> startSimulatedPayment());
    }

    private void applyPromoFromInput() {
        String code = binding.etPromo.getText() != null
                ? binding.etPromo.getText().toString() : "";
        viewModel.applyPromo(code);
    }

    private void observeViewModel() {
        viewModel.getTotals().observe(this, this::renderTotals);
        viewModel.getPromoState().observe(this, this::renderPromoState);
        viewModel.getBookingState().observe(this, this::renderBookingState);
    }

    private void renderTotals(CheckoutViewModel.OrderTotals totals) {
        if (totals == null) return;
        binding.tvSubtotal.setText(money(totals.subTotal));
        binding.tvFinalTotal.setText(money(totals.finalAmount));

        boolean hasDiscount = totals.discount > 0d;
        binding.layoutDiscountRow.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
        binding.tvDiscount.setText("-" + money(totals.discount));

        // Crossed-out original price when discounted.
        if (hasDiscount) {
            binding.tvOriginalTotal.setVisibility(View.VISIBLE);
            binding.tvOriginalTotal.setText(money(totals.subTotal));
            binding.tvOriginalTotal.setPaintFlags(
                    binding.tvOriginalTotal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            binding.tvOriginalTotal.setVisibility(View.GONE);
        }
    }

    private void renderPromoState(CheckoutViewModel.PromoUiState state) {
        if (state == null) return;
        switch (state.type) {
            case LOADING:
                binding.tvPromoFeedback.setVisibility(View.VISIBLE);
                binding.tvPromoFeedback.setTextColor(getColor(R.color.slate_600));
                binding.tvPromoFeedback.setText(R.string.checkout_promo_checking);
                break;
            case APPLIED:
                binding.tvPromoFeedback.setVisibility(View.VISIBLE);
                binding.tvPromoFeedback.setTextColor(getColor(R.color.seat_available));
                binding.tvPromoFeedback.setText(String.format(Locale.getDefault(),
                        "%s applied · you save %s", state.code, money(state.discount)));
                break;
            case ERROR:
                binding.tvPromoFeedback.setVisibility(View.VISIBLE);
                binding.tvPromoFeedback.setTextColor(getColor(R.color.status_error));
                binding.tvPromoFeedback.setText(state.message);
                break;
            case REMOVED:
                binding.tvPromoFeedback.setVisibility(View.GONE);
                break;
        }
    }

    private void startSimulatedPayment() {
        // Show the processing overlay, then run the real booking transaction.
        binding.overlayProcessing.setVisibility(View.VISIBLE);
        binding.tvProcessing.setText(R.string.checkout_processing);
        binding.btnPay.setEnabled(false);

        binding.getRoot().postDelayed(() -> {
            if (!isFinishing()) viewModel.confirmBooking();
        }, PAYMENT_SIMULATION_MS);
    }

    private void renderBookingState(Resource<String> resource) {
        if (resource == null) return;
        switch (resource.status) {
            case LOADING:
                // Overlay already visible from startSimulatedPayment().
                break;
            case SUCCESS:
                bookingCompleted = true;
                cancelCountdown();
                showSuccessThenExit(resource.data);
                break;
            case ERROR:
                binding.overlayProcessing.setVisibility(View.GONE);
                binding.btnPay.setEnabled(true);
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void showSuccessThenExit(String bookingId) {
        binding.tvProcessing.setText(R.string.checkout_success);
        // Swap in the success animation if available; here we just brief-pause.
        binding.getRoot().postDelayed(() -> {
            if (isFinishing()) return;
            // Route to My Tickets in a later step; for now return to home.
            Toast.makeText(this, R.string.checkout_success, Toast.LENGTH_SHORT).show();
            finish();
        }, 1500L);
    }

    // --- Countdown timer ---

    private void startCountdown(long holdExpiryMillis) {
        long remaining = holdExpiryMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            onTimeExpired();
            return;
        }
        countDownTimer = new CountDownTimer(remaining, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds = millisUntilFinished / 1000L;
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                binding.tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }
            @Override
            public void onFinish() {
                binding.tvTimer.setText("00:00");
                onTimeExpired();
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void onTimeExpired() {
        if (bookingCompleted) return; // a booking that just succeeded shouldn't be undone
        viewModel.releaseHolds();
        Toast.makeText(this, R.string.checkout_expired, Toast.LENGTH_LONG).show();
        finish();
    }

    /** User backed out before paying — free the seats. */
    private void exitAndRelease() {
        if (!bookingCompleted) {
            cancelCountdown();
            viewModel.releaseHolds();
        }
        finish();
    }

    private String money(double amount) {
        return String.format(Locale.getDefault(), "$%.2f", amount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        binding = null;
    }
}