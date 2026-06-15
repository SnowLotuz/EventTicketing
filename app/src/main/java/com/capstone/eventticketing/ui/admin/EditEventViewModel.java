package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.repository.EventRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Backs {@link EditEventActivity}. Loads an event for pre-fill and updates its
 * mutable fields. Capacity/pricing are deliberately not editable to protect
 * existing seats and bookings.
 */
public class EditEventViewModel extends ViewModel {

    @NonNull private final EventRepository eventRepository;
    @NonNull private final String eventId;

    private final MutableLiveData<Resource<Event>> event = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> updateState = new MutableLiveData<>();
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public EditEventViewModel(@NonNull EventRepository eventRepository, @NonNull String eventId) {
        this.eventRepository = eventRepository;
        this.eventId = eventId;
        load();
    }

    public LiveData<Resource<Event>> getEvent() { return event; }
    public LiveData<Resource<Boolean>> getUpdateState() { return updateState; }
    public LiveData<String> getValidationError() { return validationError; }

    public void load() {
        event.setValue(Resource.loading());
        LiveData<Resource<Event>> source = eventRepository.getEventById(eventId);
        source.observeForever(new Observer<Resource<Event>>() {
            @Override
            public void onChanged(Resource<Event> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                event.setValue(r);
            }
        });
    }

    public void save(String title, String category, String venue, String description,
                     @Nullable String imageUrl, long eventDateMillis, String status) {
        if (isBlank(title)) { validationError.setValue("Title cannot be empty."); return; }
        if (isBlank(category)) { validationError.setValue("Please select a category."); return; }
        if (isBlank(venue)) { validationError.setValue("Venue cannot be empty."); return; }
        if (isBlank(description)) { validationError.setValue("Description cannot be empty."); return; }
        if (eventDateMillis <= 0) { validationError.setValue("Please pick a date and time."); return; }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title.trim());
        updates.put("category", category.trim());
        updates.put("venue", venue.trim());
        updates.put("description", description.trim());
        updates.put("imageUrl", imageUrl != null ? imageUrl.trim() : "");
        updates.put("eventDate", new Timestamp(new Date(eventDateMillis)));
        updates.put("status", status);

        updateState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = eventRepository.updateEvent(eventId, updates);
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
        @NonNull private final String eventId;
        public Factory(@NonNull String eventId) { this.eventId = eventId; }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(EditEventViewModel.class)) {
                return (T) new EditEventViewModel(new EventRepository(), eventId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}