package com.capstone.eventticketing.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.User;
import com.capstone.eventticketing.data.repository.AuthRepository;
import com.capstone.eventticketing.data.repository.UserRepository;
import com.capstone.eventticketing.util.Resource;

public class ProfileViewModel extends ViewModel {

    @NonNull private final UserRepository userRepository = new UserRepository();
    @NonNull private final AuthRepository authRepository = new AuthRepository();

    private final MutableLiveData<Resource<User>> profile = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>> updateState = new MutableLiveData<>();

    public ProfileViewModel() {
        loadProfile();
    }

    public LiveData<Resource<User>> getProfile() { return profile; }
    public LiveData<Resource<Boolean>> getUpdateState() { return updateState; }

    public void loadProfile() {
        forward(userRepository.getProfile(), profile);
    }

    public void updateProfile(@NonNull String name, String avatarUrl) {
        forward(userRepository.updateProfile(name, avatarUrl), updateState);
    }

    public void logout() {
        authRepository.logout();
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