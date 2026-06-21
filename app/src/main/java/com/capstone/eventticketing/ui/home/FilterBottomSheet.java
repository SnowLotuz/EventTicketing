package com.capstone.eventticketing.ui.home;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.SheetFilterBinding;
import com.capstone.eventticketing.util.EventFilter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Deep-filter sheet: category, price range, and date range. Initializes from the
 * current {@link EventFilter} and returns a new one via {@link OnFilterAppliedListener}.
 * Holds no business logic — it only collects criteria.
 */
public class FilterBottomSheet extends BottomSheetDialogFragment {

    public interface OnFilterAppliedListener {
        void onFilterApplied(@NonNull EventFilter filter);
    }

    private static final List<String> CATEGORIES = EventFilter.GENRES_WITH_ALL;

    private static final float PRICE_MAX = 500f;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    private SheetFilterBinding binding;
    @Nullable private OnFilterAppliedListener listener;

    // Working state, seeded from the incoming filter.
    private String selectedCategory = EventFilter.CATEGORY_ALL;
    private long fromMillis = 0L;
    private long toMillis = 0L;
    @Nullable private EventFilter initial;

    public void setInitialFilter(@NonNull EventFilter filter) { this.initial = filter; }
    public void setOnFilterAppliedListener(@Nullable OnFilterAppliedListener l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetFilterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EventFilter f = (initial != null) ? initial : EventFilter.none();
        selectedCategory = f.category;
        fromMillis = f.startDateMillis;
        toMillis = f.endDateMillis;

        buildCategoryChips();
        setupPriceSlider(f);
        setupDateFields();
        setupActions();
    }

    private void buildCategoryChips() {
        binding.chipGroupFilterCategory.removeAllViews();
        for (String cat : CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals(selectedCategory));
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_selector, null));
            chip.setChipStrokeWidth(0f);
            chip.setOnClickListener(v -> selectedCategory = cat);
            binding.chipGroupFilterCategory.addView(chip);
        }
    }

    private void setupPriceSlider(@NonNull EventFilter f) {
        float from = (float) Math.max(0d, f.minPrice);
        float to = (f.maxPrice >= Double.MAX_VALUE) ? PRICE_MAX : (float) Math.min(PRICE_MAX, f.maxPrice);
        binding.sliderPrice.setValues(from, to);
        updatePriceLabel(from, to);

        binding.sliderPrice.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            updatePriceLabel(values.get(0), values.get(1));
        });
    }

    private void updatePriceLabel(float from, float to) {
        String toLabel = (to >= PRICE_MAX) ? "$" + (int) PRICE_MAX + "+" : "$" + (int) to;
        binding.tvPriceRange.setText(String.format(Locale.getDefault(),
                "$%d – %s", (int) from, toLabel));
    }

    private void setupDateFields() {
        if (fromMillis > 0L) binding.etDateFrom.setText(DATE_FMT.format(fromMillis));
        if (toMillis > 0L) binding.etDateTo.setText(DATE_FMT.format(toMillis));

        binding.etDateFrom.setOnClickListener(v -> pickDate(true));
        binding.tilDateFrom.setOnClickListener(v -> pickDate(true));
        binding.etDateTo.setOnClickListener(v -> pickDate(false));
        binding.tilDateTo.setOnClickListener(v -> pickDate(false));
    }

    private void pickDate(boolean isFrom) {
        Calendar c = Calendar.getInstance();
        long seed = isFrom ? fromMillis : toMillis;
        if (seed > 0L) c.setTimeInMillis(seed);

        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            Calendar picked = Calendar.getInstance();
            // From = start of day; To = end of day, so the range is inclusive.
            picked.set(y, m, d, isFrom ? 0 : 23, isFrom ? 0 : 59, isFrom ? 0 : 59);
            long millis = picked.getTimeInMillis();
            if (isFrom) {
                fromMillis = millis;
                binding.etDateFrom.setText(DATE_FMT.format(millis));
            } else {
                toMillis = millis;
                binding.etDateTo.setText(DATE_FMT.format(millis));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setupActions() {
        binding.tvReset.setOnClickListener(v -> {
            selectedCategory = EventFilter.CATEGORY_ALL;
            fromMillis = 0L;
            toMillis = 0L;
            binding.etDateFrom.setText("");
            binding.etDateTo.setText("");
            binding.sliderPrice.setValues(0f, PRICE_MAX);
            buildCategoryChips();
        });

        binding.btnApplyFilter.setOnClickListener(v -> {
            List<Float> values = binding.sliderPrice.getValues();
            double min = values.get(0);
            float toVal = values.get(1);
            // Treat the slider maxed-out as "no upper bound".
            double max = (toVal >= PRICE_MAX) ? Double.MAX_VALUE : toVal;

            // Preserve the existing search query when applying deep filters.
            String query = (initial != null) ? initial.query : "";

            EventFilter result = new EventFilter(query, selectedCategory, min, max, fromMillis, toMillis);
            if (listener != null) listener.onFilterApplied(result);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}