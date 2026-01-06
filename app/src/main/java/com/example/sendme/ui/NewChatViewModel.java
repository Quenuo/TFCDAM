package com.example.sendme.ui;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.data.model.User;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel encargado de gestionar la lógica de la pantalla de "Nuevo Chat".
 * Escucha los usuarios disponibles en Firebase y expone una lista para la UI.
 */
public class NewChatViewModel extends ViewModel {

    private final MutableLiveData<List<User>> users = new MutableLiveData<>();
    private ListenerRegistration usersListener;
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

    public NewChatViewModel() {
        loadAvailableUsers();
    }

    private void loadAvailableUsers() {
        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            errorLiveData.postValue("Usuario no autenticado");
            users.setValue(new ArrayList<>());
            return;
        }

        usersListener = FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e("NewChatViewModel", "Error: " + error.getMessage());
                        errorLiveData.postValue("Error cargando usuarios");
                        users.setValue(new ArrayList<>());
                        return;
                    }

                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        users.setValue(new ArrayList<>());
                        return;
                    }

                    List<User> availableUsers = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            // Forzar el uid desde el ID del documento (por seguridad)
                            user.setUid(doc.getId());

                            if (!doc.getId().equals(currentUserUid)) {
                                availableUsers.add(user);
                            }
                        }
                    }

                    users.setValue(availableUsers);
                    Log.d("NewChatViewModel", "Usuarios disponibles cargados: " + availableUsers.size());
                });
    }

    public LiveData<List<User>> getUsers() {
        return users;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (usersListener != null) {
            usersListener.remove();
        }
    }
}
