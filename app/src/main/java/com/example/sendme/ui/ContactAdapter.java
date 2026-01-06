package com.example.sendme.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemContactBinding;
import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador que muestra una lista de contactos para iniciar nuevos chats.
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private List<User> users = new ArrayList<>();
    private NavController navController;
    private boolean isProcessingClick = false;
    private static final String TAG = "ContactAdapter";

    public ContactAdapter(NavController navController) {
        this.navController = navController;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        User user = users.get(position);

        holder.binding.usernameText.setText(user.getUsername());

        String imageUrl = user.getImageUrl();
        Glide.with(holder.itemView.getContext())
                .load(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop()
                .into(holder.binding.profileImage);

        // CLICK LISTENER CON LOGS DETALLADOS
        holder.itemView.setOnClickListener(v -> {
            Log.d(TAG, "=== CLICK EN CONTACTO INICIADO ===");
            Log.d(TAG, "Usuario pulsado: " + user.getUsername() + " (UID: " + user.getUid() + ")");

            if (isProcessingClick) {
                Log.d(TAG, "Click ignorado: ya se está procesando otro click");
                return;
            }
            isProcessingClick = true;

            String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                    ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                    : null;

            if (currentUserUid == null) {
                Log.e(TAG, "ERROR: UID del usuario actual es null → no autenticado");
                isProcessingClick = false;
                return;
            }

            Log.d(TAG, "UID actual autenticado: " + currentUserUid);

            String otherUserUid = user.getUid();
            if (otherUserUid == null) {
                Log.e(TAG, "ERROR: UID del contacto seleccionado es null");
                isProcessingClick = false;
                return;
            }

            Log.d(TAG, "Creando chat 1:1 entre " + currentUserUid + " y " + otherUserUid);

            // Crear chat 1:1
            Map<String, Boolean> participants = new HashMap<>();
            participants.put(currentUserUid, true);
            participants.put(otherUserUid, true);

            Map<String, Integer> unreadCount = new HashMap<>();
            unreadCount.put(currentUserUid, 0);
            unreadCount.put(otherUserUid, 0);

            Chat newChat = new Chat();
            newChat.setParticipants(participants);
            newChat.setLastMessage("");
            newChat.setLastMessageTimestamp(System.currentTimeMillis());
            newChat.setUnreadCount(unreadCount);
            newChat.setGroup(false);

            String chatId = FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").push().getKey();

            newChat.setId(chatId);

            if (chatId == null) {
                Log.e(TAG, "ERROR: No se pudo generar chatId");
                isProcessingClick = false;
                return;
            }

            Log.d(TAG, "chatId generado: " + chatId);
            Log.d(TAG, "Guardando chat en /chats/" + chatId);

            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(chatId)
                    .setValue(newChat)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Chat 1:1 creado correctamente en Firebase");

                        DatabaseReference userChatsRef = FirebaseManager.getInstance().getDatabase().getReference("user-chats");

                        Log.d(TAG, "Actualizando user-chats para " + currentUserUid);
                        userChatsRef.child(currentUserUid).child(chatId).setValue(true);

                        Log.d(TAG, "Actualizando user-chats para " + otherUserUid);
                        userChatsRef.child(otherUserUid).child(chatId).setValue(true)
                                .addOnSuccessListener(aVoid2 -> {
                                    Log.d(TAG, "user-chats actualizados para ambos usuarios");

                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("user", user);
                                    bundle.putString("chatId", chatId);
                                    bundle.putBoolean("isGroup", false);

                                    Log.d(TAG, "Navegando a ChatFragment con chatId: " + chatId);

                                    try {
                                        navController.navigate(R.id.action_newChatFragment_to_chatFragment, bundle);
                                        Log.d(TAG, "Navegación exitosa");
                                    } catch (Exception e) {
                                        Log.e(TAG, "ERROR al navegar: " + e.getMessage(), e);
                                    } finally {
                                        isProcessingClick = false;
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "ERROR actualizando user-chats del otro usuario: " + e.getMessage());
                                    isProcessingClick = false;
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "ERROR creando el chat en Firebase: " + e.getMessage());
                        isProcessingClick = false;
                    });
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void setUsers(List<User> allUsers) {
        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Log.e(TAG, "Usuario no autenticado al filtrar contactos");
            this.users = new ArrayList<>();
            notifyDataSetChanged();
            return;
        }

        FirebaseManager.getInstance().getDatabase()
                .getReference("user-chats")
                .child(currentUserUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Set<String> uidsWithIndividualChat = new HashSet<>();
                        long totalChats = dataSnapshot.getChildrenCount();
                        final int[] processed = {0};

                        if (totalChats == 0) {
                            filterAndShowUsers(allUsers, currentUserUid, uidsWithIndividualChat);
                            return;
                        }

                        for (DataSnapshot chatSnap : dataSnapshot.getChildren()) {
                            String chatId = chatSnap.getKey();
                            if (chatId == null) {
                                processed[0]++;
                                continue;
                            }

                            FirebaseManager.getInstance().getDatabase()
                                    .getReference("chats").child(chatId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot chatSnapshot) {
                                            Chat chat = chatSnapshot.getValue(Chat.class);
                                            if (chat != null && chat.getParticipants() != null && !chat.isGroup()) {
                                                long otherCount = chat.getParticipants().keySet().stream()
                                                        .filter(uid -> !uid.equals(currentUserUid))
                                                        .count();

                                                if (otherCount == 1) {
                                                    String otherUid = chat.getParticipants().keySet().stream()
                                                            .filter(uid -> !uid.equals(currentUserUid))
                                                            .findFirst()
                                                            .orElse(null);
                                                    if (otherUid != null) {
                                                        uidsWithIndividualChat.add(otherUid);
                                                    }
                                                }
                                            }

                                            processed[0]++;
                                            if (processed[0] == totalChats) {
                                                filterAndShowUsers(allUsers, currentUserUid, uidsWithIndividualChat);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            processed[0]++;
                                            if (processed[0] == totalChats) {
                                                filterAndShowUsers(allUsers, currentUserUid, uidsWithIndividualChat);
                                            }
                                        }
                                    });
                        }
                    }

                    private void filterAndShowUsers(List<User> allUsers, String currentUserUid, Set<String> uidsWithIndividualChat) {
                        List<User> filtered = new ArrayList<>();
                        for (User user : allUsers) {
                            if (user.getUid() != null &&
                                    !user.getUid().equals(currentUserUid) &&
                                    !uidsWithIndividualChat.contains(user.getUid())) {
                                filtered.add(user);
                            }
                        }
                        ContactAdapter.this.users = filtered;
                        notifyDataSetChanged();
                        Log.d(TAG, "Mostrando " + filtered.size() + " contactos disponibles");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error cargando user-chats: " + error.getMessage());
                        ContactAdapter.this.users = new ArrayList<>();
                        notifyDataSetChanged();
                    }
                });
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        ItemContactBinding binding;

        ContactViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}