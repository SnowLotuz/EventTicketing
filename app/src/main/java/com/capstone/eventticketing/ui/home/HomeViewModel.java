package com.capstone.eventticketing.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.EventFilter;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link HomeFragment}. Loads all movies once and exposes a filtered view
 * derived from the current {@link EventFilter} (real-time title search + deep
 * filters). Filtering is in-memory; see EventFilter for the rationale.
 */
public class HomeViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository;

    private final MutableLiveData<Integer> refreshTrigger = new MutableLiveData<>(0);
    private final LiveData<Resource<List<Movie>>> rawMovies;

    private final MutableLiveData<EventFilter> filter = new MutableLiveData<>(EventFilter.none());

    /** The list the UI renders: rawMovies passed through the active filter. */
    private final MediatorLiveData<Resource<List<Movie>>> filteredMovies = new MediatorLiveData<>();

    private final MutableLiveData<Resource<Boolean>> wishlistToggleState = new MutableLiveData<>();

    public HomeViewModel() {
        this.movieRepository = new MovieRepository();
        // Always fetch the full catalog; genre is handled by the in-memory filter.
        this.rawMovies = Transformations.switchMap(
                refreshTrigger, t -> movieRepository.getMovies(null));

        // Recompute whenever either the data or the filter changes.
        filteredMovies.addSource(rawMovies, r -> recompute());
        filteredMovies.addSource(filter, f -> recompute());
    }

    private void recompute() {
        Resource<List<Movie>> r = rawMovies.getValue();
        EventFilter f = filter.getValue();
        if (r == null) return;
        if (r.status != Resource.Status.SUCCESS) {
            filteredMovies.setValue(r); // propagate Loading / Error as-is
            return;
        }
        List<Movie> filtered = (f != null ? f : EventFilter.none()).apply(r.data);
        filteredMovies.setValue(Resource.success(filtered));
    }

    public LiveData<Resource<List<Movie>>> getMovies() { return filteredMovies; }
    public LiveData<EventFilter> getFilter() { return filter; }
    public LiveData<Resource<Boolean>> getWishlistToggleState() { return wishlistToggleState; }

    public LiveData<Resource<List<String>>> getWishlistIds() {
        return movieRepository.getWishlistIds();
    }

    /** Real-time search: updates only the query, preserving deep filters. */
    public void setSearchQuery(@NonNull String query) {
        EventFilter current = filter.getValue();
        filter.setValue((current != null ? current : EventFilter.none()).withQuery(query));
    }

    /** Genre chip selection feeds into the same filter object. */
    public void selectCategory(@NonNull String category) {
        EventFilter c = filter.getValue();
        if (c == null) c = EventFilter.none();
        filter.setValue(new EventFilter(c.query, category, c.minPrice, c.maxPrice,
                c.startDateMillis, c.endDateMillis));
    }

    /** Applies a fully-specified deep filter from the bottom sheet. */
    public void applyFilter(@NonNull EventFilter newFilter) {
        filter.setValue(newFilter);
    }

    /** Clears everything back to the default. */
    public void clearFilters() {
        filter.setValue(EventFilter.none());
    }

    public void refresh() {
        Integer current = refreshTrigger.getValue();
        refreshTrigger.setValue(current == null ? 1 : current + 1);
    }

    public void toggleWishlist(@NonNull String movieId, boolean add) {
        LiveData<Resource<Boolean>> source = movieRepository.toggleWishlist(movieId, add);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                wishlistToggleState.setValue(resource);
                source.removeObserver(this);
            }
        });
    }
}