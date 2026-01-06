package com.example.sendme.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.repository.FirebaseManager;

import java.util.HashMap;
import java.util.Map;

public class ProfileSetupViewModel extends ViewModel {
    private final MutableLiveData<Boolean> profileSaved = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private static final String DEFAULT_IMAGE_URL = "";
    /**
     * Guarda el perfil del usuario con el nombre, estado y URL de la imagen.
     * @param username Nombre de usuario ingresado.
     * @param status Estado del usuario (puede ser el mensaje por defecto).
     * @param imageUriString URL de la imagen subida a Imgur (o vacío si no se seleccionó imagen).
     */
    public void saveProfile(String username, String status, String imageUriString, String phone) {
        String uid = FirebaseManager.getInstance().getAuth().getCurrentUser().getUid();
        if (uid == null) {
            error.setValue("Error: usuario no autenticado");
            return;
        }

        String imageUrl = (imageUriString != null && !imageUriString.isEmpty()) ? imageUriString : DEFAULT_IMAGE_URL;

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);           // ← AÑADIDO: guardamos el UID dentro del documento
        userData.put("username", username);
        userData.put("status", status);
        userData.put("imageUrl", imageUrl);
        userData.put("phone", phone);
        userData.put("email", FirebaseManager.getInstance().getAuth().getCurrentUser().getEmail());

        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> profileSaved.setValue(true))
                .addOnFailureListener(e -> error.setValue("Error al guardar perfil: " + e.getMessage()));
    }

    public LiveData<Boolean> getProfileSaved() {
        return profileSaved;
    }

    public LiveData<String> getError() {
        return error;
    }
}