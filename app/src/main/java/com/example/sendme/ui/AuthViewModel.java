package com.example.sendme.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.repository.FirebaseManager;

public class AuthViewModel extends ViewModel {

    // LiveData que notifica si el usuario existe en la base de datos
    private final MutableLiveData<Boolean> userExists = new MutableLiveData<>();

    // LiveData para reportar errores ocurridos durante la verificación
    private final MutableLiveData<String> error = new MutableLiveData<>();

    /**
     * Verifica si existe un usuario con el número de teléfono especificado
     * en la colección "users" de Firebase Firestore.
     *
     * @param phone Número de teléfono a verificar
     */
    public void checkUser(String phone) {
        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .whereEqualTo("phone", phone)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Si la consulta retorna documentos, el usuario existe
                    userExists.setValue(!queryDocumentSnapshots.isEmpty());
                })
                .addOnFailureListener(e -> {
                    // Si hay un error en la consulta, se informa
                    error.setValue(e.getMessage());
                });
    }

    /**
     * Permite establecer un mensaje de error manualmente.
     *
     * @param errorMessage Mensaje de error a mostrar
     */
    public void setError(String errorMessage) {
        error.setValue(errorMessage);
    }

    /**
     * Retorna el LiveData que indica si el usuario existe.
     *
     * @return LiveData<Boolean> indicando existencia del usuario
     */
    public LiveData<Boolean> getUserExists() {
        return userExists;
    }

    /**
     * Retorna el LiveData con el mensaje de error.
     *
     * @return LiveData<String> con el error
     */
    public LiveData<String> getError() {
        return error;
    }
}
