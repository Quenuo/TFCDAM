package com.example.sendme.data.model;

public class User {
    private String phone;
    private String username;
    private String status;
    private String imageUrl;

    public User() {}

    public User(String phone, String username, String status, String imageUrl) {
        this.phone = phone;
        this.username = username;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
