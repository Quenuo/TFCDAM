package com.example.sendme.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemParticipantBinding;

import java.util.ArrayList;
import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder> {
    private List<User> participants = new ArrayList<>();

    public ParticipantAdapter() {
        // Constructor vacío
    }

    public void setParticipants(List<User> participants) {
        this.participants = participants != null ? participants : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemParticipantBinding binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ParticipantViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        User user = participants.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    class ParticipantViewHolder extends RecyclerView.ViewHolder {
        private final ItemParticipantBinding binding;

        ParticipantViewHolder(@NonNull ItemParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(User user) {
            // Cargar imagen de perfil redondeada con Glide
            String imageUrl = user.getImageUrl() != null && !user.getImageUrl().isEmpty() ? user.getImageUrl() : null;
            Glide.with(binding.getRoot().getContext())
                    .load(imageUrl != null ? imageUrl : R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(binding.participantImage);

            // Mostrar nombre de usuario
            binding.participantName.setText(user.getUsername());
        }
    }
}
