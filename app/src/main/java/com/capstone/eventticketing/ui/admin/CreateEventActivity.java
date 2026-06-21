package com.capstone.eventticketing.ui.admin;

import android.app.DatePickerDialog;
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
 * Admin movie-creation form. Pure View: collects input, drives the native date
 * picker, and delegates validation + persistence to {@link CreateEventViewModel}.
 * Cinema capacity is fixed (150) so there is no capacity input.
 */
public class CreateEventActivity extends AppCompatActivity {

    private static final String[] GENRES =
            {"Action", "Comedy", "Drama", "Sci-Fi", "Horror", "Animation", "Thriller", "Romance"};
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    private ActivityCreateEventBinding binding;
    private CreateEventViewModel viewModel;

    private final Calendar selectedDate = Calendar.getInstance();
    private boolean datePicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(CreateEventViewModel.class);

        setupToolbar();
        setupGenreDropdown();
        setupDatePicker();
        setupSaveButton();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupGenreDropdown() {
        MaterialAutoCompleteTextView dropdown = binding.actGenre;
        dropdown.setSimpleItems(GENRES);
    }

    private void setupDatePicker() {
        binding.etReleaseDate.setOnClickListener(v -> showDatePicker());
        binding.tilReleaseDate.setEndIconOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            selectedDate.set(Calendar.HOUR_OF_DAY, 0);
            selectedDate.set(Calendar.MINUTE, 0);
            selectedDate.set(Calendar.SECOND, 0);
            datePicked = true;
            binding.etReleaseDate.setText(DISPLAY_FORMAT.format(selectedDate.getTime()));
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private void setupSaveButton() {
        binding.btnSaveEvent.setOnClickListener(v -> viewModel.createMovie(
                text(binding.etTitle),
                text(binding.actGenre),
                text(binding.etDuration),
                text(binding.etDescription),
                text(binding.etPosterUrl),
                datePicked ? selectedDate.getTimeInMillis() : -1L,
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