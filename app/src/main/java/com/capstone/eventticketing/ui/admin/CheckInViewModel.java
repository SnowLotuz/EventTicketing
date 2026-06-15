package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.CheckInResult;
import com.capstone.eventticketing.data.repository.CheckInRepository;
import com.capstone.eventticketing.util.Resource;

/**
 * Backs {@link CheckInActivity}. Receives the event scope via {@link Factory},
 * runs each scan through the transactional validation, and guards against the
 * same payload being processed repeatedly while its result is still on screen.
 */
public class CheckInViewModel extends ViewModel {

    @NonNull private final CheckInRepository checkInRepository;
    @NonNull private final String eventId;

    private final MutableLiveData<Resource<CheckInResult>> checkInState = new MutableLiveData<>();

    /** True while a scan is being validated, to ignore duplicate decodes. */
    private boolean isProcessing = false;
    /** The last payload processed, to debounce the continuous scanner. */
    private String lastPayload = null;

    public CheckInViewModel(@NonNull CheckInRepository checkInRepository, @NonNull String eventId) {
        this.checkInRepository = checkInRepository;
        this.eventId = eventId;
    }

    public LiveData<Resource<CheckInResult>> getCheckInState() { return checkInState; }

    /**
     * Submits a scanned payload. Ignored if an identical payload is already being
     * processed or was just processed (the continuous scanner fires rapidly).
     */
    public void onScan(@NonNull String payload) {
        if (isProcessing) return;
        if (payload.equals(lastPayload)) return; // debounce repeated decodes of the same code
        isProcessing = true;
        lastPayload = payload;

        LiveData<Resource<CheckInResult>> source = checkInRepository.checkIn(eventId, payload);
        source.observeForever(new Observer<Resource<CheckInResult>>() {
            @Override
            public void onChanged(Resource<CheckInResult> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                checkInState.setValue(resource);
                isProcessing = false;
            }
        });
    }

    /** Clears the debounce so the same ticket can be re-scanned after the result clears. */
    public void readyForNextScan() {
        lastPayload = null;
    }

    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String eventId;

        public Factory(@NonNull String eventId) {
            this.eventId = eventId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CheckInViewModel.class)) {
                return (T) new CheckInViewModel(new CheckInRepository(), eventId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}