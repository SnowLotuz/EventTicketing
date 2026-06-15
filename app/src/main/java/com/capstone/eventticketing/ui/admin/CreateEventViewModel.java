package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.repository.EventRepository;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.util.SeatMapGenerator;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Backs {@link CreateEventActivity}. Validates form input, assembles a complete
 * {@link Event} (including a non-null nested {@code seatMap} and {@code rating}
 * to prevent client-side null crashes), and persists via {@link EventRepository}.
 */
public class CreateEventViewModel extends ViewModel {

    private static final String TIER_STANDARD = "Standard";

    @NonNull private final EventRepository eventRepository;

    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public CreateEventViewModel() {
        this.eventRepository = new EventRepository();
    }

    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<String> getValidationError() { return validationError; }

    /**
     * Validates inputs and, if valid, builds and saves the event.
     *
     * @param eventDateMillis selected date/time in epoch millis, or -1 if unset.
     */
    public void createEvent(String title,
                            String category,
                            String venue,
                            String description,
                            String imageUrl,
                            long eventDateMillis,
                            String capacityStr,
                            String priceStr) {

        if (isBlank(title)) { validationError.setValue("Please enter an event title."); return; }
        if (isBlank(category)) { validationError.setValue("Please select a category."); return; }
        if (isBlank(venue)) { validationError.setValue("Please enter a venue."); return; }
        if (isBlank(description)) { validationError.setValue("Please enter a description."); return; }
        if (eventDateMillis <= 0) { validationError.setValue("Please pick a date and time."); return; }

        int capacity;
        try {
            capacity = Integer.parseInt(capacityStr.trim());
            if (capacity <= 0) { validationError.setValue("Capacity must be greater than 0."); return; }
        } catch (NumberFormatException e) {
            validationError.setValue("Please enter a valid capacity.");
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

        Event event = buildEvent(title, category, venue, description, imageUrl,
                eventDateMillis, capacity, basePrice);

        // Keep the parsed values for seat generation after the event is created.
        final int finalCapacity = capacity;
        final double finalBasePrice = basePrice;

        saveState.setValue(Resource.loading());
        LiveData<Resource<String>> createSource = eventRepository.createEvent(event);
        createSource.observeForever(new androidx.lifecycle.Observer<Resource<String>>() {
            @Override
            public void onChanged(Resource<String> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                createSource.removeObserver(this);

                if (resource.status != Resource.Status.SUCCESS || resource.data == null) {
                    saveState.setValue(Resource.error(
                            resource.message != null ? resource.message : "Failed to create event."));
                    return;
                }
                // Event created — now seed its seat grid.
                seedSeats(resource.data, finalCapacity, finalBasePrice);
            }
        });
    }

    /** Generates and writes the seat grid for a freshly created event. */
    private void seedSeats(@NonNull String eventId, int capacity, double basePrice) {
        List<Seat> seats = SeatMapGenerator.generate(capacity, TIER_STANDARD, basePrice);
        LiveData<Resource<Boolean>> seatSource = eventRepository.createSeatsForEvent(eventId, seats);
        seatSource.observeForever(new androidx.lifecycle.Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                seatSource.removeObserver(this);

                if (resource.status == Resource.Status.SUCCESS) {
                    saveState.setValue(Resource.success(eventId));
                } else {
                    // Event exists but seats failed — surface it so the admin can retry.
                    saveState.setValue(Resource.error(
                            "Event created, but seat setup failed: "
                                    + (resource.message != null ? resource.message : "unknown error")));
                }
            }
        });
    }

    /** Assembles a fully-populated Event. Nested objects are never left null. */
    private Event buildEvent(String title, String category, String venue, String description,
                             @Nullable String imageUrl, long eventDateMillis,
                             int capacity, double basePrice) {
        Event event = new Event();
        event.setTitle(title.trim());
        event.setCategory(category.trim());
        event.setVenue(venue.trim());
        event.setDescription(description.trim());
        event.setImageUrl(imageUrl != null ? imageUrl.trim() : "");
        event.setEventDate(new Timestamp(new Date(eventDateMillis)));
        event.setStatus(Event.STATUS_UPCOMING);

        // Nested seatMap with a single base tier — guarantees getLowestPrice() works.
        Event.SeatMap seatMap = new Event.SeatMap();
        seatMap.setTotalCapacity(capacity);
        Map<String, Double> tiers = new HashMap<>();
        tiers.put(TIER_STANDARD, basePrice);
        seatMap.setPricingTiers(tiers);
        event.setSeatMap(seatMap);

        // Initialize rating so detail/home screens never read a null rating.
        Event.Rating rating = new Event.Rating();
        rating.setAverageScore(0d);
        rating.setTotalReviews(0);
        event.setRating(rating);

        return event;
    }

    private boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}