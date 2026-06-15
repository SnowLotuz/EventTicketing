package com.capstone.eventticketing.ui.onboarding;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.databinding.ItemOnboardingPageBinding;

import java.util.List;

/** Static 3-page onboarding adapter for the intro ViewPager2. */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageVH> {

    /** Immutable description of a single onboarding page. */
    public static class Page {
        @RawRes public final int lottieRes;
        @StringRes public final int titleRes;
        @StringRes public final int descRes;
        public Page(@RawRes int lottieRes, @StringRes int titleRes, @StringRes int descRes) {
            this.lottieRes = lottieRes;
            this.titleRes = titleRes;
            this.descRes = descRes;
        }
    }

    @NonNull private final List<Page> pages;

    public OnboardingAdapter(@NonNull List<Page> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOnboardingPageBinding b = ItemOnboardingPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PageVH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {
        holder.bind(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageVH extends RecyclerView.ViewHolder {
        private final ItemOnboardingPageBinding b;
        PageVH(@NonNull ItemOnboardingPageBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull Page page) {
            b.lottieIllustration.setAnimation(page.lottieRes);
            b.lottieIllustration.playAnimation();
            b.tvTitle.setText(page.titleRes);
            b.tvDescription.setText(page.descRes);
        }
    }
}