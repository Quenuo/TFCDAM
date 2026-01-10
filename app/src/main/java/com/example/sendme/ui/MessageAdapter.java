// Paquete de la clase
package com.example.sendme.ui;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Message;
import com.example.sendme.databinding.ItemMessageBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptador para mostrar los mensajes (texto o imagen) en un RecyclerView.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messages = new ArrayList<>();
    private String currentUserUid; // ← UID del usuario actual
    private static final String TAG = "MessageAdapter";
    private NavController navController;
    private final boolean isGroupChat;
    private final Map<String, String> uidToNameMap; // ← Map UID → Nombre (para grupos)

    private static String searchQuery = ""; // ← Query de búsqueda actual (para resaltar)

    // Constructor para chats individuales
    public MessageAdapter(String currentUserUid, NavController navController) {
        this.currentUserUid = currentUserUid;
        this.navController = navController;
        this.isGroupChat = false;
        this.uidToNameMap = new HashMap<>();
    }

    // Constructor para grupos
    public MessageAdapter(String currentUserUid, NavController navController, Map<String, String> uidToNameMap) {
        this.currentUserUid = currentUserUid;
        this.navController = navController;
        this.isGroupChat = true;
        this.uidToNameMap = uidToNameMap != null ? uidToNameMap : new HashMap<>();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMessageBinding binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding, navController, isGroupChat);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        boolean isSentByCurrentUser = currentUserUid != null && currentUserUid.equals(message.getSender());

        String senderName = null;
        if (isGroupChat && !isSentByCurrentUser) {
            senderName = uidToNameMap.get(message.getSender());
            if (senderName == null || senderName.isEmpty()) {
                senderName = "Usuario";
            }
        }

        holder.bind(message, isSentByCurrentUser, senderName);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        Log.d(TAG, "Mensaje añadido al adapter");
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
        Log.d(TAG, "Mensajes establecidos en adapter: " + this.messages.size());
    }

    /** Nuevo método: establece la query de búsqueda y fuerza re-bind para resaltar */
    public void setSearchQuery(String query) {
        this.searchQuery = (query == null || query.trim().isEmpty()) ? "" : query.toLowerCase().trim();
        notifyDataSetChanged();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemMessageBinding binding;
        private final NavController navController;
        private final boolean isGroupChat;

        MessageViewHolder(ItemMessageBinding binding, NavController navController, boolean isGroupChat) {
            super(binding.getRoot());
            this.binding = binding;
            this.navController = navController;
            this.isGroupChat = isGroupChat;
        }

        void bind(Message message, boolean isSentByCurrentUser, String senderName) {
            // Nombre del remitente (solo en grupos y si no es mío)
            if (isGroupChat && !isSentByCurrentUser && senderName != null) {
                binding.senderNameText.setText(senderName);
                binding.senderNameText.setVisibility(View.VISIBLE);
            } else {
                binding.senderNameText.setVisibility(View.GONE);
            }

            // Texto del mensaje + resaltado si hay búsqueda activa
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                String content = message.getContent();

                if (!searchQuery.isEmpty()) {
                    SpannableString spannable = new SpannableString(content);
                    String lowerContent = content.toLowerCase();
                    int index = lowerContent.indexOf(searchQuery);

                    while (index >= 0) {
                        // Fondo amarillo claro
                        spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#FFFF88")),
                                index, index + searchQuery.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        // Texto negro para buena legibilidad
                        spannable.setSpan(new ForegroundColorSpan(Color.BLACK),
                                index, index + searchQuery.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                        index = lowerContent.indexOf(searchQuery, index + searchQuery.length());
                    }

                    binding.messageText.setText(spannable);
                } else {
                    binding.messageText.setText(content);
                }

                binding.messageText.setVisibility(View.VISIBLE);
            } else {
                binding.messageText.setVisibility(View.GONE);
            }

            // Imagen
            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                binding.messageImage.setVisibility(View.VISIBLE);
                Glide.with(binding.getRoot().getContext())
                        .load(message.getImageUrl())
                        .error(R.drawable.ic_close_white_24dp)
                        .into(binding.messageImage);

                binding.messageImage.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putString("imageUrl", message.getImageUrl());
                    navController.navigate(R.id.imageViewerFragment, bundle);
                });

                binding.messageImage.setOnLongClickListener(v -> {
                    showDownloadDialog(message.getImageUrl());
                    return true;
                });
            } else {
                binding.messageImage.setVisibility(View.GONE);
            }

            // Hora
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            binding.timestampText.setText(sdf.format(new Date(message.getTimestamp())));

            // Estilo del bubble según si es enviado o recibido
            if (isSentByCurrentUser) {
                binding.messageBubble.setBackgroundResource(R.drawable.message_bubble_sent);
                binding.getRoot().setGravity(Gravity.END);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.messageBubble.getLayoutParams();
                params.leftMargin = dpToPx(64);
                params.rightMargin = dpToPx(12);
                binding.messageBubble.setLayoutParams(params);
            } else {
                binding.messageBubble.setBackgroundResource(R.drawable.message_bubble_received);
                binding.getRoot().setGravity(Gravity.START);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.messageBubble.getLayoutParams();
                params.leftMargin = dpToPx(12);
                params.rightMargin = dpToPx(64);
                binding.messageBubble.setLayoutParams(params);
            }
        }

        private int dpToPx(int dp) {
            float density = binding.getRoot().getContext().getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        private void showDownloadDialog(String imageUrl) {
            new AlertDialog.Builder(binding.getRoot().getContext())
                    .setTitle("Descargar imagen")
                    .setMessage("¿Deseas descargar esta imagen?")
                    .setPositiveButton("Sí", (d, w) -> downloadImage(imageUrl))
                    .setNegativeButton("No", null)
                    .show();
        }

        private void downloadImage(String imageUrl) {
            DownloadManager dm = (DownloadManager) binding.getRoot().getContext()
                    .getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(imageUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    "SendMe/" + System.currentTimeMillis() + ".jpg");
            dm.enqueue(request);
            Toast.makeText(binding.getRoot().getContext(), "Descarga iniciada", Toast.LENGTH_SHORT).show();
        }
    }
}
