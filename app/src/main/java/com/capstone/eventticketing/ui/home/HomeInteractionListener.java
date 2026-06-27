package com.capstone.eventticketing.ui.home;

import androidx.annotation.NonNull;

import com.capstone.eventticketing.data.model.Movie;

/** Bubbles movie taps from any Home section (banner or rail) up to the Fragment. */
public interface HomeInteractionListener {
    void onMovieClick(@NonNull Movie movie);
}