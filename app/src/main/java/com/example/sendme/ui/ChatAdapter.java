package com.example.sendme.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemChatBinding;
import com.example.sendme.repository.FirebaseManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<Chat> chats = new ArrayList<>();
    private List<User> users = new ArrayList<>();
    private NavController navController;
    private static final String TAG = "ChatAdapter";

    public ChatAdapter(NavController navController) {
        this.navController = navController;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatBinding binding = ItemChatBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        Log.d(TAG, "Binding chat ID: " + chat.getId());

        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Log.e(TAG, "currentUserUid es null");
            return;
        }

        boolean isGroup = chat.isGroup();

        if (isGroup) {
            // === CHAT DE GRUPO ===
            String groupName = chat.getGroupName();
            if (groupName == null || groupName.trim().isEmpty()) {
                groupName = "Grupo";
            }
            holder.binding.usernameText.setText(groupName);

            String groupIcon = chat.getGroupIcon();
            if (groupIcon != null && !groupIcon.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(groupIcon)
                        .placeholder(R.drawable.ic_group)
                        .error(R.drawable.ic_group)
                        .circleCrop()
                        .into(holder.binding.profileImage);
            } else {
                holder.binding.profileImage.setImageResource(R.drawable.ic_group);
            }
        } else {
            // === CHAT INDIVIDUAL ===
            String otherParticipantUid = getOtherParticipant(chat.getParticipants(), currentUserUid);
            User otherUser = users.stream()
                    .filter(u -> u.getUid() != null && u.getUid().equals(otherParticipantUid))
                    .findFirst()
                    .orElse(null);

            if (otherUser != null) {
                holder.binding.usernameText.setText(otherUser.getUsername());
                String imageUrl = otherUser.getImageUrl();
                Glide.with(holder.itemView.getContext())
                        .load(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : R.drawable.default_profile)
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .circleCrop()
                        .into(holder.binding.profileImage);
            } else {
                holder.binding.usernameText.setText("Usuario desconocido");
                holder.binding.profileImage.setImageResource(R.drawable.default_profile);
            }
        }

        // Último mensaje
        String lastMessage = chat.getLastMessage();
        holder.binding.lastMessageText.setText(lastMessage != null ? lastMessage : "");

        // Mensajes no leídos (FIX PARA EVITAR CRASH SI UNREAD ES NULL)
        int unread = 0;
        if (chat.getUnreadCount() != null) {
            Integer count = chat.getUnreadCount().get(currentUserUid);
            if (count != null) {
                unread = count;
            }
        }

        if (unread > 0) {
            holder.binding.unreadCount.setText(String.valueOf(unread));
            holder.binding.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.binding.unreadCount.setVisibility(View.GONE);
        }

        // Timestamp
        holder.binding.timestampText.setText(formatTimestamp(chat.getLastMessageTimestamp()));

        // === CLICK EN EL CHAT CON LOGS DETALLADOS ===
        holder.itemView.setOnClickListener(v -> {
            Log.d(TAG, "=== CHAT PULSADO ===");
            Log.d(TAG, "Chat ID: " + chat.getId());
            Log.d(TAG, "Tipo: " + (isGroup ? "GRUPO" : "INDIVIDUAL"));
            Log.d(TAG, "groupName: '" + chat.getGroupName() + "'");
            Log.d(TAG, "isGroup flag: " + chat.isGroup());

            Bundle bundle = new Bundle();
            bundle.putString("chatId", chat.getId());

            if (isGroup) {
                Log.d(TAG, "Preparando navegación a GroupChatFragment");

                String groupName = chat.getGroupName();
                if (groupName == null || groupName.trim().isEmpty()) {
                    groupName = "Grupo";
                }

                bundle.putString("groupId", chat.getId());
                bundle.putString("groupName", groupName);
                bundle.putString("groupIcon", chat.getGroupIcon());
                bundle.putBoolean("isGroup", true);

                try {
                    Navigation.findNavController(v)
                            .navigate(R.id.action_chatListFragment_to_groupChatFragment, bundle);
                    Log.d(TAG, "Navegación a GroupChatFragment ejecutada correctamente");
                } catch (Exception e) {
                    Log.e(TAG, "ERROR navegando a GroupChatFragment: " + e.getMessage(), e);
                }
            } else {
                Log.d(TAG, "Preparando navegación a ChatFragment (individual)");

                String otherUid = getOtherParticipant(chat.getParticipants(), currentUserUid);
                User otherUser = users.stream()
                        .filter(u -> u.getUid() != null && u.getUid().equals(otherUid))
                        .findFirst()
                        .orElse(null);

                if (otherUser != null) {
                    bundle.putParcelable("user", otherUser);
                }
                bundle.putBoolean("isGroup", false);

                try {
                    Navigation.findNavController(v)
                            .navigate(R.id.action_chatListFragment_to_chatFragment, bundle);
                    Log.d(TAG, "Navegación a ChatFragment ejecutada correctamente");
                } catch (Exception e) {
                    Log.e(TAG, "ERROR navegando a ChatFragment: " + e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void setChats(List<Chat> chats, List<User> users) {
        this.chats = chats != null ? chats : new ArrayList<>();
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    private String getOtherParticipant(Map<String, Boolean> participants, String currentUserUid) {
        if (participants == null || participants.isEmpty()) return "";
        return participants.keySet().stream()
                .filter(uid -> !uid.equals(currentUserUid))
                .findFirst()
                .orElse("");
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ItemChatBinding binding;

        ChatViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}