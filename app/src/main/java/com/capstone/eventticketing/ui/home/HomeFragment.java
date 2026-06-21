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
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.FragmentHomeBinding;
import com.capstone.eventticketing.ui.home.adapter.CarouselAdapter;
import com.capstone.eventticketing.ui.home.adapter.MovieAdapter;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayoutMediator;
import com.capstone.eventticketing.ui.detail.EventDetailActivity;
import com.capstone.eventticketing.util.EventFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class HomeFragment extends Fragment implements
        MovieAdapter.OnMovieInteractionListener,
        CarouselAdapter.OnCarouselClickListener {

    private static final long CAROUSEL_AUTO_SCROLL_MS = 4000L;
    // Genre chips. Adjust these to whatever genres you seed in Firestore.
    private static final List<String> GENRES =
            com.capstone.eventticketing.util.EventFilter.GENRES_WITH_ALL;

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    private MovieAdapter movieAdapter;
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
        movieAdapter = new MovieAdapter(this);
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvEvents.setAdapter(movieAdapter);
    }

    private void setupCarousel() {
        carouselAdapter = new CarouselAdapter(this);
        binding.vpCarousel.setAdapter(carouselAdapter);
        new TabLayoutMediator(binding.tabCarouselIndicator, binding.vpCarousel,
                (tab, position) -> { /* dots only */ }).attach();
    }

    private void setupCategoryChips() {
        for (String genre : GENRES) {
            Chip chip = new Chip(requireContext());
            chip.setText(genre);
            chip.setCheckable(true);
            chip.setChecked("All".equals(genre));
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_selector, null));
            chip.setChipStrokeWidth(0f);
            chip.setId(View.generateViewId());
            chip.setOnClickListener(v -> homeViewModel.selectCategory(genre));
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
        homeViewModel.getMovies().observe(getViewLifecycleOwner(), this::renderMovies);

        homeViewModel.getWishlistIds().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.status == Resource.Status.SUCCESS && resource.data != null) {
                movieAdapter.setWishlistedIds(new HashSet<>(resource.data));
            }
        });

        homeViewModel.getWishlistToggleState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                showToast(resource.message);
            } else if (resource.status == Resource.Status.SUCCESS) {
                homeViewModel.getWishlistIds().observe(getViewLifecycleOwner(), r -> {
                    if (r != null && r.status == Resource.Status.SUCCESS && r.data != null) {
                        movieAdapter.setWishlistedIds(new HashSet<>(r.data));
                    }
                });
            }
        });

        homeViewModel.getFilter().observe(getViewLifecycleOwner(), filter -> {
            if (filter == null) return;
            int tint = filter.hasActiveDeepFilters()
                    ? R.color.accent_blue : R.color.slate_400;
            binding.tilSearch.setEndIconTintList(
                    getResources().getColorStateList(tint, null));
        });
    }

    private void renderMovies(@Nullable Resource<List<Movie>> resource) {
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
                List<Movie> movies = resource.data;
                if (movies == null || movies.isEmpty()) {
                    binding.rvEvents.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);

                    EventFilter current = homeViewModel.getFilter().getValue();
                    boolean isFiltered = current != null
                            && (!current.query.isEmpty() || current.hasActiveDeepFilters());
                    binding.tvEmptyMessage.setText(
                            isFiltered ? "No movies match your filters" : "No movies showing");
                } else {
                    binding.rvEvents.setVisibility(View.VISIBLE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    movieAdapter.submitList(movies);
                    updateCarousel(movies);
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

    private void updateCarousel(@NonNull List<Movie> movies) {
        List<Movie> featured = movies.size() > 5 ? movies.subList(0, 5) : movies;
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
    public void onMovieClick(@NonNull Movie movie) {
        if (movie.getMovieId() == null) return;
        startActivity(EventDetailActivity.newIntent(requireContext(), movie.getMovieId()));
    }

    @Override
    public void onWishlistToggle(@NonNull Movie movie, boolean isCurrentlyWishlisted) {
        if (movie.getMovieId() == null) return;
        homeViewModel.toggleWishlist(movie.getMovieId(), !isCurrentlyWishlisted);
    }

    @Override
    public void onCarouselClick(@NonNull Movie movie) {
        if (movie.getMovieId() == null) return;
        startActivity(EventDetailActivity.newIntent(requireContext(), movie.getMovieId()));
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