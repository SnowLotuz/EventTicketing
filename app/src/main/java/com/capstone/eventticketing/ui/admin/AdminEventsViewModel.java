package com.capstone.eventticketing.ui.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.MovieRepository;
import com.capstone.eventticketing.util.Resource;

import java.util.List;

/**
 * Backs {@link AdminEventsActivity}. Lists all movies (reusing the shared
 * repository query) and exposes delete. A refresh trigger re-runs the query
 * after a mutation so the list reflects deletes/edits without a manual reload.
 */
public class AdminEventsViewModel extends ViewModel {

    @NonNull private final MovieRepository movieRepository = new MovieRepository();

    private final MutableLiveData<Integer> refreshTrigger = new MutableLiveData<>(0);
    private final LiveData<Resource<List<Movie>>> movies;

    private final MutableLiveData<Resource<Boolean>> deleteState = new MutableLiveData<>();

    public AdminEventsViewModel() {
        movies = Transformations.switchMap(refreshTrigger, t -> movieRepository.getMovies(null));
    }

    public LiveData<Resource<List<Movie>>> getMovies() { return movies; }
    public LiveData<Resource<Boolean>> getDeleteState() { return deleteState; }

    public void refresh() {
        Integer current = refreshTrigger.getValue();
        refreshTrigger.setValue(current == null ? 1 : current + 1);
    }

    public void deleteMovie(@NonNull String movieId) {
        deleteState.setValue(Resource.loading());
        LiveData<Resource<Boolean>> source = movieRepository.deleteMovie(movieId);
        source.observeForever(new Observer<Resource<Boolean>>() {
            @Override
            public void onChanged(Resource<Boolean> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                deleteState.setValue(resource);
                if (resource.status == Resource.Status.SUCCESS) {
                    refresh();
                }
            }
        });
    }
}