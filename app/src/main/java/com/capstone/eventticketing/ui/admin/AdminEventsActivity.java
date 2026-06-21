package com.capstone.eventticketing.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.databinding.ActivityAdminEventsBinding;
import com.capstone.eventticketing.util.Resource;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/**
 * Admin movie-management list. Shows every movie and, per movie, an action sheet
 * to Edit, Scan tickets (launches {@link CheckInActivity} scoped to the movie),
 * or Delete (with confirmation). Pure View — all data work is in
 * {@link AdminEventsViewModel}.
 */
public class AdminEventsActivity extends AppCompatActivity
        implements AdminEventAdapter.OnEventActionListener {

    private ActivityAdminEventsBinding binding;
    private AdminEventsViewModel viewModel;
    private AdminEventAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminEventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AdminEventsViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new AdminEventAdapter(this);
        binding.rvEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEvents.setAdapter(adapter);

        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void observeViewModel() {
        viewModel.getMovies().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.shimmer.setVisibility(View.VISIBLE);
                    binding.shimmer.startShimmer();
                    binding.rvEvents.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    renderList(resource.data);
                    break;
                case ERROR:
                    binding.shimmer.stopShimmer();
                    binding.shimmer.setVisibility(View.GONE);
                    binding.rvEvents.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getDeleteState().observe(this, resource -> {
            if (resource == null) return;
            if (resource.status == Resource.Status.SUCCESS) {
                Toast.makeText(this, R.string.admin_deleted, Toast.LENGTH_SHORT).show();
            } else if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderList(List<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            binding.rvEvents.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.rvEvents.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(movies);
        }
    }

    @Override
    public void onMovieOptions(@NonNull Movie movie) {
        if (movie.getMovieId() == null) return;
        showActionSheet(movie);
    }

    private void showActionSheet(@NonNull Movie movie) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.sheet_event_actions, null);
        sheet.setContentView(content);

        ((TextView) content.findViewById(R.id.tv_sheet_title)).setText(movie.getTitle());

        content.findViewById(R.id.action_edit).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(EditEventActivity.newIntent(this, movie.getMovieId()));
        });

        content.findViewById(R.id.action_scan).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(CheckInActivity.newIntent(this, movie.getMovieId()));
        });

        content.findViewById(R.id.action_delete).setOnClickListener(v -> {
            sheet.dismiss();
            confirmDelete(movie);
        });

        sheet.show();
    }

    private void confirmDelete(@NonNull Movie movie) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_delete_confirm_title)
                .setMessage(R.string.admin_delete_confirm_msg)
                .setPositiveButton(R.string.admin_delete_confirm_yes,
                        (d, w) -> viewModel.deleteMovie(movie.getMovieId()))
                .setNegativeButton(R.string.admin_delete_confirm_no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}