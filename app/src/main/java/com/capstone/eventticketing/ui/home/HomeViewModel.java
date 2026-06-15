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
 * Backs {@link HomeFragment}. Fetches the full event catalog once, then applies
 * search and deep filters in memory via {@link EventFilter}. This architecture
 * allows real-time substring search and multi-criteria filtering without
 * requiring complex, restrictive Firestore composite indexes.
 */
public class HomeViewModel extends ViewModel {

    @NonNull private final EventRepository eventRepository = new EventRepository();

    // The single source of truth for the raw, unfiltered catalog.
    private final LiveData<Resource<List<Event>>> rawEvents;

    // The active filter criteria.
    private final MutableLiveData<EventFilter> activeFilter = new MutableLiveData<>(
            new EventFilter(null, null, 0L, Long.MAX_VALUE, Double.MAX_VALUE));

    // The combined result: rawEvents passed through activeFilter.
    private final MediatorLiveData<Resource<List<Event>>> filteredEvents = new MediatorLiveData<>();

    private final MutableLiveData<Resource<Boolean>> wishlistToggleState = new MutableLiveData<>();

    public HomeViewModel() {
        // Fetch all upcoming events exactly once.
        rawEvents = eventRepository.getEvents(null);

        // Recompute the filtered list whenever the raw data OR the filter changes.
        filteredEvents.addSource(rawEvents, res -> applyFilter(res, activeFilter.getValue()));
        filteredEvents.addSource(activeFilter, filter -> applyFilter(rawEvents.getValue(), filter));
    }

    public LiveData<Resource<List<Event>>> getEvents() { return filteredEvents; }
    public LiveData<Resource<Boolean>> getWishlistToggleState() { return wishlistToggleState; }
    public EventFilter getActiveFilter() { return activeFilter.getValue(); }

    /** Replaces the current filter, triggering an immediate in-memory recompute. */
    public void setFilter(@NonNull EventFilter filter) {
        activeFilter.setValue(filter);
    }

    private void applyFilter(Resource<List<Event>> dataRes, EventFilter filter) {
        if (dataRes == null) return;
        if (dataRes.status != Resource.Status.SUCCESS || dataRes.data == null) {
            // Pass loading/error states through directly.
            filteredEvents.setValue(dataRes);
            return;
        }
        if (filter == null) {
            filteredEvents.setValue(dataRes);
            return;
        }
        // Success + Filter: apply the math.
        List<Event> result = filter.apply(dataRes.data);
        filteredEvents.setValue(Resource.success(result));
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