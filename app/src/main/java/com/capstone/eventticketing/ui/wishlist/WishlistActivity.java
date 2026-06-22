package com.capstone.eventticketing.ui.wishlist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ActivityWishlistBinding;
import com.capstone.eventticketing.ui.detail.EventDetailActivity;
import com.capstone.eventticketing.ui.home.adapter.MovieAdapter;
import com.capstone.eventticketing.util.Resource;

import java.util.HashSet;
import java.util.List;

/**
 * Dedicated wishlist screen. Lists the user's saved movies and allows removing
 * them (the heart toggle un-wishlists). Pure View: all data work is in
 * {@link WishlistViewModel}. Reuses {@link MovieAdapter} for visual consistency
 * with the home list.
 */
public class WishlistActivity extends AppCompatActivity
        implements MovieAdapter.OnMovieInteractionListener {

    private ActivityWishlistBinding binding;
    private WishlistViewModel viewModel;
    private MovieAdapter adapter;

    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, WishlistActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWishlistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(WishlistViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new MovieAdapter(this);
        binding.rvWishlist.setLayoutManager(new LinearLayoutManager(this));
        binding.rvWishlist.setAdapter(adapter);

        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.load(); // refresh in case wishlist changed elsewhere
    }

    private void observeViewModel() {
        viewModel.getMovies().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmer.setVisibility(View.VISIBLE);
                    binding.shimmer.startShimmer();
                    binding.rvWishlist.setVisibility(View.GONE);
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
                    binding.rvWishlist.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getRemoveState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render(List<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            binding.rvWishlist.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.rvWishlist.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            // Every movie here is wishlisted, so all hearts render filled.
            HashSet<String> ids = new HashSet<>();
            for (Movie m : movies) {
                if (m.getMovieId() != null) ids.add(m.getMovieId());
            }
            adapter.setWishlistedIds(ids);
            adapter.submitList(movies);
        }
    }

    @Override
    public void onMovieClick(@NonNull Movie movie) {
        if (movie.getMovieId() == null) return;
        startActivity(EventDetailActivity.newIntent(this, movie.getMovieId()));
    }

    @Override
    public void onWishlistToggle(@NonNull Movie movie, boolean isCurrentlyWishlisted) {
        if (movie.getMovieId() == null) return;
        // On this screen the heart only ever removes from the wishlist.
        viewModel.removeFromWishlist(movie.getMovieId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}