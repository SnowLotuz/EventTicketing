package com.capstone.eventticketing.ui.detail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.data.model.Review;
import com.capstone.eventticketing.data.repository.ReviewRepository;

import java.util.List;

/**
 * Backs {@link EventDetailActivity}. Receives its {@code movieId} at construction
 * (via {@link Factory}). Owns the movie fetch, the wishlist state, and reviews.
 */
public class EventDetailViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository;
    @NonNull private final String movieId;
    @NonNull private final ReviewRepository reviewRepository = new ReviewRepository();

    private final MutableLiveData<Resource<Movie>> movie = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> wishlistState = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Review>>> reviews = new MutableLiveData<>();

    public EventDetailViewModel(@NonNull MovieRepository movieRepository, @NonNull String movieId) {
        this.movieRepository = movieRepository;
        this.movieId = movieId;
        loadEvent();
        loadWishlistState();
    }

    public LiveData<Resource<Movie>> getMovie() { return movie; }
    public LiveData<Resource<Boolean>> getWishlistState() { return wishlistState; }
    public LiveData<Resource<List<Review>>> getReviews() { return reviews; }

    /** Loads (or reloads, on retry) the movie document. */
    public void loadEvent() {
        forward(movieRepository.getMovieById(movieId), movie);
    }

    private void loadWishlistState() {
        forward(isMovieWishlisted(), wishlistState);
    }

    /** Reads the current user's wishlist and reports whether this movie is in it. */
    private LiveData<Resource<Boolean>> isMovieWishlisted() {
        MutableLiveData<Resource<Boolean>> out = new MutableLiveData<>();
        out.setValue(Resource.loading());
        LiveData<Resource<List<String>>> source = movieRepository.getWishlistIds();
        source.observeForever(new androidx.lifecycle.Observer<Resource<List<String>>>() {
            @Override
            public void onChanged(Resource<List<String>> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                if (r.status == Resource.Status.SUCCESS) {
                    boolean inList = r.data != null && r.data.contains(movieId);
                    out.setValue(Resource.success(inList));
                } else {
                    out.setValue(Resource.error(r.message != null ? r.message : "Failed to load wishlist."));
                }
            }
        });
        return out;
    }

    /** Loads (or reloads, after a new review) the reviews for this movie. */
    public void loadReviews() {
        forward(reviewRepository.getReviewsForEvent(movieId), reviews);
    }

    public void toggleWishlist(boolean currentlyWishlisted) {
        boolean add = !currentlyWishlisted;
        LiveData<Resource<Boolean>> source = movieRepository.toggleWishlist(movieId, add);
        source.observeForever(new androidx.lifecycle.Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                if (resource.status == Resource.Status.SUCCESS) {
                    wishlistState.setValue(Resource.success(add));
                } else {
                    wishlistState.setValue(Resource.error(
                            resource.message != null ? resource.message : "Failed to update wishlist."));
                }
                source.removeObserver(this);
            }
        });
    }

    private <T> void forward(@NonNull LiveData<Resource<T>> source,
                             @NonNull MutableLiveData<Resource<T>> target) {
        target.setValue(Resource.loading());
        source.observeForever(new androidx.lifecycle.Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                target.setValue(resource);
                source.removeObserver(this);
            }
        });
    }

    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String movieId;

        public Factory(@NonNull String movieId) {
            this.movieId = movieId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(EventDetailViewModel.class)) {
                return (T) new EventDetailViewModel(new MovieRepository(), movieId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}