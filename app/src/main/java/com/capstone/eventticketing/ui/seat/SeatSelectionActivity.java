package com.capstone.eventticketing.ui.seat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.databinding.ActivitySeatSelectionBinding;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.ui.checkout.CheckoutActivity;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

/**
 * Seat selection screen. Renders the live seat map, reflects the user's holds,
 * and shows a running total. Pure View: taps and lifecycle events are forwarded
 * to {@link SeatSelectionViewModel}; all hold/release/transaction logic is there.
 */
public class SeatSelectionActivity extends AppCompatActivity
        implements SeatMapView.OnSeatTapListener {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ActivitySeatSelectionBinding binding;
    private SeatSelectionViewModel viewModel;

    public static Intent newIntent(@NonNull Context context, @NonNull String eventId) {
        Intent intent = new Intent(context, SeatSelectionActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySeatSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new SeatSelectionViewModel.Factory(eventId))
                .get(SeatSelectionViewModel.class);

        setupMap();
        setupListeners();
        observeViewModel();
    }

    private void setupMap() {
        binding.seatMap.setCurrentUserId(
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                        ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                        : null);
        binding.seatMap.setOnSeatTapListener(this);
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> confirmExit());

        // Releasing holds on back-press so seats free up for others immediately.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        binding.btnContinue.setOnClickListener(v -> {
            List<Seat> selected = viewModel.getSelectedSeats().getValue();
            if (selected == null || selected.isEmpty()) return;

            ArrayList<String> seatIds = new ArrayList<>(viewModel.getSelectedSeatIds());
            long holdExpiry = viewModel.getEarliestHoldExpiryMillis();

            startActivity(CheckoutActivity.newIntent(
                    this,
                    getIntent().getStringExtra(EXTRA_EVENT_ID),
                    seatIds,
                    viewModel.getBasePrice(),
                    viewModel.getBookedCount(),
                    viewModel.isBlockbuster(),
                    holdExpiry));
        });
    }

    private void observeViewModel() {
        viewModel.getSeats().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.lottieLoading.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    binding.lottieLoading.setVisibility(View.GONE);
                    if (resource.data != null) {
                        binding.seatMap.setSeats(resource.data);
                        // Re-assert our selection tint after each fresh snapshot.
                        binding.seatMap.setSelectedSeatIds(viewModel.getSelectedSeatIds());
                    }
                    break;
                case ERROR:
                    binding.lottieLoading.setVisibility(View.GONE);
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getSelectedSeats().observe(this, this::renderSelection);

        viewModel.getActionError().observe(this, message -> {
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void renderSelection(List<Seat> selected) {
        binding.seatMap.setSelectedSeatIds(viewModel.getSelectedSeatIds());

        boolean hasSelection = selected != null && !selected.isEmpty();
        binding.btnContinue.setEnabled(hasSelection);

        if (!hasSelection) {
            binding.tvSelectionSummary.setText(R.string.seat_none_selected);
            binding.tvTotal.setText(String.format(Locale.getDefault(), "$%.2f", 0.0));
            return;
        }

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) ids.append(", ");
            ids.append(selected.get(i).getSeatId());
        }
        String count = selected.size() == 1 ? "1 seat" : selected.size() + " seats";
        binding.tvSelectionSummary.setText(String.format("%s · %s", count, ids));
        binding.tvTotal.setText(String.format(Locale.getDefault(), "$%.2f",
                viewModel.getSelectedTotal()));
    }

    @Override
    public void onSeatTapped(@NonNull Seat seat) {
        viewModel.onSeatTapped(seat);
    }

    /** Releases any holds, then exits — so seats aren't locked by an abandoned screen. */
    private void confirmExit() {
        viewModel.releaseAllHolds();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

}