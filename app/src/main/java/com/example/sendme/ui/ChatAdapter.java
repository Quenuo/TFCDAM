package com.example.sendme.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemChatBinding;
import com.example.sendme.repository.FirebaseManager;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private List<Chat> chats = new ArrayList<>();
    private List<User> users = new ArrayList<>();

    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemChatBinding binding = ItemChatBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
        String otherParticipant = chat.getParticipants().stream()
                .filter(p -> !p.equals(currentUserPhone))
                .findFirst()
                .orElse("");

        // Buscar el usuario correspondiente
        User user = users.stream()
                .filter(u -> u.getPhone().equals(otherParticipant))
                .findFirst()
                .orElse(null);

        if (user != null) {
            holder.binding.usernameText.setText(user.getUsername());
            if (user.getImageUrl() != null && user.getImageUrl().startsWith("content://")) {
                holder.binding.profileImage.setImageURI(Uri.parse(user.getImageUrl()));
            } else {
                holder.binding.profileImage.setImageResource(R.drawable.default_profile);
            }
        } else {
            holder.binding.usernameText.setText(otherParticipant);
            holder.binding.profileImage.setImageResource(R.drawable.default_profile);
        }

        holder.binding.lastMessageText.setText(chat.getLastMessage() != null ? chat.getLastMessage() : "");

        // Mostrar contador de mensajes no leídos
        Integer unread = chat.getUnreadCount() != null ? chat.getUnreadCount().get(currentUserPhone) : 0;
        if (unread != null && unread > 0) {
            holder.binding.unreadCount.setText(String.valueOf(unread));
            holder.binding.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.binding.unreadCount.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void setChats(List<Chat> chats, List<User> users) {
        this.chats = chats;
        this.users = users;
        notifyDataSetChanged();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ItemChatBinding binding;

        ChatViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
