package com.capstone.eventticketing.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.model.Review;
import com.capstone.eventticketing.databinding.ActivityEventDetailBinding;
import com.capstone.eventticketing.ui.rating.RatingDialogFragment;
import com.capstone.eventticketing.ui.rating.RatingViewModel;
import com.capstone.eventticketing.ui.seat.SeatSelectionActivity;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Movie details screen. Pure View: reads the {@code movieId} extra, hands it to
 * {@link EventDetailViewModel} via its Factory, and renders observed state.
 * Hosts the post-viewing rating prompt (for ENDED movies the user attended) and
 * gates booking so only NOW_SHOWING movies can proceed to seat selection.
 */
public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id"; // holds movieId

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());

    private ActivityEventDetailBinding binding;
    private EventDetailViewModel viewModel;

    /** The movie this screen is showing. Resolved once in onCreate and reused. */
    private String movieId;

    private boolean isWishlisted = false;
    private boolean isDescriptionExpanded = false;

    private RatingViewModel ratingViewModel;
    private String currentUserName = "";

    private ReviewAdapter reviewAdapter;
    private ActorAdapter actorAdapter;

    // --- MỚI: Thêm các biến cho phần Discussions ---
    private DiscussionAdapter discussionAdapter;
    private DiscussionViewModel discussionViewModel;
    private boolean discussionInitialized = false;

    public static Intent newIntent(@NonNull Context context, @NonNull String movieId) {
        Intent intent = new Intent(context, EventDetailActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, movieId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        movieId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (movieId == null || movieId.isEmpty()) {
            Toast.makeText(this, R.string.detail_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new EventDetailViewModel.Factory(movieId))
                .get(EventDetailViewModel.class);

        ratingViewModel = new ViewModelProvider(this).get(RatingViewModel.class);
        observeRating();

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null && fbUser.getDisplayName() != null) {
            currentUserName = fbUser.getDisplayName();
        }

        reviewAdapter = new ReviewAdapter();
        binding.rvReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReviews.setNestedScrollingEnabled(false);
        binding.rvReviews.setAdapter(reviewAdapter);

        // Khởi tạo Adapter và RecyclerView cho Cast
        actorAdapter = new ActorAdapter();
        binding.rvCast.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        binding.rvCast.setNestedScrollingEnabled(false);
        binding.rvCast.setAdapter(actorAdapter);

        discussionAdapter = new DiscussionAdapter();
        binding.rvDiscussion.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvDiscussion.setNestedScrollingEnabled(false);
        binding.rvDiscussion.setAdapter(discussionAdapter);

        binding.btnPostComment.setOnClickListener(v -> postComment());

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
            Resource<Movie> current = viewModel.getMovie().getValue();
            if (current != null && current.status == Resource.Status.SUCCESS
                    && current.data != null && current.data.getMovieId() != null
                    && Movie.STATUS_NOW_SHOWING.equals(current.data.getStatus())) {
                startActivity(SeatSelectionActivity.newIntent(this, current.data.getMovieId()));
            }
        });
    }

    private void observeViewModel() {
        viewModel.getMovie().observe(this, this::renderMovie);

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

        viewModel.getReviews().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                renderReviews(resource.data);
            } else if (resource.status == Resource.Status.ERROR) {
                // Surface query failures (e.g. a missing composite index) instead
                // of silently showing an empty reviews list.
                binding.rvReviews.setVisibility(View.GONE);
                binding.tvNoReviews.setVisibility(View.VISIBLE);
                showToast(resource.message);
            }
        });

        viewModel.getBookedSeatCount().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                Resource<Movie> m = viewModel.getMovie().getValue();
                if (m == null || m.data == null) return;
                Movie movie = m.data;

                int bookedCount = resource.data;

                // Tickets-sold line (NOW_SHOWING only) — existing behavior.
                boolean nowShowing = Movie.STATUS_NOW_SHOWING.equals(movie.getStatus());
                if (nowShowing) {
                    binding.tvTicketsSold.setVisibility(View.VISIBLE);
                    binding.tvTicketsSold.setText(getString(R.string.detail_tickets_sold,
                            bookedCount,
                            com.capstone.eventticketing.util.SeatMapGenerator.CINEMA_CAPACITY));
                }

                // Same-price discount — recompute now that the booked count is known.
                bindDiscountedPrice(movie, bookedCount);
            }
        });
    }

    private void renderMovie(Resource<Movie> resource) {
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
                bindMovie(resource.data);
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

    private void bindMovie(Movie movie) {
        if (movie == null) return;

        binding.collapsingToolbar.setTitle(movie.getTitle());
        binding.tvTitle.setText(movie.getTitle());
        binding.chipCategory.setText(movie.getGenre());
        binding.tvVenue.setText(movie.getFormattedDuration());   // duration in the old "venue" row
        binding.tvDescription.setText(movie.getDescription());

        if (movie.getReleaseDate() != null) {
            binding.tvDate.setText(getString(R.string.detail_released_on,
                    DATE_FORMAT.format(movie.getReleaseDate().toDate())));
        }

        bindRatingSummary(movie);

        // Provisional price: show the base price immediately. The booked-count
        // observer recomputes with any same-price discount once the count loads.
        if (movie.getSeatMap() != null && movie.getSeatMap().getLowestPrice() > 0) {
            binding.tvPrice.setText(String.format(Locale.getDefault(), "$%.2f",
                    movie.getSeatMap().getLowestPrice()));
        } else {
            binding.tvPrice.setText(R.string.price_free);
        }
        binding.tvPriceOriginal.setVisibility(View.GONE);

        bindBookingState(movie);

        // ENDED movies prompt the attendee for a post-viewing rating.
        if (movie.isEnded()) {
            ratingViewModel.checkEligibility(movie.getMovieId(), true);
        }

        // Cast — show the section only if the movie has actors.
        java.util.List<com.capstone.eventticketing.data.model.Actor> cast = movie.getActors();
        boolean hasCast = cast != null && !cast.isEmpty();
        binding.tvCastLabel.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        binding.rvCast.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        if (hasCast) actorAdapter.submitList(cast);

        // Trailer — show the button only if a trailer URL exists.
        binding.btnTrailer.setVisibility(movie.hasTrailer() ? View.VISIBLE : View.GONE);
        binding.btnTrailer.setOnClickListener(v -> openTrailer(movie.getTrailerUrl()));

        // Tickets-sold is shown only while NOW_SHOWING (see observer below).
        boolean showSold = Movie.STATUS_NOW_SHOWING.equals(movie.getStatus());
        binding.tvTicketsSold.setVisibility(showSold ? View.VISIBLE : View.GONE);

        loadHero(movie.getPosterUrl());

        // --- MỚI: Khởi tạo DiscussionViewModel khi đã có status của movie ---
        if (!discussionInitialized) {
            discussionInitialized = true;
            discussionViewModel = new ViewModelProvider(this,
                    new DiscussionViewModel.Factory(movie.getMovieId(), movie.getStatus()))
                    .get(DiscussionViewModel.class);
            observeDiscussion();

            // Toggle input vs closed-message by status.
            boolean open = discussionViewModel.isCommentingOpen();
            binding.tilComment.setVisibility(open ? View.VISIBLE : View.GONE);
            binding.btnPostComment.setVisibility(open ? View.VISIBLE : View.GONE);
            binding.tvDiscussionClosed.setVisibility(open ? View.GONE : View.VISIBLE);
        }
    }

    private void observeDiscussion() {
        discussionViewModel.getComments().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                discussionAdapter.submitList(resource.data);
                boolean empty = resource.data.isEmpty();
                binding.tvDiscussionEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.rvDiscussion.setVisibility(empty ? View.GONE : View.VISIBLE);
            } else if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
            }
        });

        discussionViewModel.getPostState().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.btnPostComment.setEnabled(false);
                    break;
                case SUCCESS:
                    binding.btnPostComment.setEnabled(true);
                    binding.etComment.setText(""); // list self-updates via the live listener
                    Toast.makeText(this, R.string.discussion_posted, Toast.LENGTH_SHORT).show();
                    break;
                case ERROR:
                    binding.btnPostComment.setEnabled(true);
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    // --- MỚI: Hàm xử lý hành  gửi comment ---
    private void postComment() {
        if (discussionViewModel == null) return;
        String text = binding.etComment.getText() != null
                ? binding.etComment.getText().toString() : "";
        if (text.trim().isEmpty()) {
            binding.tilComment.setError(getString(R.string.discussion_empty));
            return;
        }
        binding.tilComment.setError(null);
        discussionViewModel.postComment(text);
    }

    /**
     * Applies the same-price discount to the price display. Computes the tier via
     * {@link com.capstone.eventticketing.util.PriceTierCalculator} using the movie's
     * base price, the booked-seat count, and its blockbuster flag. When discounted,
     * shows the bucket price as primary and the original struck through beside it.
     */
    private void bindDiscountedPrice(@NonNull Movie movie, int bookedCount) {
        double basePrice = (movie.getSeatMap() != null) ? movie.getSeatMap().getLowestPrice() : 0d;
        if (basePrice <= 0) {
            binding.tvPrice.setText(R.string.price_free);
            binding.tvPriceOriginal.setVisibility(View.GONE);
            return;
        }

        com.capstone.eventticketing.util.PriceTierCalculator.Result tier =
                com.capstone.eventticketing.util.PriceTierCalculator.evaluate(
                        basePrice, bookedCount, movie.isBlockbuster());

        if (tier.discounted) {
            binding.tvPrice.setText(String.format(Locale.getDefault(), "$%.2f", tier.finalPrice));
            binding.tvPriceOriginal.setText(
                    String.format(Locale.getDefault(), "$%.2f", tier.originalPrice));
            // Strike through the original price.
            binding.tvPriceOriginal.setPaintFlags(
                    binding.tvPriceOriginal.getPaintFlags()
                            | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            binding.tvPriceOriginal.setVisibility(View.VISIBLE);
        } else {
            binding.tvPrice.setText(String.format(Locale.getDefault(), "$%.2f", basePrice));
            binding.tvPriceOriginal.setVisibility(View.GONE);
        }
    }

    /** Renders the inline rating row and the reviews-section rating summary. */
    private void bindRatingSummary(@NonNull Movie movie) {
        boolean hasReviews = movie.getRating() != null && movie.getRating().getTotalReviews() > 0;

        if (hasReviews) {
            binding.tvRating.setText(String.format(Locale.getDefault(), "%.1f (%d reviews)",
                    movie.getRating().getAverageScore(), movie.getRating().getTotalReviews()));
            binding.icStar.setVisibility(View.VISIBLE);
            binding.tvRating.setVisibility(View.VISIBLE);

            binding.tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f",
                    movie.getRating().getAverageScore()));
            int count = movie.getRating().getTotalReviews();
            binding.tvReviewCount.setText(getResources().getQuantityString(
                    R.plurals.review_count, count, count));
            binding.layoutRatingSummary.setVisibility(View.VISIBLE);
        } else {
            binding.tvRating.setText(R.string.detail_no_reviews);
            binding.layoutRatingSummary.setVisibility(View.GONE);
        }
    }

    /**
     * Gates the Buy CTA: booking is permitted only while the movie is actively
     * showing. ENDED and COMING_SOON movies remain viewable but cannot be booked,
     * so the button is disabled and relabeled rather than silently allowing seat
     * selection.
     */
    private void bindBookingState(@NonNull Movie movie) {
        boolean bookable = Movie.STATUS_NOW_SHOWING.equals(movie.getStatus());
        binding.btnBuyTickets.setEnabled(bookable);
        binding.btnBuyTickets.setText(
                bookable ? R.string.detail_buy_tickets : R.string.detail_not_bookable);
    }

    /**
     * Loads the detail hero as two layers: a blurred, center-cropped backdrop
     * that fills the app bar, and the full, sharp poster fitted on top. The blur
     * uses a platform RenderEffect, which is API 31+ only; on older devices the
     * cropped backdrop plus scrim already reads as an intentional frame, so the
     * blur is simply skipped. No RenderEffect call is made below API 31.
     */
    private void loadHero(@Nullable String posterUrl) {
        // Sharp, whole poster on top.
        Glide.with(this)
                .load(posterUrl)
                .placeholder(R.color.slate_200)
                .error(R.color.slate_200)
                .fitCenter()
                .into(binding.ivHeroImage);

        // Blurred backdrop behind it — RenderEffect requires API 31 (S).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            binding.ivHeroBackdrop.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                            60f, 60f, android.graphics.Shader.TileMode.CLAMP));
        }

        // Cropped backdrop image (loaded on all API levels; blur applied above only on 31+).
        Glide.with(this)
                .load(posterUrl)
                .placeholder(R.color.slate_900)
                .error(R.color.slate_900)
                .centerCrop()
                .into(binding.ivHeroBackdrop);
    }

    private void renderReviews(@NonNull List<Review> reviews) {
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

    /** Opens the trailer URL externally (browser / YouTube app). */
    private void openTrailer(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.trim())));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.detail_trailer_unavailable, Toast.LENGTH_SHORT).show();
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
                RatingDialogFragment dialog =
                        RatingDialogFragment.newInstance(movieId, currentUserName);
                dialog.setOnReviewSubmittedListener(() -> {
                    viewModel.loadEvent();
                    viewModel.loadReviews();
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