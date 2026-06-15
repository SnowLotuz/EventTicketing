package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.data.repository.UserRepository;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link AdminUsersActivity}. Loads the user directory and handles role
 * changes, reloading the list after a successful change. No Firestore here.
 */
public class AdminUsersViewModel extends ViewModel {

    @NonNull private final UserRepository userRepository = new UserRepository();

    private final MutableLiveData<Resource<List<User>>> users = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> roleUpdateState = new MutableLiveData<>();

    public AdminUsersViewModel() {
        loadUsers();
    }

    public LiveData<Resource<List<User>>> getUsers() { return users; }
    public LiveData<Resource<Boolean>> getRoleUpdateState() { return roleUpdateState; }

    public String getCurrentUserId() { return userRepository.getCurrentUserId(); }

    public void loadUsers() {
        forward(userRepository.getAllUsers(), users);
    }

    public void updateRole(@NonNull String targetUserId, @NonNull String newRole) {
        roleUpdateState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = userRepository.updateUserRole(targetUserId, newRole);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                roleUpdateState.setValue(r);
                if (r.status == Resource.Status.SUCCESS) {
                    loadUsers(); // reflect the change in the list
                }
            }
        });
    }

    private <T> void forward(@NonNull LiveData<Resource<T>> source,
                             @NonNull MutableLiveData<Resource<T>> target) {
        target.setValue(Resource.loading());
        source.observeForever(new Observer<Resource<T>>() {
            @Override
            public void onChanged(Resource<T> r) {
                if (r == null || r.status == Resource.Status.LOADING) return;
                target.setValue(r);
                source.removeObserver(this);
            }
        });
    }
}