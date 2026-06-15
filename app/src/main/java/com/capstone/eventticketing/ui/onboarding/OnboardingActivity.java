package com.capstone.eventticketing.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.ActivityOnboardingBinding;
import com.capstone.eventticketing.ui.auth.LoginActivity;
import com.capstone.eventticketing.util.PrefsManager;

import java.util.Arrays;
import java.util.List;

/**
 * First-launch intro. Shows a 3-step ViewPager2, then marks onboarding seen and
 * routes to Login. Reached only from {@link com.capstone.eventticketing.ui.splash.SplashActivity}
 * when the onboarding flag is unset.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private PrefsManager prefsManager;
    private OnboardingAdapter adapter;
    private int pageCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefsManager = new PrefsManager(this);

        List<OnboardingAdapter.Page> pages = Arrays.asList(
                new OnboardingAdapter.Page(R.raw.onboarding_1,
                        R.string.onboarding_title_1, R.string.onboarding_desc_1),
                new OnboardingAdapter.Page(R.raw.onboarding_2,
                        R.string.onboarding_title_2, R.string.onboarding_desc_2),
                new OnboardingAdapter.Page(R.raw.onboarding_3,
                        R.string.onboarding_title_3, R.string.onboarding_desc_3));
        pageCount = pages.size();

        adapter = new OnboardingAdapter(pages);
        binding.viewPager.setAdapter(adapter);

        new com.google.android.material.tabs.TabLayoutMediator(
                binding.tabIndicator, binding.viewPager,
                (tab, position) -> { /* dots only */ }).attach();

        setupControls();
    }

    private void setupControls() {
        binding.btnSkip.setOnClickListener(v -> finishOnboarding());

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < pageCount - 1) {
                binding.viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });

        // On the last page, the Next button becomes "Get Started" and Skip hides.
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean isLast = position == pageCount - 1;
                binding.btnNext.setText(isLast
                        ? R.string.onboarding_get_started : R.string.onboarding_next);
                binding.btnSkip.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    private void finishOnboarding() {
        prefsManager.setOnboardingSeen(true);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}