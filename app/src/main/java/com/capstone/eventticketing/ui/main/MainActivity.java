package com.capstone.eventticketing.ui.main;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.databinding.ActivityMainBinding;
import com.capstone.eventticketing.ui.home.HomeFragment;
import com.capstone.eventticketing.ui.profile.ProfileFragment;
import com.capstone.eventticketing.ui.tickets.TicketsFragment;

/**
 * Host activity for the three top-level destinations. Uses attach/detach
 * FragmentTransactions so each tab retains its view state and scroll position
 * when switching, rather than being recreated each time.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_HOME = "tag_home";
    private static final String TAG_TICKETS = "tag_tickets";
    private static final String TAG_PROFILE = "tag_profile";

    private ActivityMainBinding binding;
    private final FragmentManager fragmentManager = getSupportFragmentManager();
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            setupInitialFragments();
        } else {
            // Restore the active reference after process recreation.
            activeFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        }

        binding.bottomNav.setOnItemSelectedListener(this::onNavItemSelected);
    }

    /** Adds all three fragments once; Home shown, others detached. */
    private void setupInitialFragments() {
        Fragment home = new HomeFragment();
        Fragment tickets = new TicketsFragment();
        Fragment profile = new ProfileFragment();

        fragmentManager.beginTransaction()
                .add(R.id.fragment_container, profile, TAG_PROFILE).detach(profile)
                .add(R.id.fragment_container, tickets, TAG_TICKETS).detach(tickets)
                .add(R.id.fragment_container, home, TAG_HOME)
                .commit();

        activeFragment = home;
    }

    private boolean onNavItemSelected(@NonNull android.view.MenuItem item) {
        Fragment target = resolveFragment(item.getItemId());
        if (target == null || target == activeFragment) {
            return target != null;
        }
        fragmentManager.beginTransaction()
                .detach(activeFragment)
                .attach(target)
                .commit();
        activeFragment = target;
        return true;
    }

    private Fragment resolveFragment(int itemId) {
        if (itemId == R.id.nav_home) return fragmentManager.findFragmentByTag(TAG_HOME);
        if (itemId == R.id.nav_tickets) return fragmentManager.findFragmentByTag(TAG_TICKETS);
        if (itemId == R.id.nav_profile) return fragmentManager.findFragmentByTag(TAG_PROFILE);
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}