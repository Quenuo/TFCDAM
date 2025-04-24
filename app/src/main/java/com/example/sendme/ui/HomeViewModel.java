package com.example.sendme.ui;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {
    private final MutableLiveData<List<Chat>> chatList = new MutableLiveData<>();
    private final MutableLiveData<List<User>> userList = new MutableLiveData<>();
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private ValueEventListener chatsListener;
    private ListenerRegistration usersListener;

    public HomeViewModel() {
        loadUsers();
        loadChats();
    }



    private void loadUsers() {
        usersListener = FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    List<User> users = value.toObjects(User.class);
                    userList.setValue(users);
                });
    }

    private void loadChats() {
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Chat> chats = new ArrayList<>();
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    Chat chat = chatSnapshot.getValue(Chat.class);
                    if (chat != null && chat.getParticipants().contains(currentUserPhone)) {
                        chat.setId(chatSnapshot.getKey());
                        chats.add(chat);
                    }
                }
                chatList.setValue(chats);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("loadChats", "Error al cargar chats: " + error.getMessage(), error.toException());
                errorMessage.setValue("No se pudieron cargar los chats. Intenta de nuevo más tarde.");
            }
        };
        FirebaseManager.getInstance().getDatabase()
                .getReference("chats")
                .addValueEventListener(chatsListener);
    }


    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public LiveData<List<Chat>> getChatList() {
        return chatList;
    }

    public LiveData<List<User>> getUserList() {
        return userList;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }
    public LiveData<String> getErrorMessage() {
        return errorMessage;
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
