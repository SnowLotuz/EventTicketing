package com.capstone.eventticketing.ui.detail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.data.repository.EventRepository;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.data.model.Review;
import com.capstone.eventticketing.data.repository.ReviewRepository;

import java.util.List;

/**
 * Backs {@link EventDetailActivity}. Receives its {@code eventId} at construction
 * (via {@link Factory}) so the View never passes Intent extras into business
 * logic. Owns the event fetch and the wishlist state for this single event.
 */
public class EventDetailViewModel extends ViewModel {

    @NonNull private final EventRepository eventRepository;
    @NonNull private final String eventId;
    // Bỏ vào phần khai báo biến:
    @NonNull private final ReviewRepository reviewRepository = new ReviewRepository();

    private final MutableLiveData<Resource<Event>> event = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> wishlistState = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Review>>> reviews = new MutableLiveData<>();

    public EventDetailViewModel(@NonNull EventRepository eventRepository, @NonNull String eventId) {
        this.eventRepository = eventRepository;
        this.eventId = eventId;
        loadEvent();
        loadWishlistState();
    }

    public LiveData<Resource<Event>> getEvent() { return event; }
    public LiveData<Resource<Boolean>> getWishlistState() { return wishlistState; }
    public LiveData<Resource<List<Review>>> getReviews() { return reviews; }

    /** Loads (or reloads, on retry) the event document. */
    public void loadEvent() {
        forward(eventRepository.getEventById(eventId), event);
    }

    private void loadWishlistState() {
        forward(eventRepository.isEventWishlisted(eventId), wishlistState);
    }

    /** Loads (or reloads, after a new review) the reviews for this event. */
    public void loadReviews() {
        forward(reviewRepository.getReviewsForEvent(eventId), reviews);
    }

    /**
     * Toggles wishlist membership. {@code currentlyWishlisted} is the state the
     * user sees; we write the opposite, then optimistically reflect it.
     */
    public void toggleWishlist(boolean currentlyWishlisted) {
        boolean add = !currentlyWishlisted;
        LiveData<Resource<Boolean>> source = eventRepository.toggleWishlist(eventId, add);
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

    /** Bridges a one-shot repository LiveData into a stable ViewModel-owned LiveData. */
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

    /**
     * Factory that injects the repository and the {@code eventId} from the Intent
     * into the ViewModel, keeping construction testable and the View thin.
     */
    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String eventId;

        public Factory(@NonNull String eventId) {
            this.eventId = eventId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(EventDetailViewModel.class)) {
                return (T) new EventDetailViewModel(new EventRepository(), eventId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}