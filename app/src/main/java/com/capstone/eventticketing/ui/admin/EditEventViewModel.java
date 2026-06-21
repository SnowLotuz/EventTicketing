package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Backs {@link EditEventActivity}. Loads a movie for pre-fill and updates its
 * mutable fields. Capacity/pricing are deliberately not editable to protect
 * existing seats and bookings.
 */
public class EditEventViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository;
    @NonNull private final String movieId;

    private final MutableLiveData<Resource<Movie>> movie = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> updateState = new MutableLiveData<>();
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public EditEventViewModel(@NonNull MovieRepository movieRepository, @NonNull String movieId) {
        this.movieRepository = movieRepository;
        this.movieId = movieId;
        load();
    }

    public LiveData<Resource<Movie>> getMovie() { return movie; }
    public LiveData<Resource<Boolean>> getUpdateState() { return updateState; }
    public LiveData<String> getValidationError() { return validationError; }

    public void load() {
        movie.setValue(Resource.loading());
        LiveData<Resource<Movie>> source = movieRepository.getMovieById(movieId);
        source.observeForever(new Observer<Resource<Movie>>() {
            @Override
            public void onChanged(Resource<Movie> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                movie.setValue(r);
            }
        });
    }

    public void save(String title, String genre, String durationStr, String description,
                     @Nullable String posterUrl, long releaseDateMillis, String status) {
        if (isBlank(title)) { validationError.setValue("Title cannot be empty."); return; }
        if (isBlank(genre)) { validationError.setValue("Please select a genre."); return; }
        if (isBlank(durationStr)) { validationError.setValue("Duration cannot be empty."); return; }
        if (isBlank(description)) { validationError.setValue("Description cannot be empty."); return; }
        if (releaseDateMillis <= 0) { validationError.setValue("Please pick a release date."); return; }

        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationStr.trim());
            if (durationMinutes <= 0) { validationError.setValue("Duration must be greater than 0."); return; }
        } catch (NumberFormatException e) {
            validationError.setValue("Please enter a valid duration in minutes.");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title.trim());
        updates.put("genre", genre.trim());
        updates.put("durationMinutes", durationMinutes);
        updates.put("description", description.trim());
        updates.put("posterUrl", posterUrl != null ? posterUrl.trim() : "");
        updates.put("releaseDate", new Timestamp(new Date(releaseDateMillis)));
        updates.put("status", status);

        updateState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = movieRepository.updateMovie(movieId, updates);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                updateState.setValue(r);
            }
        });
    }

    private boolean isBlank(@Nullable String s) { return s == null || s.trim().isEmpty(); }

    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String movieId;
        public Factory(@NonNull String movieId) { this.movieId = movieId; }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(EditEventViewModel.class)) {
                return (T) new EditEventViewModel(new MovieRepository(), movieId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}