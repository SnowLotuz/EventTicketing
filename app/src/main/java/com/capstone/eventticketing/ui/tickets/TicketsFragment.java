package com.capstone.eventticketing.ui.tickets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.data.model.Ticket;
import com.capstone.eventticketing.data.repository.TicketRepository;
import com.capstone.eventticketing.databinding.FragmentTicketsBinding;

import java.util.List;

/**
 * Digital Wallet. Lists the user's tickets (served from the offline cache when
 * there's no connectivity) and opens a full-screen, max-brightness QR on tap.
 * View-only: the live subscription and its lifecycle live in {@link TicketsViewModel}.
 */
public class TicketsFragment extends Fragment implements TicketAdapter.OnTicketClickListener {

    private FragmentTicketsBinding binding;
    private TicketsViewModel viewModel;

    private TicketAdapter adapter;      // Adapter cho vé Active
    private TicketAdapter usedAdapter;  // Adapter cho vé Used

    public TicketsFragment() { super(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTicketsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TicketsViewModel.class);

        adapter = new TicketAdapter(this);
        binding.rvTickets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTickets.setNestedScrollingEnabled(false);
        binding.rvTickets.setAdapter(adapter);

        usedAdapter = new TicketAdapter(this);
        binding.rvUsedTickets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsedTickets.setNestedScrollingEnabled(false);
        binding.rvUsedTickets.setAdapter(usedAdapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getTickets().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmerTickets.setVisibility(View.VISIBLE);
                    binding.shimmerTickets.startShimmer();
                    binding.scrollTickets.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmerTickets.stopShimmer();
                    binding.shimmerTickets.setVisibility(View.GONE);
                    if (resource.data != null) {
                        binding.bannerOffline.setVisibility(resource.data.isFromCache ? View.VISIBLE : View.GONE);
                    }
                    updateEmptyState();
                    break;
                case ERROR:
                    binding.shimmerTickets.stopShimmer();
                    binding.shimmerTickets.setVisibility(View.GONE);
                    binding.scrollTickets.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    break;
            }
        });

        viewModel.getActiveTickets().observe(getViewLifecycleOwner(), tickets -> {
            if (tickets == null) return;
            adapter.submitList(tickets);

            boolean empty = tickets.isEmpty();
            binding.tvActiveLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.rvTickets.setVisibility(empty ? View.GONE : View.VISIBLE);

            updateEmptyState();
        });

        viewModel.getUsedTickets().observe(getViewLifecycleOwner(), tickets -> {
            if (tickets == null) return;
            usedAdapter.submitList(tickets);

            boolean empty = tickets.isEmpty();
            binding.tvUsedLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.rvUsedTickets.setVisibility(empty ? View.GONE : View.VISIBLE);

            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        List<?> active = viewModel.getActiveTickets().getValue();
        List<?> used = viewModel.getUsedTickets().getValue();

        boolean isActiveEmpty = (active == null || active.isEmpty());
        boolean isUsedEmpty = (used == null || used.isEmpty());

        if (isActiveEmpty && isUsedEmpty) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            if (binding.shimmerTickets.getVisibility() == View.GONE) {
                binding.scrollTickets.setVisibility(View.GONE);
            }
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            if (binding.shimmerTickets.getVisibility() == View.GONE) {
                binding.scrollTickets.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onTicketClick(@NonNull Ticket ticket) {
        if (ticket.getQrCodeData() == null) return;
        QrDialogFragment.newInstance(ticket.getQrCodeData(),
                        ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "")
                .show(getChildFragmentManager(), "qr_dialog");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}