package com.capstone.eventticketing.ui.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.databinding.ActivityEditEventBinding;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EditEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private static final String[] CATEGORIES = {"Music", "Sports", "Theater"};
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy · h:mm a", Locale.getDefault());

    private ActivityEditEventBinding binding;
    private EditEventViewModel viewModel;

    private final Calendar selectedDateTime = Calendar.getInstance();
    private boolean dateTimePicked = false;
    private String currentStatus = Event.STATUS_UPCOMING;

    public static Intent newIntent(@NonNull Context context, @NonNull String eventId) {
        Intent intent = new Intent(context, EditEventActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new EditEventViewModel.Factory(eventId))
                .get(EditEventViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        ((MaterialAutoCompleteTextView) binding.actCategory).setSimpleItems(CATEGORIES);
        setupStatusChips();
        setupDateTimePicker();
        binding.btnSaveEvent.setOnClickListener(v -> save());

        observeViewModel();
    }

    private void setupStatusChips() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_upcoming) {
                currentStatus = Event.STATUS_UPCOMING;
            } else if (id == R.id.chip_ongoing) {
                currentStatus = Event.STATUS_ONGOING;
            } else if (id == R.id.chip_ended) {
                currentStatus = Event.STATUS_ENDED;
            }
        });
    }

    private void selectStatusChip(@NonNull String status) {
        switch (status) {
            case Event.STATUS_ONGOING:
                binding.chipOngoing.setChecked(true);
                break;
            case Event.STATUS_ENDED:
                binding.chipEnded.setChecked(true);
                break;
            case Event.STATUS_UPCOMING:
            default:
                binding.chipUpcoming.setChecked(true);
                break;
        }
    }

    private void observeViewModel() {
        viewModel.getEvent().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                prefill(resource.data);
            } else if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        viewModel.getValidationError().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getUpdateState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    Toast.makeText(this, R.string.edit_success, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case ERROR:
                    setLoading(false);
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void prefill(@NonNull Event event) {
        binding.etTitle.setText(event.getTitle());
        binding.actCategory.setText(event.getCategory(), false);
        binding.etVenue.setText(event.getVenue());
        binding.etDescription.setText(event.getDescription());
        binding.etImageUrl.setText(event.getImageUrl());

        currentStatus = event.getStatus() != null ? event.getStatus() : Event.STATUS_UPCOMING;
        selectStatusChip(currentStatus);

        if (event.getSeatMap() != null) {
            binding.etCapacity.setText(String.valueOf(event.getSeatMap().getTotalCapacity()));
            binding.etPrice.setText(String.format(Locale.getDefault(), "%.2f",
                    event.getSeatMap().getLowestPrice()));
        }

        if (event.getEventDate() != null) {
            selectedDateTime.setTime(event.getEventDate().toDate());
            dateTimePicked = true;
            binding.etDatetime.setText(DISPLAY_FORMAT.format(selectedDateTime.getTime()));
        }
    }

    private void setupDateTimePicker() {
        binding.etDatetime.setOnClickListener(v -> showDatePicker());
        binding.tilDatetime.setEndIconOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog dp = new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDateTime.set(Calendar.YEAR, y);
            selectedDateTime.set(Calendar.MONTH, m);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, d);
            showTimePicker();
        }, selectedDateTime.get(Calendar.YEAR), selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private void showTimePicker() {
        TimePickerDialog tp = new TimePickerDialog(this, (view, h, min) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, h);
            selectedDateTime.set(Calendar.MINUTE, min);
            selectedDateTime.set(Calendar.SECOND, 0);
            dateTimePicked = true;
            binding.etDatetime.setText(DISPLAY_FORMAT.format(selectedDateTime.getTime()));
        }, selectedDateTime.get(Calendar.HOUR_OF_DAY), selectedDateTime.get(Calendar.MINUTE), false);
        tp.show();
    }

    private void save() {
        viewModel.save(
                text(binding.etTitle),
                text(binding.actCategory),
                text(binding.etVenue),
                text(binding.etDescription),
                text(binding.etImageUrl),
                dateTimePicked ? selectedDateTime.getTimeInMillis() : -1L,
                currentStatus);
    }

    private void setLoading(boolean loading) {
        binding.lottieLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSaveEvent.setEnabled(!loading);
        binding.btnSaveEvent.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    private String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }
    private String text(MaterialAutoCompleteTextView e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}