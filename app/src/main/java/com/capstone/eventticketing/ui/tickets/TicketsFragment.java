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
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Digital Wallet. Lists the user's tickets (served from the offline cache when
 * there's no connectivity) and opens a full-screen, max-brightness QR on tap.
 * View-only: the live subscription and its lifecycle live in {@link TicketsViewModel}.
 */
public class TicketsFragment extends Fragment implements TicketAdapter.OnTicketClickListener {

    private FragmentTicketsBinding binding;
    private TicketsViewModel viewModel;
    private TicketAdapter adapter;

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
        binding.rvTickets.setAdapter(adapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getTickets().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmerTickets.setVisibility(View.VISIBLE);
                    binding.shimmerTickets.startShimmer();
                    binding.rvTickets.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmerTickets.stopShimmer();
                    binding.shimmerTickets.setVisibility(View.GONE);
                    renderTickets(resource.data);
                    break;
                case ERROR:
                    binding.shimmerTickets.stopShimmer();
                    binding.shimmerTickets.setVisibility(View.GONE);
                    binding.rvTickets.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void renderTickets(@Nullable TicketRepository.TicketResult result) {
        if (result == null) return;

        binding.bannerOffline.setVisibility(result.isFromCache ? View.VISIBLE : View.GONE);

        List<TicketRepository.TicketWithMovie> tickets = result.tickets;
        if (tickets.isEmpty()) {
            binding.rvTickets.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.rvTickets.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(tickets);
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