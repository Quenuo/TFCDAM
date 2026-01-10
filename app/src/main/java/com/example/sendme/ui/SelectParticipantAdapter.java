package com.example.sendme.ui;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemSelectParticipantBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SelectParticipantAdapter extends RecyclerView.Adapter<SelectParticipantAdapter.SelectParticipantViewHolder> {

    private List<User> users = new ArrayList<>();
    private final Set<String> selectedUserUids; // ← Ahora seleccionamos por UID
    private final OnUserSelectedListener listener;

    public interface OnUserSelectedListener {
        void onUserSelected(String userUid);
    }

    public SelectParticipantAdapter(Set<String> selectedUserUids, OnUserSelectedListener listener) {
        this.selectedUserUids = selectedUserUids != null ? selectedUserUids : new HashSet<>();
        this.listener = listener;
    }

    public void setUsers(List<User> users) {
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
        Log.d("SelectParticipantAdapter", "Users set, size: " + this.users.size());
    }

    @NonNull
    @Override
    public SelectParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSelectParticipantBinding binding = ItemSelectParticipantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SelectParticipantViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectParticipantViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class SelectParticipantViewHolder extends RecyclerView.ViewHolder {

        private final ItemSelectParticipantBinding binding;

        SelectParticipantViewHolder(@NonNull ItemSelectParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(User user) {
            String userUid = user.getUid();
            if (userUid == null) {
                Log.e("SelectParticipantAdapter", "User sin UID");
                return;
            }

            Log.d("SelectParticipantAdapter", "Binding user: " + user.getUsername() + ", UID: " + userUid);

            // Imagen de perfil
            String imageUrl = user.getImageUrl();
            Glide.with(binding.getRoot().getContext())
                    .load(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(binding.participantImage);

            // Nombre
            String username = user.getUsername();
            binding.participantName.setText(username != null && !username.isEmpty() ? username : "Usuario");

            // Estado de selección (solo fondo, ya no hay checkbox)
            boolean isSelected = selectedUserUids.contains(userUid);
            int backgroundColor = isSelected
                    ? ContextCompat.getColor(binding.getRoot().getContext(), R.color.light_green)
                    : ContextCompat.getColor(binding.getRoot().getContext(), R.color.white);
            binding.getRoot().setBackgroundColor(backgroundColor);

            // Click: toggle selección
            itemView.setOnClickListener(v -> {
                Log.d("SelectParticipantAdapter", "Item clicked: " + user.getUsername() + ", UID: " + userUid);
                if (listener != null) {
                    listener.onUserSelected(userUid);
                }
                // Actualizar visualmente este item (cambia el fondo)
                notifyItemChanged(getAdapterPosition());
            });
        }
    }
}