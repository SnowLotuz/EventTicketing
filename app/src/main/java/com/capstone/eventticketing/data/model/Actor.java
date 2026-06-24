package com.capstone.eventticketing.data.model;

/**
 * A cast member of a {@link Movie}: display name plus an image URL. Stored as an
 * element of the movie's {@code actors} array. Array order is billing order and
 * is preserved by Firestore, so the order entered in the create form is the
 * order shown on the detail screen.
 */
public class Actor {

    private String name;
    private String imageUrl;

    /** Required empty constructor for Firestore deserialization. */
    public Actor() { }

    public Actor(String name, String imageUrl) {
        this.name = name;
        this.imageUrl = imageUrl;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}