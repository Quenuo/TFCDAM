package com.example.sendme.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.sendme.AuthActivity;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentChatListBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.ChatAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragmento que muestra la lista de chats activos del usuario.
 * Incluye funcionalidad para iniciar nuevos chats, gestionar el perfil del usuario a través de un drawer
 * y cerrar sesión.
 */
public class ChatListFragment extends Fragment {

    private FragmentChatListBinding binding;
    private ChatAdapter adapter;
    private DrawerLayout drawerLayout;
    private NavController navController;
    private DocumentReference userRef;
    private ListenerRegistration userListener;

    // Listener en tiempo real para user-chats (nuevos chats)
    private ChildEventListener userChatsListener;
    private DatabaseReference userChatsRef;

    // Maps para referencias y listeners (para removerlos correctamente al salir de un chat)
    private Map<String, DatabaseReference> lastMessageRefs = new HashMap<>();
    private Map<String, ValueEventListener> lastMessageListeners = new HashMap<>();
    private Map<String, DatabaseReference> timestampRefs = new HashMap<>();
    private Map<String, ValueEventListener> timestampListeners = new HashMap<>();
    private Map<String, DatabaseReference> unreadRefs = new HashMap<>();
    private Map<String, ValueEventListener> unreadListeners = new HashMap<>();

    // UID del usuario actual
    private String currentUserUid;

    // Listas para chats y usuarios (para 1:1)
    private List<Chat> chats = new ArrayList<>();
    private List<User> contactUsers = new ArrayList<>();

    private static final String TAG = "ChatListFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatListBinding.inflate(inflater, container, false);
        Log.d(TAG, "onCreateView: binding inflado");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        navController = NavHostFragment.findNavController(this);
        drawerLayout = binding.drawerLayout;

        adapter = new ChatAdapter(navController);

        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.chatRecyclerView.setAdapter(adapter);

        MaterialToolbar toolbar = binding.toolbar;
        if (toolbar != null) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                toolbar.setNavigationIcon(null);
            } else {
                toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
            }
        }

        binding.fabNewChat.setOnClickListener(v -> {
            Log.d(TAG, "FAB clickeado, navegando a NewChatFragment");
            navController.navigate(R.id.action_chatListFragment_to_newChatFragment);
        });

        binding.fabCreateGroup.setOnClickListener(v -> {
            Log.d(TAG, "FAB Create Group clicked");
            navController.navigate(R.id.action_chatListFragment_to_createGroupFragment);
        });

        currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Log.e(TAG, "Usuario no autenticado");
            return;
        }

        // Drawer: foto y nombre
        if (binding.profileImageDrawer != null && binding.usernameDrawer != null) {
            userRef = FirebaseManager.getInstance().getFirestore()
                    .collection("users")
                    .document(currentUserUid);

            userListener = userRef.addSnapshotListener((snapshot, error) -> {
                if (error != null || binding == null || !isAdded()) return;

                if (snapshot != null && snapshot.exists()) {
                    User user = snapshot.toObject(User.class);
                    if (user != null) {
                        String imageUrl = user.getImageUrl() != null && !user.getImageUrl().isEmpty() ? user.getImageUrl() : null;
                        Glide.with(requireContext())
                                .load(imageUrl != null ? imageUrl : R.drawable.default_profile)
                                .error(R.drawable.default_profile)
                                .circleCrop()
                                .into(binding.profileImageDrawer);

                        binding.usernameDrawer.setText(user.getUsername() != null && !user.getUsername().isEmpty()
                                ? user.getUsername()
                                : "Usuario");
                    }
                }
            });
        }

        // Botones drawer
        if (binding.editProfileButton != null) {
            binding.editProfileButton.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                navController.navigate(R.id.action_chatListFragment_to_editProfileFragment);
            });
        }

        if (binding.logoutButton != null) {
            binding.logoutButton.setOnClickListener(v -> {
                if (userListener != null) userListener.remove();
                FirebaseManager.getInstance().getAuth().signOut();
                Intent intent = new Intent(requireContext(), AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            });
        }

        // Listener principal para nuevos chats y remoción
        userChatsRef = FirebaseManager.getInstance().getDatabase()
                .getReference("user-chats").child(currentUserUid);

        userChatsListener = userChatsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String chatId = snapshot.getKey();
                if (chatId != null && snapshot.getValue(Boolean.class) == true) {
                    loadChatDetails(chatId);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String chatId = snapshot.getKey();
                if (chatId != null) {
                    loadChatDetails(chatId);
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String chatId = snapshot.getKey();
                if (chatId != null) {
                    removeChatFromList(chatId);
                }
            }

            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error en listener user-chats: " + error.getMessage());
            }
        });

        loadInitialChats();

        // Landscape drawer fijo
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN, GravityCompat.START);
            }
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(requireContext())
                        .setTitle("¿Salir de SendMe?")
                        .setMessage("¿Estás seguro de que quieres cerrar la aplicación?")
                        .setPositiveButton("Salir", (dialog, which) -> requireActivity().finish())
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
    }

    private void loadInitialChats() {
        userChatsRef.get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot child : snapshot.getChildren()) {
                String chatId = child.getKey();
                if (chatId != null && child.getValue(Boolean.class) == true) {
                    loadChatDetails(chatId);
                }
            }
        });
    }

    private void loadChatDetails(String chatId) {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId);

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;

                Chat chat = snapshot.getValue(Chat.class);
                if (chat == null) return;

                chat.setId(chatId);
                if (chat.getParticipants() == null ||
                        !chat.getParticipants().containsKey(currentUserUid)) {

                    // El usuario ya NO pertenece al grupo
                    removeChatFromList(chatId);
                    return;
                }
                if (!chat.isGroup()) {
                    String otherUid = getOtherParticipant(chat.getParticipants(), currentUserUid);
                    if (otherUid != null) {
                        loadUserForChat(otherUid, chat);
                    } else {
                        addOrUpdateChat(chat, null);
                    }
                } else {
                    addOrUpdateChat(chat, null);
                }

                // === LISTENERS EN TIEMPO REAL ===
                DatabaseReference lmRef = chatRef.child("lastMessage");
                lastMessageRefs.put(chatId, lmRef);
                ValueEventListener lmListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String newLast = snapshot.getValue(String.class);
                        if (newLast != null) updateLastMessage(chatId, newLast);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                };
                lmRef.addValueEventListener(lmListener);
                lastMessageListeners.put(chatId, lmListener);

                DatabaseReference tsRef = chatRef.child("lastMessageTimestamp");
                timestampRefs.put(chatId, tsRef);
                ValueEventListener tsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long newTs = snapshot.getValue(Long.class);
                        if (newTs != null) updateTimestamp(chatId, newTs);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                };
                tsRef.addValueEventListener(tsListener);
                timestampListeners.put(chatId, tsListener);

                DatabaseReference unreadRef = chatRef.child("unreadCount").child(currentUserUid);
                unreadRefs.put(chatId, unreadRef);
                ValueEventListener unreadListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Integer newUnread = snapshot.getValue(Integer.class);
                        if (newUnread != null) updateUnread(chatId, newUnread);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                };
                unreadRef.addValueEventListener(unreadListener);
                unreadListeners.put(chatId, unreadListener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error cargando chat " + chatId + ": " + error.getMessage());
            }
        });
    }

    private void updateLastMessage(String chatId, String newLast) {
        for (Chat c : chats) {
            if (c.getId().equals(chatId)) {
                c.setLastMessage(newLast);
                addOrUpdateChat(c, null);
                break;
            }
        }
    }

    private void updateTimestamp(String chatId, long newTs) {
        for (Chat c : chats) {
            if (c.getId().equals(chatId)) {
                c.setLastMessageTimestamp(newTs);
                addOrUpdateChat(c, null);
                break;
            }
        }
    }

    private void updateUnread(String chatId, int newUnread) {
        for (Chat c : chats) {
            if (c.getId().equals(chatId)) {
                if (c.getUnreadCount() == null) c.setUnreadCount(new HashMap<>());
                c.getUnreadCount().put(currentUserUid, newUnread);
                addOrUpdateChat(c, null);
                break;
            }
        }
    }

    private void loadUserForChat(String otherUid, Chat chat) {
        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .document(otherUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || binding == null) return;

                    User otherUser = documentSnapshot.toObject(User.class);
                    addOrUpdateChat(chat, otherUser);
                })
                .addOnFailureListener(e -> addOrUpdateChat(chat, null));
    }

    private void addOrUpdateChat(Chat chat, User otherUser) {
        if (!isAdded() || binding == null) return;

        chats.removeIf(c -> c.getId().equals(chat.getId()));
        chats.add(chat);

        if (otherUser != null) {
            contactUsers.removeIf(u -> u.getUid() != null && u.getUid().equals(otherUser.getUid()));
            contactUsers.add(otherUser);
        }

        chats.sort((c1, c2) -> Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));

        adapter.setChats(chats, contactUsers);

        if (chats.isEmpty()) {
            binding.emptyChatView.setVisibility(View.VISIBLE);
            binding.chatRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyChatView.setVisibility(View.GONE);
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void removeChatFromList(String chatId) {
        if (!isAdded() || binding == null) return;

        // Remover listeners para evitar updates residuales después de salir
        DatabaseReference lmRef = lastMessageRefs.remove(chatId);
        ValueEventListener lmListener = lastMessageListeners.remove(chatId);
        if (lmRef != null && lmListener != null) lmRef.removeEventListener(lmListener);

        DatabaseReference tsRef = timestampRefs.remove(chatId);
        ValueEventListener tsListener = timestampListeners.remove(chatId);
        if (tsRef != null && tsListener != null) tsRef.removeEventListener(tsListener);

        DatabaseReference unreadRef = unreadRefs.remove(chatId);
        ValueEventListener unreadListener = unreadListeners.remove(chatId);
        if (unreadRef != null && unreadListener != null) unreadRef.removeEventListener(unreadListener);

        // Remover de la lista local
        chats.removeIf(c -> c.getId().equals(chatId));
        adapter.setChats(chats, contactUsers);

        if (chats.isEmpty()) {
            binding.emptyChatView.setVisibility(View.VISIBLE);
            binding.chatRecyclerView.setVisibility(View.GONE);
        }
    }

    private String getOtherParticipant(Map<String, Boolean> participants, String currentUid) {
        if (participants == null) return null;
        for (String uid : participants.keySet()) {
            if (!uid.equals(currentUid)) {
                return uid;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: binding establecido a nulo");

        if (userListener != null) {
            userListener.remove();
        }

        if (userChatsRef != null && userChatsListener != null) {
            userChatsRef.removeEventListener(userChatsListener);
        }

        // Limpieza final de listeners residuales
        for (Map.Entry<String, ValueEventListener> entry : lastMessageListeners.entrySet()) {
            DatabaseReference ref = lastMessageRefs.get(entry.getKey());
            if (ref != null) ref.removeEventListener(entry.getValue());
        }
        for (Map.Entry<String, ValueEventListener> entry : timestampListeners.entrySet()) {
            DatabaseReference ref = timestampRefs.get(entry.getKey());
            if (ref != null) ref.removeEventListener(entry.getValue());
        }
        for (Map.Entry<String, ValueEventListener> entry : unreadListeners.entrySet()) {
            DatabaseReference ref = unreadRefs.get(entry.getKey());
            if (ref != null) ref.removeEventListener(entry.getValue());
        }

        lastMessageListeners.clear();
        timestampListeners.clear();
        unreadListeners.clear();
        lastMessageRefs.clear();
        timestampRefs.clear();
        unreadRefs.clear();

        if (drawerLayout != null) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        binding = null;
    }
}