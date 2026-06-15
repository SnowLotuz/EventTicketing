package com.capstone.eventticketing.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.data.repository.BookingRepository;
import com.capstone.eventticketing.databinding.ActivityBookingHistoryBinding;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Shows the current user's booking history. View-only: data work is in
 * {@link BookingHistoryViewModel}.
 */
public class BookingHistoryActivity extends AppCompatActivity {

    private ActivityBookingHistoryBinding binding;
    private BookingHistoryViewModel viewModel;
    private BookingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBookingHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(BookingHistoryViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new BookingAdapter();
        binding.rvBookings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBookings.setAdapter(adapter);

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getBookings().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmer.setVisibility(View.VISIBLE);
                    binding.shimmer.startShimmer();
                    binding.rvBookings.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    render(resource.data);
                    break;
                case ERROR:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    binding.rvBookings.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    private void render(List<BookingRepository.BookingWithEvent> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            binding.rvBookings.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.rvBookings.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(bookings);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}