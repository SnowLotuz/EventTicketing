package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.util.SeatMapGenerator;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Backs {@link CreateEventActivity}. Validates form input, assembles a complete
 * {@link Movie} (including a non-null nested {@code seatMap} and {@code rating}
 * to prevent client-side null crashes), and persists via {@link MovieRepository}.
 */
public class CreateEventViewModel extends ViewModel {

    private static final String TIER_STANDARD = "Standard";

    @NonNull private final MovieRepository movieRepository;

    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public CreateEventViewModel() {
        this.movieRepository = new MovieRepository();
    }

    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<String> getValidationError() { return validationError; }

    /**
     * Validates inputs and, if valid, builds and saves the movie.
     */
    public void createMovie(String title,
                            String genre,
                            String durationStr,
                            String description,
                            String posterUrl,
                            long releaseDateMillis,
                            String priceStr) {

        if (isBlank(title)) { validationError.setValue("Please enter a movie title."); return; }
        if (isBlank(genre)) { validationError.setValue("Please select a genre."); return; }
        if (isBlank(durationStr)) { validationError.setValue("Please enter movie duration."); return; }
        if (isBlank(description)) { validationError.setValue("Please enter a description."); return; }
        if (releaseDateMillis <= 0) { validationError.setValue("Please pick a release date."); return; }

        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationStr.trim());
            if (durationMinutes <= 0) { validationError.setValue("Duration must be greater than 0."); return; }
        } catch (NumberFormatException e) {
            validationError.setValue("Please enter a valid duration in minutes.");
            return;
        }

        double basePrice;
        try {
            basePrice = Double.parseDouble(priceStr.trim());
            if (basePrice < 0) { validationError.setValue("Price cannot be negative."); return; }
        } catch (NumberFormatException e) {
            validationError.setValue("Please enter a valid price.");
            return;
        }

        Movie movie = buildMovie(title, genre, durationMinutes, description, posterUrl, releaseDateMillis, basePrice);

        final double finalBasePrice = basePrice;

        saveState.setValue(Resource.loading());
        LiveData<Resource<String>> createSource = movieRepository.createMovie(movie);
        createSource.observeForever(new androidx.lifecycle.Observer<Resource<String>>() {
            @Override
            public void onChanged(Resource<String> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                createSource.removeObserver(this);

                if (resource.status != Resource.Status.SUCCESS || resource.data == null) {
                    saveState.setValue(Resource.error(
                            resource.message != null ? resource.message : "Failed to create movie."));
                    return;
                }
                // Movie created — now seed its 150-seat grid.
                seedSeats(resource.data, finalBasePrice);
            }
        });
    }

    /** Generates and writes the seat grid for a freshly created movie. */
    private void seedSeats(@NonNull String movieId, double basePrice) {
        List<Seat> seats = SeatMapGenerator.generateCinema(TIER_STANDARD, basePrice);

        LiveData<Resource<Boolean>> seatSource = movieRepository.createSeatsForMovie(movieId, seats);
        seatSource.observeForever(new androidx.lifecycle.Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                seatSource.removeObserver(this);

                if (resource.status == Resource.Status.SUCCESS) {
                    saveState.setValue(Resource.success(movieId));
                } else {
                    saveState.setValue(Resource.error(
                            "Movie created, but seat setup failed: "
                                    + (resource.message != null ? resource.message : "unknown error")));
                }
            }
        });
    }

    /** Assembles a fully-populated Movie. Nested objects are never left null. */
    private Movie buildMovie(String title, String genre, int durationMinutes, String description,
                             @Nullable String posterUrl, long releaseDateMillis, double basePrice) {
        Movie movie = new Movie();
        movie.setTitle(title.trim());
        movie.setGenre(genre.trim());
        movie.setDurationMinutes(durationMinutes);
        movie.setDescription(description.trim());
        movie.setPosterUrl(posterUrl != null ? posterUrl.trim() : "");
        movie.setReleaseDate(new Timestamp(new Date(releaseDateMillis)));
        movie.setStatus(Movie.STATUS_NOW_SHOWING);

        // Nested seatMap with a single base tier
        Movie.SeatMap seatMap = new Movie.SeatMap();
        seatMap.setTotalCapacity(SeatMapGenerator.CINEMA_CAPACITY);
        Map<String, Double> tiers = new HashMap<>();
        tiers.put(TIER_STANDARD, basePrice);
        seatMap.setPricingTiers(tiers);
        movie.setSeatMap(seatMap);

        // Initialize rating
        Movie.Rating rating = new Movie.Rating();
        rating.setAverageScore(0d);
        rating.setTotalReviews(0);
        movie.setRating(rating);

        return movie;
    }

    private boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}