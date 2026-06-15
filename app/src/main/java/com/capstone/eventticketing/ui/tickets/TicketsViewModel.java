package com.capstone.eventticketing.ui.tickets;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.repository.TicketRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Backs {@link TicketsFragment}. Subscribes to the user's live ticket stream and
 * owns the listener lifecycle so it is torn down exactly once when the screen
 * is gone. No Firestore access here.
 */
public class TicketsViewModel extends ViewModel {

    private final TicketRepository ticketRepository = new TicketRepository();

    private final MutableLiveData<Resource<TicketRepository.TicketResult>> tickets =
            new MutableLiveData<>();

    @Nullable private ListenerRegistration registration;

    public TicketsViewModel() {
        startListening();
    }

    public LiveData<Resource<TicketRepository.TicketResult>> getTickets() { return tickets; }

    private void startListening() {
        registration = ticketRepository.listenToMyTickets(tickets);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}