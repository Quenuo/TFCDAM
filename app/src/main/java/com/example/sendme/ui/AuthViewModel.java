package com.example.sendme.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.repository.FirebaseManager;

public class AuthViewModel extends ViewModel {
    private final MutableLiveData<Boolean> userExists = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public void checkUser(String phone) {
        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .whereEqualTo("phone", phone)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userExists.setValue(!queryDocumentSnapshots.isEmpty());
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }

    public void setError(String errorMessage) {
        error.setValue(errorMessage);
    }

    public LiveData<Boolean> getUserExists() {
        return userExists;
    }

    public LiveData<String> getError() {
        return error;
    }
}
