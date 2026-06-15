package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.repository.EventRepository;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link AdminEventsActivity}. Lists all events (reusing the shared
 * repository query) and exposes delete. A refresh trigger re-runs the query
 * after a mutation so the list reflects deletes/edits without a manual reload.
 */
public class AdminEventsViewModel extends ViewModel {

    @NonNull private final EventRepository eventRepository = new EventRepository();

    private final MutableLiveData<Integer> refreshTrigger = new MutableLiveData<>(0);
    private final LiveData<Resource<List<Event>>> events;

    private final MutableLiveData<Resource<Boolean>> deleteState = new MutableLiveData<>();

    public AdminEventsViewModel() {
        // Re-query whenever the refresh trigger changes (initial load + post-mutation).
        events = Transformations.switchMap(refreshTrigger, t -> eventRepository.getEvents(null));
    }

    public LiveData<Resource<List<Event>>> getEvents() { return events; }
    public LiveData<Resource<Boolean>> getDeleteState() { return deleteState; }

    public void refresh() {
        Integer current = refreshTrigger.getValue();
        refreshTrigger.setValue(current == null ? 1 : current + 1);
    }

    public void deleteEvent(@NonNull String eventId) {
        deleteState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = eventRepository.deleteEvent(eventId);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                deleteState.setValue(resource);
                if (resource.status == Resource.Status.SUCCESS) {
                    refresh(); // reload the list after a successful delete
                }
            }
        });
    }
}