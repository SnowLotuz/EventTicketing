package com.capstone.eventticketing.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.databinding.FragmentHomeBinding;
import com.capstone.eventticketing.ui.home.adapter.CarouselAdapter;
import com.capstone.eventticketing.ui.home.adapter.EventAdapter;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayoutMediator;
import com.capstone.eventticketing.ui.detail.EventDetailActivity;
import com.capstone.eventticketing.util.EventFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class HomeFragment extends Fragment implements
        EventAdapter.OnEventInteractionListener,
        CarouselAdapter.OnCarouselClickListener {

    private static final long CAROUSEL_AUTO_SCROLL_MS = 4000L;
    private static final List<String> CATEGORIES =
            Arrays.asList("All", "Music", "Sports", "Theater");

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    private EventAdapter eventAdapter;
    private CarouselAdapter carouselAdapter;

    private final Handler carouselHandler = new Handler(Looper.getMainLooper());
    private Runnable carouselRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupRecyclerView();
        setupCarousel();
        setupCategoryChips();
        setupSearch();
        observeViewModel();
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(this);
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvEvents.setAdapter(eventAdapter);
    }

    private void setupCarousel() {
        carouselAdapter = new CarouselAdapter(this);
        binding.vpCarousel.setAdapter(carouselAdapter);
        new TabLayoutMediator(binding.tabCarouselIndicator, binding.vpCarousel,
                (tab, position) -> { /* dots only */ }).attach();
    }

    private void setupCategoryChips() {
        for (String category : CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setText(category);
            chip.setCheckable(true);
            chip.setChecked("All".equals(category));
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_selector, null));
            chip.setChipStrokeWidth(0f);
            chip.setId(View.generateViewId());
            // Nguyên bản của Claude: gọi hàm selectCategory, không cần chế gì thêm
            chip.setOnClickListener(v -> homeViewModel.selectCategory(category));
            binding.chipGroupCategories.addView(chip);
        }
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                homeViewModel.setSearchQuery(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        // The search field's end icon opens the deep-filter sheet.
        binding.tilSearch.setEndIconMode(
                com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
        binding.tilSearch.setEndIconDrawable(R.drawable.ic_filter);
        binding.tilSearch.setEndIconOnClickListener(v -> openFilterSheet());
    }

    private void openFilterSheet() {
        FilterBottomSheet sheet = new FilterBottomSheet();
        EventFilter current = homeViewModel.getFilter().getValue();
        sheet.setInitialFilter(current != null ? current : EventFilter.none());
        sheet.setOnFilterAppliedListener(homeViewModel::applyFilter);
        sheet.show(getChildFragmentManager(), "filter_sheet");
    }

    private void observeViewModel() {
        homeViewModel.getEvents().observe(getViewLifecycleOwner(), this::renderEvents);

        homeViewModel.getWishlistIds().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                eventAdapter.setWishlistedIds(new HashSet<>(resource.data));
            }
        });

        homeViewModel.getWishlistToggleState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                showToast(resource.message);
            } else if (resource.status == Resource.Status.SUCCESS) {
                homeViewModel.getWishlistIds().observe(getViewLifecycleOwner(), r -> {
                    if (r != null && r.status == Resource.Status.SUCCESS && r.data != null) {
                        eventAdapter.setWishlistedIds(new HashSet<>(r.data));
                    }
                });
            }
        });

        homeViewModel.getFilter().observe(getViewLifecycleOwner(), filter -> {
            if (filter == null) return;
            // Tint the filter icon when deep filters are active, as a visual cue.
            int tint = filter.hasActiveDeepFilters()
                    ? R.color.accent_blue : R.color.slate_400;
            binding.tilSearch.setEndIconTintList(
                    getResources().getColorStateList(tint, null));
        });
    }

    private void renderEvents(@Nullable Resource<List<Event>> resource) {
        if (resource == null) return;
        switch (resource.status) {
            case LOADING:
                binding.shimmerLayout.setVisibility(View.VISIBLE);
                binding.shimmerLayout.startShimmer();
                binding.rvEvents.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.shimmerLayout.stopShimmer();
                binding.shimmerLayout.setVisibility(View.GONE);
                List<Event> events = resource.data;
                if (events == null || events.isEmpty()) {
                    binding.rvEvents.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);

                    // Logic đổi câu thông báo của Claude
                    EventFilter current = homeViewModel.getFilter().getValue();
                    boolean isFiltered = current != null && (!current.query.isEmpty() || current.hasActiveDeepFilters());
                    binding.tvEmptyMessage.setText(isFiltered ? "No events match your filters" : "No upcoming events");
                } else {
                    binding.rvEvents.setVisibility(View.VISIBLE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    eventAdapter.submitList(events);
                    updateCarousel(events);
                }
                break;
            case ERROR:
                binding.shimmerLayout.stopShimmer();
                binding.shimmerLayout.setVisibility(View.GONE);
                binding.rvEvents.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
                binding.tvEmptyMessage.setText(resource.message);
                showToast(resource.message);
                break;
        }
    }

    private void updateCarousel(@NonNull List<Event> events) {
        List<Event> featured = events.size() > 5 ? events.subList(0, 5) : events;
        carouselAdapter.submitList(featured);
        startCarouselAutoScroll(featured.size());
    }

    private void startCarouselAutoScroll(int count) {
        stopCarouselAutoScroll();
        if (count <= 1) return;
        carouselRunnable = new Runnable() {
            @Override
            public void run() {
                int next = (binding.vpCarousel.getCurrentItem() + 1) % count;
                binding.vpCarousel.setCurrentItem(next, true);
                carouselHandler.postDelayed(this, CAROUSEL_AUTO_SCROLL_MS);
            }
        };
        carouselHandler.postDelayed(carouselRunnable, CAROUSEL_AUTO_SCROLL_MS);
    }

    private void stopCarouselAutoScroll() {
        if (carouselRunnable != null) {
            carouselHandler.removeCallbacks(carouselRunnable);
        }
    }

    @Override
    public void onEventClick(@NonNull Event event) {
        if (event.getEventId() == null) return;
        startActivity(EventDetailActivity.newIntent(requireContext(), event.getEventId()));
    }

    @Override
    public void onWishlistToggle(@NonNull Event event, boolean isCurrentlyWishlisted) {
        if (event.getEventId() == null) return;
        homeViewModel.toggleWishlist(event.getEventId(), !isCurrentlyWishlisted);
    }

    @Override
    public void onCarouselClick(@NonNull Event event) {
        if (event.getEventId() == null) return;
        startActivity(EventDetailActivity.newIntent(requireContext(), event.getEventId()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (carouselAdapter != null && carouselAdapter.getItemCount() > 1) {
            startCarouselAutoScroll(carouselAdapter.getItemCount());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCarouselAutoScroll();
    }

    private void showToast(String message) {
        if (message != null && isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopCarouselAutoScroll();
        binding = null;
    }
}