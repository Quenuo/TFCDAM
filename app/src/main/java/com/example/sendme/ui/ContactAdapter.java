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
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Infla el layout de cada ítem de contacto.
        ItemContactBinding binding = ItemContactBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        User user = users.get(position);

        // Muestra el nombre de usuario.
        holder.binding.usernameText.setText(user.getUsername());

        // Carga la imagen de perfil, o imagen por defecto si no tiene.
        String imageUrl = user.getImageUrl();
        Glide.with(holder.itemView.getContext())
                .load(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop()
                .into(holder.binding.profileImage);

        // Listener para cuando se hace clic en un contacto.
        holder.itemView.setOnClickListener(v -> {
            if (isProcessingClick) {
                Log.d(TAG, "Click ignorado: ya se está procesando otro click");
                return;
            }

            isProcessingClick = true;

            Log.d(TAG, "Contacto pulsado: " + user.getUsername() + ", Teléfono: " + user.getPhone());

            // Obtiene el teléfono del usuario autenticado.
            String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                    ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()
                    : null;

            if (currentUserPhone == null) {
                Log.e(TAG, "Teléfono del usuario actual es null");
                isProcessingClick = false;
                return;
            }

            // Crea un nuevo chat con los participantes.
            Map<String, Boolean> participants = new HashMap<>();
            participants.put(currentUserPhone, true);
            participants.put(user.getPhone(), true);

            // Contador de mensajes no leídos para cada participante.
            Map<String, Integer> unreadCount = new HashMap<>();
            unreadCount.put(currentUserPhone, 0);
            unreadCount.put(user.getPhone(), 0);

            Chat newChat = new Chat();
            newChat.setParticipants(participants);
            newChat.setLastMessage("");
            newChat.setLastMessageTimestamp(System.currentTimeMillis());
            newChat.setUnreadCount(unreadCount);

            // Genera una ID única para el chat.
            String chatId = FirebaseManager.getInstance().getDatabase().getReference("chats").push().getKey();
            newChat.setId(chatId);

            if (chatId != null) {
                // Guarda el chat en Firebase.
                Log.d(TAG, "Intentando guardar el chat con chatId: " + chatId);
                FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId).setValue(newChat)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Chat creado exitosamente con chatId: " + chatId);

                            // Asocia el chat con ambos usuarios.
                            FirebaseManager.getInstance().getDatabase().getReference("user-chats").child(currentUserPhone).child(chatId).setValue(true);
                            FirebaseManager.getInstance().getDatabase().getReference("user-chats").child(user.getPhone()).child(chatId).setValue(true)
                                    .addOnSuccessListener(aVoid2 -> {
                                        Log.d(TAG, "user-chats actualizados para ambos usuarios");

                                        // Navega al fragmento del chat con el usuario y chatId como argumentos.
                                        Bundle bundle = new Bundle();
                                        bundle.putParcelable("user", user);
                                        bundle.putString("chatId", chatId);
                                        try {
                                            navController.navigate(R.id.action_newChatFragment_to_chatFragment, bundle);
                                            Log.d(TAG, "Navegación al fragmento de chat iniciada");
                                        } catch (Exception e) {
                                            Log.e(TAG, "Fallo en la navegación: " + e.getMessage(), e);
                                        } finally {
                                            isProcessingClick = false;
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Fallo al actualizar user-chats del receptor: " + e.getMessage(), e);
                                        isProcessingClick = false;
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Fallo al crear el chat: " + e.getMessage(), e);
                            isProcessingClick = false;
                        });
            } else {
                Log.e(TAG, "No se pudo generar chatId");
                isProcessingClick = false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * Establece la lista de usuarios excluyendo aquellos con los que ya se tiene un chat.
     */
    public void setUsers(List<User> allUsers) {
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber()
                : null;

        if (currentUserPhone == null) {
            Log.e(TAG, "Usuario actual no autenticado");
            this.users = new ArrayList<>();
            notifyDataSetChanged();
            return;
        }

        // Carga los chats existentes del usuario actual.
        FirebaseManager.getInstance().getDatabase()
                .getReference("user-chats")
                .child(currentUserPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<String> existingChatParticipants = new ArrayList<>();
                        final int[] processedChats = {0};

                        if (!dataSnapshot.hasChildren()) {
                            // Si el usuario no tiene chats, mostrar todos los demás usuarios.
                            List<User> filteredUsers = new ArrayList<>();
                            for (User user : allUsers) {
                                if (!user.getPhone().equals(currentUserPhone)) {
                                    filteredUsers.add(user);
                                }
                            }
                            ContactAdapter.this.users = filteredUsers;
                            notifyDataSetChanged();
                            return;
                        }

                        // Para cada chat existente, obtener los participantes.
                        for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                            String chatId = chatSnapshot.getKey();
                            FirebaseManager.getInstance().getDatabase()
                                    .getReference("chats")
                                    .child(chatId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot chatDataSnapshot) {
                                            Chat chat = chatDataSnapshot.getValue(Chat.class);
                                            if (chat != null && chat.getParticipants() != null) {
                                                for (String participant : chat.getParticipants().keySet()) {
                                                    if (!participant.equals(currentUserPhone) && !existingChatParticipants.contains(participant)) {
                                                        existingChatParticipants.add(participant);
                                                    }
                                                }
                                            }
                                            processedChats[0]++;

                                            // Una vez procesados todos los chats, filtrar usuarios que ya tienen chat.
                                            if (processedChats[0] == dataSnapshot.getChildrenCount()) {
                                                List<User> filteredUsers = new ArrayList<>();
                                                for (User user : allUsers) {
                                                    if (!existingChatParticipants.contains(user.getPhone()) && !user.getPhone().equals(currentUserPhone)) {
                                                        filteredUsers.add(user);
                                                    }
                                                }
                                                ContactAdapter.this.users = filteredUsers;
                                                notifyDataSetChanged();
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError databaseError) {
                                            Log.e(TAG, "Error cargando detalles del chat: " + databaseError.getMessage());
                                            processedChats[0]++;
                                            if (processedChats[0] == dataSnapshot.getChildrenCount()) {
                                                List<User> filteredUsers = new ArrayList<>();
                                                for (User user : allUsers) {
                                                    if (!existingChatParticipants.contains(user.getPhone()) && !user.getPhone().equals(currentUserPhone)) {
                                                        filteredUsers.add(user);
                                                    }
                                                }
                                                ContactAdapter.this.users = filteredUsers;
                                                notifyDataSetChanged();
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error al cargar user-chats: " + databaseError.getMessage());
                        ContactAdapter.this.users = new ArrayList<>();
                        notifyDataSetChanged();
                    }
                });
    }

    /**
     * ViewHolder que mantiene la referencia al binding del ítem de contacto.
     */
    static class ContactViewHolder extends RecyclerView.ViewHolder {
        ItemContactBinding binding;

        ContactViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
