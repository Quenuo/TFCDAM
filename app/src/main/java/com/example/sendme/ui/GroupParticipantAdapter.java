package com.example.sendme.ui;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.ItemGroupParticipantBinding;

import java.util.ArrayList;
import java.util.List;

public class GroupParticipantAdapter extends RecyclerView.Adapter<GroupParticipantAdapter.ViewHolder> {

    private List<User> participants = new ArrayList<>();
    private String adminUid;           // ← UID del administrador
    private String currentUserUid;     // ← UID del usuario actual
    private OnRemoveParticipantListener removeListener;

    public interface OnRemoveParticipantListener {
        void onRemoveParticipant(User user);
    }

    public GroupParticipantAdapter(String adminUid, String currentUserUid, OnRemoveParticipantListener listener) {
        this.adminUid = adminUid;
        this.currentUserUid = currentUserUid;
        this.removeListener = listener;
    }

    public void setParticipants(List<User> participants) {
        this.participants = participants != null ? participants : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGroupParticipantBinding binding = ItemGroupParticipantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = participants.get(position);

        // Verificar si es admin o usuario actual por UID
        boolean isAdmin = user.getUid() != null && user.getUid().equals(adminUid);
        boolean isCurrentUser = user.getUid() != null && user.getUid().equals(currentUserUid);

        // Foto de perfil
        String imageUrl = user.getImageUrl();
        Glide.with(holder.binding.getRoot().getContext())
                .load(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : R.drawable.default_profile)
                .circleCrop()
                .error(R.drawable.default_profile)
                .into(holder.binding.participantImage);

        // Nombre
        holder.binding.participantName.setText(user.getUsername() != null ? user.getUsername() : "Usuario");

        // Etiqueta "Administrador"
        if (isAdmin) {
            holder.binding.adminLabel.setText("Administrador");
            holder.binding.adminLabel.setTextColor(Color.parseColor("#25D366")); // Verde WhatsApp
            holder.binding.adminLabel.setVisibility(View.VISIBLE);
        } else {
            holder.binding.adminLabel.setVisibility(View.GONE);
        }

        // Estado
        String status = user.getStatus();
        if (status != null && !status.isEmpty()) {
            holder.binding.participantStatus.setText(status);
            holder.binding.participantStatus.setVisibility(View.VISIBLE);
            holder.binding.participantStatus.setMaxLines(1);
            holder.binding.participantStatus.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            holder.binding.participantStatus.setVisibility(View.GONE);
        }

        // Click: solo el admin puede expulsar a otros (no a sí mismo)
        holder.itemView.setOnClickListener(v -> {
            if (currentUserUid != null && currentUserUid.equals(adminUid) && !isCurrentUser) {
                if (removeListener != null) {
                    removeListener.onRemoveParticipant(user);
                }
            }
            // Si no es admin o es él mismo → no hace nada
        });
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemGroupParticipantBinding binding;

        ViewHolder(ItemGroupParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}