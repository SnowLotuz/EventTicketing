package com.capstone.eventticketing.ui.rating;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.repository.ReviewRepository;
import com.capstone.eventticketing.util.Resource;

/**
 * Drives the post-event rating prompt and submission. Computes eligibility
 * (attended + not yet reviewed) and submits via {@link ReviewRepository}.
 */
public class RatingViewModel extends ViewModel {

    @NonNull private final ReviewRepository reviewRepository = new ReviewRepository();

    private final MutableLiveData<Boolean> shouldPrompt = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> submitState = new MutableLiveData<>();

    public LiveData<Boolean> getShouldPrompt() { return shouldPrompt; }
    public LiveData<Resource<Boolean>> getSubmitState() { return submitState; }

    /**
     * Resolves whether to show the rating prompt: only for an ENDED event the
     * user attended and hasn't already reviewed.
     */
    public void checkEligibility(@NonNull String eventId, boolean isEnded) {
        if (!isEnded) {
            shouldPrompt.setValue(false);
            return;
        }
        LiveData<Resource<Boolean>> attended = reviewRepository.hasAttended(eventId);
        attended.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                attended.removeObserver(this);
                if (r.status != Resource.Status.SUCCESS || r.data == null || !r.data) {
                    shouldPrompt.setValue(false);
                    return;
                }
                // Attended — now ensure they haven't already reviewed.
                checkNotYetReviewed(eventId);
            }
        });
    }

    private void checkNotYetReviewed(@NonNull String eventId) {
        LiveData<Resource<Boolean>> reviewed = reviewRepository.hasUserReviewed(eventId);
        reviewed.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                reviewed.removeObserver(this);
                boolean alreadyReviewed = r.status == Resource.Status.SUCCESS
                        && r.data != null && r.data;
                shouldPrompt.setValue(!alreadyReviewed);
            }
        });
    }

    public void submit(@NonNull String eventId, @NonNull String userName,
                       int rating, @NonNull String comment) {
        submitState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source =
                reviewRepository.submitReview(eventId, userName, rating, comment);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                submitState.setValue(r);
            }
        });
    }
}