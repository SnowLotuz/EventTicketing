package com.capstone.eventticketing.data.model;

import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors a document in the Firestore {@code users} collection.
 * Public no-arg constructor + getters/setters are required for Firestore
 * automatic (de)serialization via {@code toObject(User.class)}.
 */
public class User {

    private String userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String role;
    private String fcmToken;
    private List<String> wishlistMovieIds;

    /** Required empty constructor for Firestore deserialization. */
    public User() {
        this.wishlistMovieIds = new ArrayList<>();
    }

    public User(String userId, String name, String email, String phoneNumber, String role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.wishlistMovieIds = new ArrayList<>();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public List<String> getWishlistMovieIds() { return wishlistMovieIds; }
    public void setWishlistMovieIds(List<String> wishlistMovieIds) { this.wishlistMovieIds = wishlistMovieIds; }

    @Exclude
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}