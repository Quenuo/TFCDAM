package com.example.sendme.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.repository.FirebaseManager;

public class AuthViewModel extends ViewModel {

    private final MutableLiveData<Boolean> userExists = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    /**
     * Verifica si el usuario autenticado ya tiene un perfil en Firestore.
     * Usa el UID como ID del documento (correcto para email + contraseña).
     */
    public void checkUser() {
        String uid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            error.setValue("Usuario no autenticado");
            userExists.setValue(false);
            return;
        }

        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot ->
                        userExists.setValue(documentSnapshot.exists()))
                .addOnFailureListener(e ->
                        error.setValue("Error verificando perfil: " + e.getMessage()));
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