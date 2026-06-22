package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.repository.BookingRepository;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;

/**
 * Backs {@link AdminDashboardActivity}. Loads the two top-level KPIs — total
 * movie count and confirmed-booking revenue — from their respective
 * repositories. No Firestore access here; the Activity only observes state.
 */
public class DashboardViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository = new MovieRepository();
    @NonNull private final BookingRepository bookingRepository = new BookingRepository();

    private final MutableLiveData<Resource<Integer>> movieCount = new MutableLiveData<>();
    private final MutableLiveData<Resource<Double>> revenue = new MutableLiveData<>();

    public LiveData<Resource<Integer>> getMovieCount() { return movieCount; }
    public LiveData<Resource<Double>> getRevenue() { return revenue; }

    /** Loads (or reloads) both KPIs. Call from the Activity's onResume so the
     *  numbers refresh after creating a movie or completing a booking. */
    public void load() {
        forward(movieRepository.getMovieCount(), movieCount);
        forward(bookingRepository.getTotalRevenue(), revenue);
    }

    private <T> void forward(@NonNull LiveData<Resource<T>> source,
                             @NonNull MutableLiveData<Resource<T>> target) {
        target.setValue(Resource.loading());
        source.observeForever(new Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                target.setValue(r);
            }
        });
    }
}