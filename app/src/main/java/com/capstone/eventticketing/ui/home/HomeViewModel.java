package com.capstone.eventticketing.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.repository.EventRepository;
import com.capstone.eventticketing.util.EventFilter;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link HomeFragment}. Loads all events once and exposes a filtered view
 * derived from the current {@link EventFilter} (real-time title search + deep
 * filters). Filtering is in-memory; see EventFilter for the rationale.
 */
public class HomeViewModel extends ViewModel {

    @NonNull private final EventRepository eventRepository;

    private final MutableLiveData<Integer> refreshTrigger = new MutableLiveData<>(0);
    private final LiveData<Resource<List<Event>>> rawEvents;

    private final MutableLiveData<EventFilter> filter = new MutableLiveData<>(EventFilter.none());

    /** The list the UI renders: rawEvents passed through the active filter. */
    private final MediatorLiveData<Resource<List<Event>>> filteredEvents = new MediatorLiveData<>();

    private final MutableLiveData<Resource<Boolean>> wishlistToggleState = new MutableLiveData<>();

    public HomeViewModel() {
        this.eventRepository = new EventRepository();
        // Always fetch the full catalog; category is handled by the in-memory filter.
        this.rawEvents = androidx.lifecycle.Transformations.switchMap(
                refreshTrigger, t -> eventRepository.getMovies(null));

        // Recompute filteredEvents whenever either the data or the filter changes.
        filteredEvents.addSource(rawEvents, r -> recompute());
        filteredEvents.addSource(filter, f -> recompute());
    }

    private void recompute() {
        Resource<List<Event>> r = rawEvents.getValue();
        EventFilter f = filter.getValue();
        if (r == null) return;
        if (r.status != Resource.Status.SUCCESS) {
            filteredEvents.setValue(r); // propagate Loading / Error as-is
            return;
        }
        List<Event> filtered = (f != null ? f : EventFilter.none()).apply(r.data);
        filteredEvents.setValue(Resource.success(filtered));
    }

    public LiveData<Resource<List<Event>>> getMovies() { return filteredEvents; }
    public LiveData<EventFilter> getFilter() { return filter; }
    public LiveData<Resource<Boolean>> getWishlistToggleState() { return wishlistToggleState; }

    public LiveData<Resource<List<String>>> getWishlistIds() {
        return eventRepository.getWishlistIds();
    }

    /** Real-time search: updates only the query, preserving deep filters. */
    public void setSearchQuery(@NonNull String query) {
        EventFilter current = filter.getValue();
        filter.setValue((current != null ? current : EventFilter.none()).withQuery(query));
    }

    /** Category chip selection feeds into the same filter object. */
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

    public void toggleWishlist(@NonNull String eventId, boolean add) {
        LiveData<Resource<Boolean>> source = eventRepository.toggleWishlist(eventId, add);
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