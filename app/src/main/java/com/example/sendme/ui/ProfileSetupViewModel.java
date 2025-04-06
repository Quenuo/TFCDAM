package com.example.sendme.ui;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.data.model.User;
import com.example.sendme.repository.FirebaseManager;

public class ProfileSetupViewModel extends ViewModel {
    private final MutableLiveData<Boolean> profileSaved = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private static final String DEFAULT_IMAGE_URL = "";

    public void saveProfile(String username, String status, String imageUriString) {
        String phone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
        String imageUrl = (imageUriString != null) ? imageUriString : DEFAULT_IMAGE_URL; // Si no hay imagen, usamos la URL por defecto

        saveUserToFirestore(phone, username, status, imageUrl);
    }

    private void saveUserToFirestore(String phone, String username, String status, String imageUrl) {
        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .document(phone)
                .set(new User(phone, username, status, imageUrl))
                .addOnSuccessListener(aVoid -> profileSaved.setValue(true))
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }

    public LiveData<Boolean> getProfileSaved() {
        return profileSaved;
    }

    public LiveData<String> getError() {
        return error;
    }
}