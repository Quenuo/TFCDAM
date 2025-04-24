package com.example.sendme.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.navigation.NavController;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemContactBinding;
import com.example.sendme.repository.FirebaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
    private List<User> users = new ArrayList<>();
    private NavController navController;

    public ContactAdapter(NavController navController) {
        this.navController = navController;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        User user = users.get(position);
        holder.binding.usernameText.setText(user.getUsername());
        if (user.getImageUrl() != null && user.getImageUrl().startsWith("content://")) {
            holder.binding.profileImage.setImageURI(Uri.parse(user.getImageUrl()));
        } else {
            holder.binding.profileImage.setImageResource(R.drawable.default_profile);
        }
        holder.itemView.setOnClickListener(v -> {
            // Crear nuevo chat
            String currentUserPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
            List<String> participants = new ArrayList<>();
            participants.add(currentUserPhone);
            participants.add(user.getPhone());

            Map<String, Integer> unreadCount = new HashMap<>();
            unreadCount.put(currentUserPhone, 0);
            unreadCount.put(user.getPhone(), 0);

            Chat newChat = new Chat();
            newChat.setParticipants(participants);
            newChat.setLastMessage("");
            newChat.setLastMessageTimestamp(System.currentTimeMillis());
            newChat.setUnreadCount(unreadCount);

            String chatId = FirebaseManager.getInstance().getDatabase()
                    .getReference("chats")
                    .push()
                    .getKey();
            newChat.setId(chatId);

            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats")
                    .child(chatId)
                    .setValue(newChat)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Navegar de vuelta a ChatListFragment
                            navController.popBackStack();
                        }
                    });
        });
    }
    @Override
    public int getItemCount() {
        return users.size();
    }

    public void setUsers(List<User> users) {
        this.users = users;
        notifyDataSetChanged();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        ItemContactBinding binding;

        ContactViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
