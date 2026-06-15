package com.capstone.eventticketing.ui.rating;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.DialogRatingBinding;
import com.capstone.eventticketing.util.Resource;

public class RatingDialogFragment extends DialogFragment {

    private static final String ARG_EVENT_ID = "arg_event_id";
    private static final String ARG_USER_NAME = "arg_user_name";

    public interface OnReviewSubmittedListener {
        void onReviewSubmitted();
    }

    private DialogRatingBinding binding;
    private RatingViewModel viewModel;
    private ImageView[] stars;
    private int selectedRating = 0;
    @Nullable private OnReviewSubmittedListener listener;

    @NonNull
    public static RatingDialogFragment newInstance(@NonNull String eventId, @NonNull String userName) {
        RatingDialogFragment f = new RatingDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        args.putString(ARG_USER_NAME, userName);
        f.setArguments(args);
        return f;
    }

    public void setOnReviewSubmittedListener(@Nullable OnReviewSubmittedListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogRatingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RatingViewModel.class);

        stars = new ImageView[]{
                binding.star1, binding.star2, binding.star3, binding.star4, binding.star5
        };
        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> setRating(rating));
        }

        binding.btnSubmitReview.setOnClickListener(v -> submit());
        observe();
    }

    private void setRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < stars.length; i++) {
            stars[i].setColorFilter(getResources().getColor(
                    i < rating ? R.color.accent_blue : R.color.slate_200, null));
        }
    }

    private void submit() {
        if (selectedRating == 0) {
            Toast.makeText(requireContext(), R.string.rating_select_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String eventId = getArguments() != null ? getArguments().getString(ARG_EVENT_ID) : null;
        String userName = getArguments() != null ? getArguments().getString(ARG_USER_NAME) : "";
        String comment = binding.etComment.getText() != null
                ? binding.etComment.getText().toString().trim() : "";
        if (eventId == null) { dismiss(); return; }

        viewModel.submit(eventId, userName != null ? userName : "", selectedRating, comment);
    }

    private void observe() {
        viewModel.getSubmitState().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.btnSubmitReview.setEnabled(false);
                    break;
                case SUCCESS:
                    Toast.makeText(requireContext(), R.string.rating_thanks, Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onReviewSubmitted();
                    dismiss();
                    break;
                case ERROR:
                    binding.btnSubmitReview.setEnabled(true);
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}