package com.example.sendme.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemChatBinding;
import com.example.sendme.repository.FirebaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    // Lista de chats y usuarios vinculados a ellos
    private List<Chat> chats = new ArrayList<>();
    private List<User> users = new ArrayList<>();

    // Controlador de navegación para cambiar entre fragmentos
    private NavController navController;

    private static final String TAG = "ChatAdapter";

    // Constructor que recibe el NavController para navegar al hacer clic
    public ChatAdapter(NavController navController) {
        this.navController = navController;
    }

    // Crea un nuevo ViewHolder con el layout del ítem de chat
    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemChatBinding binding = ItemChatBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    // Asigna los datos del chat y usuario al ViewHolder
    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        Log.d(TAG, "Binding chat ID: " + chat.getId());

        // Teléfono del usuario actualmente autenticado
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();

        // Se identifica al otro participante del chat (no el usuario actual)
        String otherParticipant = getOtherParticipant(chat.getParticipants(), currentUserPhone);

        // Buscar el usuario con el número de teléfono del otro participante
        User user = users.stream()
                .filter(u -> u.getPhone().equals(otherParticipant))
                .findFirst()
                .orElse(null);

        // Mostrar nombre de usuario e imagen de perfil
        if (user != null) {
            Log.d(TAG, "User found: " + user.getUsername() + ", ImageUrl: " + user.getImageUrl());
            holder.binding.usernameText.setText(user.getUsername());

            Glide.with(holder.itemView.getContext())
                    .load(user.getImageUrl().isEmpty() ? R.drawable.default_profile : user.getImageUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(holder.binding.profileImage);
        } else {
            // Si no se encuentra el usuario, mostrar el número de teléfono y foto por defecto
            Log.w(TAG, "User not found for phone: " + otherParticipant);
            holder.binding.usernameText.setText(otherParticipant);
            holder.binding.profileImage.setImageResource(R.drawable.default_profile);
        }

        // Mostrar el último mensaje del chat
        holder.binding.lastMessageText.setText(chat.getLastMessage() != null ? chat.getLastMessage() : "");

        // Mostrar cantidad de mensajes no leídos (si hay)
        Integer unread = chat.getUnreadCount() != null ? chat.getUnreadCount().get(currentUserPhone) : 0;
        if (unread != null && unread > 0) {
            holder.binding.unreadCount.setText(String.valueOf(unread));
            holder.binding.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.binding.unreadCount.setVisibility(View.GONE);
        }

        // Evento al hacer clic en un chat: navegar a ChatFragment
        holder.itemView.setOnClickListener(v -> {
            Log.d(TAG, "Chat clicked: " + chat.getId() + ", User: " + (user != null ? user.getUsername() : otherParticipant));
            if (user != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable("user", user);
                bundle.putString("chatId", chat.getId());
                Navigation.findNavController(v).navigate(R.id.action_chatListFragment_to_chatFragment, bundle);
            } else {
                Log.w(TAG, "Cannot navigate to ChatFragment: User is null");
            }
        });
    }

    // Devuelve la cantidad de ítems en la lista
    @Override
    public int getItemCount() {
        return chats.size();
    }

    /**
     * Actualiza la lista de chats y usuarios en el adaptador.
     *
     * @param chats Lista de objetos Chat
     * @param users Lista de objetos User relacionados con los chats
     */
    public void setChats(List<Chat> chats, List<User> users) {
        Log.d(TAG, "Setting chats: " + (chats != null ? chats.size() : 0) + ", users: " + (users != null ? users.size() : 0));
        this.chats = chats != null ? chats : new ArrayList<>();
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Obtiene el otro número de teléfono que no corresponde al usuario actual.
     *
     * @param participants Mapa con los participantes del chat
     * @param currentUserPhone Teléfono del usuario autenticado
     * @return Teléfono del otro participante
     */
    private String getOtherParticipant(Map<String, Boolean> participants, String currentUserPhone) {
        if (participants == null || participants.isEmpty()) {
            return "";
        }
        return participants.keySet().stream()
                .filter(phone -> !phone.equals(currentUserPhone))
                .findFirst()
                .orElse("");
    }

    // ViewHolder para el layout de cada chat
    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ItemChatBinding binding;

        ChatViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
