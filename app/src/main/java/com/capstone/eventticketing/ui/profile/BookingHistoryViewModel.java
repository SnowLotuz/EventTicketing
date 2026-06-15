package com.capstone.eventticketing.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.repository.BookingRepository;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link BookingHistoryActivity}. Loads the current user's bookings with
 * resolved event titles. No Firestore access here.
 */
public class BookingHistoryViewModel extends ViewModel {

    @NonNull private final BookingRepository bookingRepository = new BookingRepository();

    private final MutableLiveData<Resource<List<BookingRepository.BookingWithEvent>>> bookings =
            new MutableLiveData<>();

    public BookingHistoryViewModel() {
        load();
    }

    public LiveData<Resource<List<BookingRepository.BookingWithEvent>>> getBookings() {
        return bookings;
    }

    public void load() {
        bookings.setValue(Resource.loading());
        LiveData<Resource<List<BookingRepository.BookingWithEvent>>> source =
                bookingRepository.getMyBookings();
        source.observeForever(new Observer<Resource<List<BookingRepository.BookingWithEvent>>>() {
            @Override
            public void onChanged(Resource<List<BookingRepository.BookingWithEvent>> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                bookings.setValue(r);
            }
        });
    }
}