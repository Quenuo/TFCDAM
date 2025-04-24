package com.example.sendme.ui;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.data.model.User;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewChatViewModel extends ViewModel {
    private final MutableLiveData<List<User>> users = new MutableLiveData<>();
    private ListenerRegistration usersListener;
    private ValueEventListener chatsListener;
    private final MutableLiveData<String> errorLiveData=new MutableLiveData<>();

    public NewChatViewModel() {
        loadAvailableUsers();
    }

    private void loadAvailableUsers() {
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
        Log.d("prueba", "Current user phone: " + currentUserPhone);
        // Paso 1: Obtener usuarios de Firestore
        usersListener = FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    List<User> allUsers = new ArrayList<>();
                    for (User user : value.toObjects(User.class)) {
                        if (!user.getPhone().equals(currentUserPhone)) {
                            allUsers.add(user);
                        }
                    }
                    Log.d("prueba", "Total users from Firestore (excluding current user): " + allUsers.size());

                    // Paso 2: Obtener chats existentes desde Realtime Database
                    chatsListener = new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            Set<String> contactedPhones = new HashSet<>();
                            for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                                List<String> participants = (List<String>) chatSnapshot.child("participants").getValue();
                                if (participants != null && participants.contains(currentUserPhone)) {
                                    for (String participant : participants) {
                                        if (!participant.equals(currentUserPhone)) {
                                            contactedPhones.add(participant);
                                        }
                                    }
                                }
                            }
                            Log.d("prueba_listener", "Contacted phones: " + contactedPhones.size());

                            // Paso 3: Filtrar usuarios que no están en chats
                            List<User> availableUsers = new ArrayList<>();
                            for (User user : allUsers) {
                                if (!contactedPhones.contains(user.getPhone())) {
                                    availableUsers.add(user);
                                }
                            }
                            users.setValue(availableUsers);
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            errorLiveData.postValue(String.valueOf(error));

                        }
                    };
                    FirebaseManager.getInstance().getDatabase()
                            .getReference("chats")
                            .addValueEventListener(chatsListener);
                });
    }

    public LiveData<List<User>> getUsers() {

        return users;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (usersListener != null) usersListener.remove();
        if (chatsListener != null) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats")
                    .removeEventListener(chatsListener);
        }
    }
}
