package com.capstone.eventticketing.ui.detail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.capstone.eventticketing.data.model.Discussion;
import com.capstone.eventticketing.data.model.Movie;
import com.capstone.eventticketing.data.repository.DiscussionRepository;
import com.capstone.eventticketing.util.Resource;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * Backs the discussion section of the detail screen. Streams a movie's comments
 * live and posts new ones. Whether the input is shown is derived from the movie's
 * status (open for COMING_SOON / NOW_SHOWING, closed for ENDED) — the same gate
 * the security rules enforce, surfaced here for the UI.
 */
public class DiscussionViewModel extends ViewModel {

    @NonNull private final DiscussionRepository repository;
    @NonNull private final String movieId;
    @NonNull private final String movieStatus;

    private final MutableLiveData<Resource<List<Discussion>>> comments = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> postState = new MutableLiveData<>();

    private ListenerRegistration commentsListener;

    public DiscussionViewModel(@NonNull DiscussionRepository repository,
                               @NonNull String movieId,
                               @NonNull String movieStatus) {
        this.repository = repository;
        this.movieId = movieId;
        this.movieStatus = movieStatus;
        startListening();
    }

    public LiveData<Resource<List<Discussion>>> getComments() { return comments; }
    public LiveData<Resource<String>> getPostState() { return postState; }

    /**
     * @return whether commenting is open for this movie's status. Mirrors the
     * security rule's whitelist: open only for NOW_SHOWING and COMING_SOON.
     */
    public boolean isCommentingOpen() {
        return Movie.STATUS_NOW_SHOWING.equals(movieStatus)
                || Movie.STATUS_COMING_SOON.equals(movieStatus);
    }

    private void startListening() {
        commentsListener = repository.listenToComments(movieId, comments);
    }

    /** Posts a comment, then surfaces success/error via {@link #getPostState()}. */
    public void postComment(@NonNull String comment) {
        if (comment.trim().isEmpty()) {
            postState.setValue(Resource.error("Comment can't be empty."));
            return;
        }
        postState.setValue(Resource.loading());

        LiveData<Resource<String>> source = repository.postComment(movieId, comment);
        source.observeForever(new Observer<Resource<String>>() {
            @Override
            public void onChanged(Resource<String> resource) {
                if (resource == null || resource.status == Resource.Status.LOADING) return;
                source.removeObserver(this);
                postState.setValue(resource);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Stop the live subscription so it doesn't read in the background.
        if (commentsListener != null) {
            commentsListener.remove();
            commentsListener = null;
        }
    }

    /** Injects the repository and movie context so the View stays logic-free. */
    public static class Factory implements ViewModelProvider.Factory {
        @NonNull private final String movieId;
        @NonNull private final String movieStatus;

        public Factory(@NonNull String movieId, @NonNull String movieStatus) {
            this.movieId = movieId;
            this.movieStatus = movieStatus;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(DiscussionViewModel.class)) {
                return (T) new DiscussionViewModel(new DiscussionRepository(), movieId, movieStatus);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}