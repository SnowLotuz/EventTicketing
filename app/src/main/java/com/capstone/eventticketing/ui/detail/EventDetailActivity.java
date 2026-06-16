package com.capstone.eventticketing.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Event;
import com.capstone.eventticketing.databinding.ActivityEventDetailBinding;
import com.capstone.eventticketing.util.Resource;
import com.capstone.eventticketing.ui.seat.SeatSelectionActivity;
import com.capstone.eventticketing.ui.rating.RatingDialogFragment;
import com.capstone.eventticketing.ui.rating.RatingViewModel;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Event details screen. Pure View: reads the {@code eventId} extra, hands it to
 * {@link EventDetailViewModel} via its Factory, and renders observed state.
 * No Firestore logic lives here.
 */
public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEEE, dd MMMM yyyy · h:mm a", Locale.getDefault());

    private ActivityEventDetailBinding binding;
    private EventDetailViewModel viewModel;

    private boolean isWishlisted = false;
    private boolean isDescriptionExpanded = false;

    private RatingViewModel ratingViewModel;
    private String currentUserName = "";

    // Thêm adapter cho Step 12
    private ReviewAdapter reviewAdapter;

    /** Type-safe launcher used by callers (e.g. HomeFragment). */
    public static Intent newIntent(@NonNull Context context, @NonNull String eventId) {
        Intent intent = new Intent(context, EventDetailActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new EventDetailViewModel.Factory(eventId))
                .get(EventDetailViewModel.class);

        ratingViewModel = new ViewModelProvider(this).get(RatingViewModel.class);
        observeRating();

        com.google.firebase.auth.FirebaseUser fbUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null && fbUser.getDisplayName() != null) {
            currentUserName = fbUser.getDisplayName();
        }

        reviewAdapter = new ReviewAdapter();
        binding.rvReviews.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvReviews.setNestedScrollingEnabled(false);
        binding.rvReviews.setAdapter(reviewAdapter);

        setupToolbar();
        setupListeners();
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.menu_event_detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_wishlist) {
                viewModel.toggleWishlist(isWishlisted);
                return true;
            }
            return false;
        });
    }

    private void setupListeners() {
        binding.tvReadMore.setOnClickListener(v -> toggleDescription());

        binding.btnBuyTickets.setOnClickListener(v -> {
            Resource<Event> current = viewModel.getEvent().getValue();
            if (current != null && current.status == Resource.Status.SUCCESS
                    && current.data != null && current.data.getEventId() != null) {
                startActivity(SeatSelectionActivity.newIntent(this, current.data.getEventId()));
            }
        });
    }

    private void observeViewModel() {
        viewModel.getEvent().observe(this, this::renderEvent);

        viewModel.getWishlistState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                isWishlisted = resource.data;
                android.view.MenuItem item = binding.toolbar.getMenu().findItem(R.id.action_wishlist);
                if (item != null) {
                    item.setIcon(isWishlisted ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                }
            } else if (resource.status == Resource.Status.ERROR) {
                showToast(resource.message);
            }
        });

        // Listen to Reviews list
        viewModel.getReviews().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                renderReviews(resource.data);
            }
        });
    }

    private void renderEvent(Resource<Event> resource) {
        if (resource == null) return;
        switch (resource.status) {
            case LOADING:
                binding.shimmerDetail.setVisibility(View.VISIBLE);
                binding.shimmerDetail.startShimmer();
                binding.contentContainer.setVisibility(View.INVISIBLE);
                binding.layoutError.setVisibility(View.GONE);
                binding.bottomBar.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.shimmerDetail.stopShimmer();
                binding.shimmerDetail.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.GONE);
                binding.contentContainer.setVisibility(View.VISIBLE);
                binding.bottomBar.setVisibility(View.VISIBLE);
                bindEvent(resource.data);
                break;
            case ERROR:
                binding.shimmerDetail.stopShimmer();
                binding.shimmerDetail.setVisibility(View.GONE);
                binding.contentContainer.setVisibility(View.INVISIBLE);
                binding.bottomBar.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.VISIBLE);
                binding.tvErrorMessage.setText(resource.message);
                break;
        }
    }

    private void bindEvent(Event event) {
        if (event == null) return;

        binding.collapsingToolbar.setTitle(event.getTitle());
        binding.tvTitle.setText(event.getTitle());
        binding.chipCategory.setText(event.getCategory());
        binding.tvVenue.setText(event.getVenue());
        binding.tvDescription.setText(event.getDescription());

        if (event.getEventDate() != null) {
            binding.tvDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
        }

        // Rating (Small row near title)
        if (event.getRating() != null && event.getRating().getTotalReviews() > 0) {
            binding.tvRating.setText(String.format(Locale.getDefault(), "%.1f (%d reviews)",
                    event.getRating().getAverageScore(), event.getRating().getTotalReviews()));
            binding.icStar.setVisibility(View.VISIBLE);
            binding.tvRating.setVisibility(View.VISIBLE);
        } else {
            binding.tvRating.setText(R.string.detail_no_reviews);
        }

        // Reviews-section aggregate (live averageScore maintained by the rating transaction).
        if (event.getRating() != null && event.getRating().getTotalReviews() > 0) {
            binding.tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f",
                    event.getRating().getAverageScore()));
            int count = event.getRating().getTotalReviews();
            binding.tvReviewCount.setText(getResources().getQuantityString(
                    R.plurals.review_count, count, count));
            binding.layoutRatingSummary.setVisibility(View.VISIBLE);
        } else {
            binding.layoutRatingSummary.setVisibility(View.GONE);
        }

        // Price
        if (event.getSeatMap() != null && event.getSeatMap().getLowestPrice() > 0) {
            binding.tvPrice.setText(String.format(Locale.getDefault(),
                    "$%.2f", event.getSeatMap().getLowestPrice()));
        } else {
            binding.tvPrice.setText(R.string.price_free);
        }

        // Post-event rating: prompt if eligible (ENDED + attended + not yet reviewed).
        if (event.isEnded()) {
            ratingViewModel.checkEligibility(event.getEventId(), true);
        }

        Glide.with(this)
                .load(event.getImageUrl())
                .placeholder(R.color.slate_200)
                .error(R.color.slate_200)
                .centerCrop()
                .into(binding.ivHeroImage);
    }

    // Render reviews list
    private void renderReviews(@NonNull java.util.List<com.capstone.eventticketing.data.model.Review> reviews) {
        if (reviews.isEmpty()) {
            binding.rvReviews.setVisibility(View.GONE);
            binding.tvNoReviews.setVisibility(View.VISIBLE);
        } else {
            binding.rvReviews.setVisibility(View.VISIBLE);
            binding.tvNoReviews.setVisibility(View.GONE);
            reviewAdapter.submitList(reviews);
        }
    }

    private void toggleDescription() {
        isDescriptionExpanded = !isDescriptionExpanded;
        if (isDescriptionExpanded) {
            binding.tvDescription.setMaxLines(Integer.MAX_VALUE);
            binding.tvReadMore.setText(R.string.detail_read_less);
        } else {
            binding.tvDescription.setMaxLines(4);
            binding.tvReadMore.setText(R.string.detail_read_more);
        }
    }

    private void showToast(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void observeRating() {
        ratingViewModel.getShouldPrompt().observe(this, shouldPrompt -> {
            if (Boolean.TRUE.equals(shouldPrompt)) {
                RatingDialogFragment dialog = RatingDialogFragment.newInstance(
                        getIntent().getStringExtra(EXTRA_EVENT_ID), currentUserName);

                // Thay đổi cho Step 12: Reload cả Event và Reviews
                dialog.setOnReviewSubmittedListener(() -> {
                    viewModel.loadEvent();    // refreshes the aggregate average
                    viewModel.loadReviews();  // refreshes the visible list
                });

                dialog.show(getSupportFragmentManager(), "rating_dialog");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}