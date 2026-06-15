package com.capstone.eventticketing.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A generic wrapper describing the state of a value driven by an async operation.
 * Used across all ViewModels to expose Loading / Success / Error uniformly.
 *
 * @param <T> the type of data carried on success.
 */
public class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final String message;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null);
    }

    public static <T> Resource<T> success(@Nullable T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    public static <T> Resource<T> error(@NonNull String message) {
        return new Resource<>(Status.ERROR, null, message);
    }
}