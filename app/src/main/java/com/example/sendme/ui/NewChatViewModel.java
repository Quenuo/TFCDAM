package com.example.sendme.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.data.model.User;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel encargado de gestionar la lógica de la pantalla de "Nuevo Chat".
 * Escucha los usuarios disponibles en Firebase y expone una lista para la UI.
 */
public class NewChatViewModel extends ViewModel {
    // LiveData con la lista de usuarios disponibles (excluyendo al usuario actual)
    private final MutableLiveData<List<User>> users = new MutableLiveData<>();

    // Registro del listener para poder eliminarlo al limpiar el ViewModel
    private ListenerRegistration usersListener;

    // LiveData para manejar y observar errores en la carga de usuarios
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

    /**
     * Constructor del ViewModel: inicia la carga de usuarios desde Firebase.
     */
    public NewChatViewModel() {
        loadAvailableUsers();
    }

    /**
     * Carga los usuarios desde Firestore, excluyendo al usuario actualmente autenticado.
     * Escucha los cambios en tiempo real usando un snapshot listener.
     */
    private void loadAvailableUsers() {
        try {
            // Obtener el número de teléfono del usuario actual
            String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                    ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()
                    : "unknown";

            // Añadir un listener a la colección "users" de Firestore
            usersListener = FirebaseManager.getInstance().getFirestore()
                    .collection("users")
                    .addSnapshotListener((value, error) -> {
                        // En caso de error, notificar a través del LiveData de errores
                        if (error != null) {
                            errorLiveData.postValue(error.getMessage());
                            users.setValue(new ArrayList<>()); // Vaciar lista por seguridad
                            return;
                        }

                        // Si no hay datos, vaciar lista
                        if (value == null || value.isEmpty()) {
                            users.setValue(new ArrayList<>());
                            return;
                        }

                        // Filtrar los usuarios distintos al actual
                        List<User> allUsers = new ArrayList<>();
                        for (User user : value.toObjects(User.class)) {
                            if (!user.getPhone().equals(currentUserPhone)) {
                                allUsers.add(user);
                            }
                        }

                        // Publicar la lista de usuarios en el LiveData
                        users.setValue(allUsers);
                    });

        } catch (Exception e) {
            // Captura cualquier excepción inesperada
            errorLiveData.postValue(e.getMessage());
            users.setValue(new ArrayList<>()); // Evita tener una lista nula
        }
    }

    /**
     * LiveData observable desde la UI que contiene la lista de usuarios disponibles.
     */
    public LiveData<List<User>> getUsers() {
        return users;
    }

    /**
     * LiveData observable desde la UI para mostrar mensajes de error.
     */
    public LiveData<String> getError() {
        return errorLiveData;
    }

    /**
     * Se llama cuando el ViewModel es destruido. Aquí eliminamos el listener de Firestore
     * para evitar fugas de memoria o listeners activos innecesarios.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (usersListener != null) usersListener.remove();
    }
}
