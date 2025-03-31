package com.example.sendme.data.model;

public class User {
    private String uid;
    private String email;
    private String username;
    private String phoneNumber;
    private String profileImageUrl;

    public User() {}

    public User(String uid, String email, String username, String phoneNumber, String profileImageUrl) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.profileImageUrl = profileImageUrl;
    }

    // Getters y Setters
    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getProfileImageUrl() { return profileImageUrl; }
}
