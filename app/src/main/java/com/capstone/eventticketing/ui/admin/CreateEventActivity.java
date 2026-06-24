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
            com.capstone.eventticketing.util.EventFilter.GENRES.toArray(new String[0]);
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    private ActivityCreateEventBinding binding;
    private CreateEventViewModel viewModel;

    private final Calendar selectedDate = Calendar.getInstance();
    private boolean datePicked = false;

    // Tracks the dynamically inflated actor rows
    private final java.util.List<View> actorRows = new java.util.ArrayList<>();

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
        setupAddActor();
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

    private void setupAddActor() {
        binding.btnAddActor.setOnClickListener(v -> addActorRow());
    }

    /** Inflates one actor input row into the container and tracks it for collection/removal. */
    private void addActorRow() {
        View row = getLayoutInflater().inflate(
                R.layout.item_actor_input, binding.containerActors, false);

        row.findViewById(R.id.btn_remove_actor).setOnClickListener(v -> {
            binding.containerActors.removeView(row);
            actorRows.remove(row);
        });

        binding.containerActors.addView(row);
        actorRows.add(row);
    }

    /** Reads the dynamic actor rows, in display (billing) order. Rows with a blank
     * name are skipped, so an empty trailing row never produces a phantom actor. */
    @NonNull
    private java.util.List<com.capstone.eventticketing.data.model.Actor> collectActors() {
        java.util.List<com.capstone.eventticketing.data.model.Actor> actors =
                new java.util.ArrayList<>();
        for (View row : actorRows) {
            com.google.android.material.textfield.TextInputEditText nameEt =
                    row.findViewById(R.id.et_actor_name);
            com.google.android.material.textfield.TextInputEditText urlEt =
                    row.findViewById(R.id.et_actor_url);

            String name = nameEt.getText() != null ? nameEt.getText().toString().trim() : "";
            String url = urlEt.getText() != null ? urlEt.getText().toString().trim() : "";

            if (!name.isEmpty()) {
                actors.add(new com.capstone.eventticketing.data.model.Actor(name, url));
            }
        }
        return actors;
    }

    private void setupSaveButton() {
        binding.btnSaveEvent.setOnClickListener(v -> viewModel.createMovie(
                text(binding.etTitle),
                text(binding.actGenre),
                text(binding.etDuration),
                text(binding.etDescription),
                text(binding.etPosterUrl),
                datePicked ? selectedDate.getTimeInMillis() : -1L,
                text(binding.etPrice),
                text(binding.etTrailerUrl),                    // new
                binding.cbBlockbuster.isChecked(),             // new
                collectActors()));                             // new
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