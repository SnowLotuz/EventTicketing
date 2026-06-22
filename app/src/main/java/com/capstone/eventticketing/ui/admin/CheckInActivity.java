package com.capstone.eventticketing.ui.admin;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.CheckInResult;
import com.capstone.eventticketing.databinding.ActivityCheckInBinding;
import com.capstone.eventticketing.util.Resource;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Admin gate scanner. Runs ZXing's continuous decoder, sends each decode to
 * {@link CheckInViewModel} for transactional validation, and renders the
 * three-state result with audio/haptic feedback. Pure View — no Firestore here.
 */
public class CheckInActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private ActivityCheckInBinding binding;
    private CheckInViewModel viewModel;
    private ToneGenerator toneGenerator;

    public static Intent newIntent(@NonNull Context context, @NonNull String eventId) {
        Intent intent = new Intent(context, CheckInActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new CheckInViewModel.Factory(eventId))
                .get(CheckInViewModel.class);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnScanNext.setOnClickListener(v -> dismissResultAndResume());

        setupScanner();
        observeViewModel();
    }

    private void setupScanner() {
        binding.barcodeScanner.setStatusText(getString(R.string.checkin_hint));
        binding.barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(@NonNull BarcodeResult result) {
                if (result.getText() != null) {
                    viewModel.onScan(result.getText());
                }
            }
        });
    }

    private void observeViewModel() {
        viewModel.getCheckInState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                renderResult(resource.data);
            } else if (resource.status == Resource.Status.ERROR) {
                // True error (e.g. network) — toast and keep scanning.
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                viewModel.readyForNextScan();
            }
        });
    }

    private void renderResult(@NonNull CheckInResult result) {
        binding.barcodeScanner.pause(); // stop decoding while showing the result
        binding.overlayResult.setVisibility(View.VISIBLE);

        switch (result.type) {
            case VALID:
                binding.overlayResult.setBackgroundColor(getColor(R.color.seat_available));
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle);
                binding.tvResultTitle.setText(R.string.checkin_valid);
                binding.tvResultDetail.setText(seatLine(result));
                playBeep();
                break;
            case ALREADY_USED:
                binding.overlayResult.setBackgroundColor(getColor(R.color.status_warning));
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle);
                binding.tvResultTitle.setText(R.string.checkin_used);
                binding.tvResultDetail.setText(alreadyUsedLine(result));
                vibrate();
                break;
            case INVALID:
                binding.overlayResult.setBackgroundColor(getColor(R.color.status_error));
                binding.ivResultIcon.setImageResource(R.drawable.ic_cancel_circle);
                binding.tvResultTitle.setText(R.string.checkin_invalid);
                binding.tvResultDetail.setText(result.message);
                vibrate();
                break;
        }
    }

    private String seatLine(@NonNull CheckInResult result) {
        return result.seatNumber != null
                ? getString(R.string.checkin_seat_label, result.seatNumber) : "";
    }

    private String alreadyUsedLine(@NonNull CheckInResult result) {
        String seat = seatLine(result);
        if (result.checkInTime != null) {
            return seat + "\nChecked in at " + TIME_FORMAT.format(result.checkInTime.toDate());
        }
        return seat;
    }

    private void dismissResultAndResume() {
        binding.overlayResult.setVisibility(View.GONE);
        viewModel.readyForNextScan();
        binding.barcodeScanner.resume();
    }

    // --- Feedback ---

    private void playBeep() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        }
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(400);
        }
    }

    // --- Scanner lifecycle (DecoratedBarcodeView requires pause/resume) ---

    @Override
    protected void onResume() {
        super.onResume();
        // Only resume the camera if we're not currently showing a result.
        if (binding.overlayResult.getVisibility() != View.VISIBLE) {
            binding.barcodeScanner.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.barcodeScanner.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        binding = null;
    }
}