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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backs {@link HomeFragment}. Loads all movies once and exposes a filtered view
 * derived from the current {@link EventFilter} (real-time title search + deep
 * filters). Filtering is in-memory; see EventFilter for the rationale.
 */
public class HomeViewModel extends ViewModel {

    // --- Section configuration constants ---
    /** Banner shows the N most-recent blockbusters; recommended picks from the M most-recent. */
    private static final int BANNER_MAX = 5;
    private static final int RECOMMENDED_POOL = 6;
    private static final int RECOMMENDED_COUNT = 1; // a single highlighted pick; raise if you want a rail

    @NonNull private final MovieRepository movieRepository;

    private final MutableLiveData<Integer> refreshTrigger = new MutableLiveData<>(0);
    private final LiveData<Resource<List<Movie>>> rawMovies;

    private final MutableLiveData<EventFilter> filter = new MutableLiveData<>(EventFilter.none());

    /** The list the UI renders: rawMovies passed through the active filter. */
    private final MediatorLiveData<Resource<List<Movie>>> filteredMovies = new MediatorLiveData<>();

    /**
     * The sectioned Home model, derived from the filtered list while the filter is
     * idle. Emits {@code null} when a filter/search is active — the Fragment then
     * renders the flat {@link #getMovies()} list instead. This keeps browsing rich
     * and searching simple, without duplicating the filter logic.
     */
    private final MediatorLiveData<List<HomeSection>> sections = new MediatorLiveData<>();

    private final MutableLiveData<Resource<Boolean>> wishlistToggleState = new MutableLiveData<>();

    public HomeViewModel() {
        this.movieRepository = new MovieRepository();
        // Always fetch the full catalog; genre is handled by the in-memory filter.
        this.rawMovies = Transformations.switchMap(
                refreshTrigger, t -> movieRepository.getMovies(null));

        // Recompute whenever either the data or the filter changes.
        filteredMovies.addSource(rawMovies, r -> recompute());
        filteredMovies.addSource(filter, f -> recompute());

        // Derive the sectioned view from the filtered list. Sections are only built
        // when the filter is idle; otherwise the flat filtered list is used.
        sections.addSource(filteredMovies, r -> rebuildSections());
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

    /** Rebuilds the Home sections from the current filtered list, idle-mode only. */
    private void rebuildSections() {
        Resource<List<Movie>> r = filteredMovies.getValue();
        EventFilter f = filter.getValue();

        // Only section the view when browsing (no active filter) and data is ready.
        boolean idle = (f == null) || f.isNone();
        if (!idle || r == null || r.status != Resource.Status.SUCCESS || r.data == null) {
            sections.setValue(null); // Fragment falls back to the flat list
            return;
        }

        sections.setValue(buildSections(r.data));
    }

    /**
     * Builds the ordered Home sections from the full (newest-first) movie list:
     * a blockbuster banner, a Now Showing rail, a Coming Soon rail, and a single
     * Recommended pick. Sections with no movies are omitted entirely.
     *
     * <p>The input is already ordered by release date descending (the query's
     * {@code orderBy}), so "most recent" is simply a prefix after filtering.
     */
    @NonNull
    private List<HomeSection> buildSections(@NonNull List<Movie> all) {
        List<HomeSection> result = new ArrayList<>();

        // Banner: up to BANNER_MAX most-recent blockbusters that are showing or upcoming.
        List<Movie> banner = new ArrayList<>();
        for (Movie m : all) {
            if (banner.size() >= BANNER_MAX) break;
            if (m.isBlockbuster() && isShowingOrSoon(m)) banner.add(m);
        }
        if (!banner.isEmpty()) result.add(new HomeSection.Banner(banner));

        // Now Showing rail.
        List<Movie> nowShowing = filterByStatus(all, Movie.STATUS_NOW_SHOWING);
        if (!nowShowing.isEmpty()) {
            result.add(new HomeSection.Header("Now Showing"));
            result.add(new HomeSection.Rail("now_showing", nowShowing));
        }

        // Coming Soon rail.
        List<Movie> comingSoon = filterByStatus(all, Movie.STATUS_COMING_SOON);
        if (!comingSoon.isEmpty()) {
            result.add(new HomeSection.Header("Coming Soon"));
            result.add(new HomeSection.Rail("coming_soon", comingSoon));
        }

        // Recommended: a random pick from the RECOMMENDED_POOL most-recent showing/soon movies.
        List<Movie> pool = new ArrayList<>();
        for (Movie m : all) {
            if (pool.size() >= RECOMMENDED_POOL) break;
            if (isShowingOrSoon(m)) pool.add(m);
        }
        if (!pool.isEmpty()) {
            Collections.shuffle(pool);
            List<Movie> recommended = pool.subList(0, Math.min(RECOMMENDED_COUNT, pool.size()));
            result.add(new HomeSection.Header("Recommended"));
            result.add(new HomeSection.Rail("recommended", new ArrayList<>(recommended)));
        }

        return result;
    }

    @NonNull
    private List<Movie> filterByStatus(@NonNull List<Movie> all, @NonNull String status) {
        List<Movie> out = new ArrayList<>();
        for (Movie m : all) {
            if (status.equals(m.getStatus())) out.add(m);
        }
        return out;
    }

    private boolean isShowingOrSoon(@NonNull Movie m) {
        return Movie.STATUS_NOW_SHOWING.equals(m.getStatus())
                || Movie.STATUS_COMING_SOON.equals(m.getStatus());
    }

    public LiveData<Resource<List<Movie>>> getMovies() { return filteredMovies; }
    public LiveData<List<HomeSection>> getSections() { return sections; }
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