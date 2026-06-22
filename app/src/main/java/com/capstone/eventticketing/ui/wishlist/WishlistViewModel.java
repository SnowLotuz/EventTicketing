package com.capstone.eventticketing.ui.wishlist;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link WishlistActivity}. Loads the user's wishlisted movies and handles
 * removing a movie from the wishlist, reloading the list after a successful
 * removal. No Firestore access here — all data work is delegated to
 * {@link MovieRepository}.
 */
public class WishlistViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository = new MovieRepository();

    private final MutableLiveData<Resource<List<Movie>>> movies = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> removeState = new MutableLiveData<>();

    public LiveData<Resource<List<Movie>>> getMovies() { return movies; }
    public LiveData<Resource<Boolean>> getRemoveState() { return removeState; }

    /** Loads (or reloads) the wishlist. */
    public void load() {
        forward(movieRepository.getWishlistMovies(), movies);
    }

    /** Removes a movie from the wishlist, then reloads on success. */
    public void removeFromWishlist(@NonNull String movieId) {
        removeState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = movieRepository.toggleWishlist(movieId, false);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                removeState.setValue(r);
                if (r.status == Resource.Status.SUCCESS) {
                    load(); // reflect the removal in the list
                }
            }
        });
    }

    private <T> void forward(@NonNull LiveData<Resource<T>> source,
                             @NonNull MutableLiveData<Resource<T>> target) {
        target.setValue(Resource.loading());
        source.observeForever(new Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                target.setValue(r);
            }
        });
    }
}