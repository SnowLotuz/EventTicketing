package com.capstone.eventticketing.ui.admin;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ActivityEditEventBinding;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EditEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private static final String[] GENRES =
            com.capstone.eventticketing.util.EventFilter.GENRES.toArray(new String[0]);
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());

    private ActivityEditEventBinding binding;
    private EditEventViewModel viewModel;

    private final Calendar selectedDate = Calendar.getInstance();
    private boolean datePicked = false;
    private String currentStatus = Movie.STATUS_NOW_SHOWING;

    public static Intent newIntent(@NonNull Context context, @NonNull String movieId) {
        Intent intent = new Intent(context, EditEventActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, movieId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String movieId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (movieId == null || movieId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new EditEventViewModel.Factory(movieId))
                .get(EditEventViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        ((MaterialAutoCompleteTextView) binding.actGenre).setSimpleItems(GENRES);
        setupStatusChips();
        setupDatePicker();
        binding.btnSaveEvent.setOnClickListener(v -> save());

        observeViewModel();
    }

    private void setupStatusChips() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_now_showing) {
                currentStatus = Movie.STATUS_NOW_SHOWING;
            } else if (id == R.id.chip_coming_soon) {
                currentStatus = Movie.STATUS_COMING_SOON;
            } else if (id == R.id.chip_ended) {
                currentStatus = Movie.STATUS_ENDED;
            }
        });
    }

    private void selectStatusChip(@NonNull String status) {
        switch (status) {
            case Movie.STATUS_COMING_SOON:
                binding.chipComingSoon.setChecked(true);
                break;
            case Movie.STATUS_ENDED:
                binding.chipEnded.setChecked(true);
                break;
            case Movie.STATUS_NOW_SHOWING:
            default:
                binding.chipNowShowing.setChecked(true);
                break;
        }
    }

    private void observeViewModel() {
        viewModel.getMovie().observe(this, resource -> {
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

    private void prefill(@NonNull Movie movie) {
        binding.etTitle.setText(movie.getTitle());
        binding.actGenre.setText(movie.getGenre(), false);
        binding.etDuration.setText(movie.getDurationMinutes() > 0
                ? String.valueOf(movie.getDurationMinutes()) : "");
        binding.etDescription.setText(movie.getDescription());
        binding.etPosterUrl.setText(movie.getPosterUrl());

        currentStatus = movie.getStatus() != null ? movie.getStatus() : Movie.STATUS_NOW_SHOWING;
        selectStatusChip(currentStatus);

        if (movie.getSeatMap() != null) {
            binding.etPrice.setText(String.format(Locale.getDefault(), "%.2f",
                    movie.getSeatMap().getLowestPrice()));
        }

        if (movie.getReleaseDate() != null) {
            selectedDate.setTime(movie.getReleaseDate().toDate());
            datePicked = true;
            binding.etReleaseDate.setText(DISPLAY_FORMAT.format(selectedDate.getTime()));
        }
    }

    private void setupDatePicker() {
        binding.etReleaseDate.setOnClickListener(v -> showDatePicker());
        binding.tilReleaseDate.setEndIconOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog dp = new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate.set(Calendar.YEAR, y);
            selectedDate.set(Calendar.MONTH, m);
            selectedDate.set(Calendar.DAY_OF_MONTH, d);
            selectedDate.set(Calendar.HOUR_OF_DAY, 0);
            selectedDate.set(Calendar.MINUTE, 0);
            selectedDate.set(Calendar.SECOND, 0);
            datePicked = true;
            binding.etReleaseDate.setText(DISPLAY_FORMAT.format(selectedDate.getTime()));
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private void save() {
        viewModel.save(
                text(binding.etTitle),
                text(binding.actGenre),
                text(binding.etDuration),
                text(binding.etDescription),
                text(binding.etPosterUrl),
                datePicked ? selectedDate.getTimeInMillis() : -1L,
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