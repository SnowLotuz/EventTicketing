package com.capstone.eventticketing.ui.tickets;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
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

    // --- MỚI: Phân loại vé bằng MediatorLiveData ---
    private final MediatorLiveData<java.util.List<TicketRepository.TicketWithMovie>> activeTickets =
            new MediatorLiveData<>();
    private final MediatorLiveData<java.util.List<TicketRepository.TicketWithMovie>> usedTickets =
            new MediatorLiveData<>();

    @Nullable private ListenerRegistration registration;

    public TicketsViewModel() {
        startListening();

        activeTickets.addSource(tickets, resource -> {
            if (resource == null || resource.data == null) {
                activeTickets.setValue(null);
                return;
            }
            java.util.List<TicketRepository.TicketWithMovie> active = new java.util.ArrayList<>();
            for (TicketRepository.TicketWithMovie t : resource.data.tickets) {
                if (!t.ticket.isCheckedIn()) active.add(t);
            }
            activeTickets.setValue(active);
        });

        usedTickets.addSource(tickets, resource -> {
            if (resource == null || resource.data == null) {
                usedTickets.setValue(null);
                return;
            }
            java.util.List<TicketRepository.TicketWithMovie> used = new java.util.ArrayList<>();
            for (TicketRepository.TicketWithMovie t : resource.data.tickets) {
                if (t.ticket.isCheckedIn()) used.add(t);
            }
            usedTickets.setValue(used);
        });
    }

    public LiveData<Resource<TicketRepository.TicketResult>> getTickets() { return tickets; }

    public LiveData<java.util.List<TicketRepository.TicketWithMovie>> getActiveTickets() { return activeTickets; }
    public LiveData<java.util.List<TicketRepository.TicketWithMovie>> getUsedTickets() { return usedTickets; }

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