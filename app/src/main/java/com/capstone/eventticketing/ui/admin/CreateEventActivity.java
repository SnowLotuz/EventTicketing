package com.capstone.eventticketing.ui.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.ActivityCreateEventBinding;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Admin event-creation form. Pure View: collects input, drives native date/time
 * pickers, and delegates validation + persistence to {@link CreateEventViewModel}.
 */
public class CreateEventActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {"Music", "Sports", "Theater"};
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy · h:mm a", Locale.getDefault());

    private ActivityCreateEventBinding binding;
    private CreateEventViewModel viewModel;

    private final Calendar selectedDateTime = Calendar.getInstance();
    private boolean dateTimePicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(CreateEventViewModel.class);

        setupToolbar();
        setupCategoryDropdown();
        setupDateTimePicker();
        setupSaveButton();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupCategoryDropdown() {
        MaterialAutoCompleteTextView dropdown = binding.actCategory;
        dropdown.setSimpleItems(CATEGORIES);
    }

    /** Chains a DatePicker into a TimePicker, then writes the formatted value. */
    private void setupDateTimePicker() {
        binding.etDatetime.setOnClickListener(v -> showDatePicker());
        binding.tilDatetime.setEndIconOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, day);
            showTimePicker();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        datePicker.getDatePicker().setMinDate(now.getTimeInMillis());
        datePicker.show();
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        TimePickerDialog timePicker = new TimePickerDialog(this, (view, hour, minute) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, hour);
            selectedDateTime.set(Calendar.MINUTE, minute);
            selectedDateTime.set(Calendar.SECOND, 0);
            dateTimePicked = true;
            binding.etDatetime.setText(DISPLAY_FORMAT.format(selectedDateTime.getTime()));
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
        timePicker.show();
    }

    private void setupSaveButton() {
        binding.btnSaveEvent.setOnClickListener(v -> viewModel.createEvent(
                text(binding.etTitle),
                text(binding.actCategory),
                text(binding.etVenue),
                text(binding.etDescription),
                text(binding.etImageUrl),
                dateTimePicked ? selectedDateTime.getTimeInMillis() : -1L,
                text(binding.etCapacity),
                text(binding.etPrice)));
    }

    private void observeViewModel() {
        viewModel.getValidationError().observe(this, this::showToast);

        viewModel.getSaveState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    showToast(getString(R.string.create_success));
                    finish();
                    break;
                case ERROR:
                    setLoading(false);
                    showToast(resource.message);
                    break;
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.lottieLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSaveEvent.setEnabled(!isLoading);
        binding.btnSaveEvent.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    @NonNull
    private String text(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    @NonNull
    private String text(MaterialAutoCompleteTextView dropdown) {
        return dropdown.getText() != null ? dropdown.getText().toString().trim() : "";
    }

    private void showToast(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}