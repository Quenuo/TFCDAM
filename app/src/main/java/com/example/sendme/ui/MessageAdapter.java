// Paquete de la clase
package com.example.sendme.ui;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Message;
import com.example.sendme.databinding.ItemMessageBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador para mostrar los mensajes (texto o imagen) en un RecyclerView.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages = new ArrayList<>();  // Lista de mensajes a mostrar
    private String currentUserPhone;                     // Teléfono del usuario actual
    private static final String TAG = "MessageAdapter";  // TAG para logs
    private NavController navController;                 // Para navegar entre fragmentos (ej. visor de imagen)

    // Constructor que recibe el teléfono del usuario actual y el NavController para navegación
    public MessageAdapter(String currentUserPhone, NavController navController) {
        this.currentUserPhone = currentUserPhone;
        this.navController = navController;
    }

    // Crea un nuevo ViewHolder inflando el layout del ítem de mensaje
    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemMessageBinding binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding, navController);
    }

    // Llama al método bind para enlazar los datos del mensaje con la vista
    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        Log.d(TAG, "Binding message: " + (message.getContent() != null ? message.getContent() : "Image message, URL: " + message.getImageUrl()) + ", Sender: " + message.getSender());

        // Verifica si el mensaje fue enviado por el usuario actual
        boolean isSentByCurrentUser = currentUserPhone.equals(message.getSender());
        holder.bind(message, isSentByCurrentUser);
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    // Añade un nuevo mensaje y notifica al adaptador para actualizar la vista
    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
        Log.d(TAG, "Message added to adapter: " + (message.getContent() != null ? message.getContent() : "Image message, URL: " + message.getImageUrl()));
    }

    // Reemplaza todos los mensajes y actualiza la vista completa
    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
        Log.d(TAG, "Messages set in adapter: " + this.messages.size());
    }

    /**
     * ViewHolder para un mensaje individual.
     */
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageBinding binding;
        private final NavController navController;

        // Constructor que recibe el binding del ítem y el NavController
        MessageViewHolder(ItemMessageBinding binding, NavController navController) {
            super(binding.getRoot());
            this.binding = binding;
            this.navController = navController;
        }

        /**
         * Enlaza los datos del mensaje con la vista, ajustando contenido, imagen, hora y estilo.
         */
        void bind(Message message, boolean isSentByCurrentUser) {
            // Si el mensaje tiene texto, se muestra
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                binding.messageText.setVisibility(View.VISIBLE);
                binding.messageText.setText(message.getContent());
            } else {
                binding.messageText.setVisibility(View.GONE);
            }

            // Si el mensaje tiene una imagen, se carga con Glide y se configuran eventos
            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                binding.messageImage.setVisibility(View.VISIBLE);
                Glide.with(binding.getRoot().getContext())
                        .load(message.getImageUrl())
                        .error(R.drawable.default_profile)
                        .into(binding.messageImage);

                // Click simple para abrir la imagen en un nuevo fragmento
                binding.messageImage.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putString("imageUrl", message.getImageUrl());
                    navController.navigate(R.id.action_chatFragment_to_imageViewerFragment, bundle);
                });

                // Click largo para mostrar el diálogo de descarga
                binding.messageImage.setOnLongClickListener(v -> {
                    showDownloadDialog(message.getImageUrl());
                    return true;
                });
            } else {
                binding.messageImage.setVisibility(View.GONE);
            }

            // Formatear y mostrar la hora del mensaje
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            binding.timestampText.setText(sdf.format(message.getTimestamp()));

            // Ajustar alineación y fondo del mensaje dependiendo del remitente
            if (isSentByCurrentUser) {
                binding.getRoot().setGravity(android.view.Gravity.END);
                binding.messageBubble.setBackgroundResource(R.drawable.message_bubble_sent);
            } else {
                binding.getRoot().setGravity(android.view.Gravity.START);
                binding.messageBubble.setBackgroundResource(R.drawable.message_bubble_received);
            }
        }

        /**
         * Muestra un diálogo de confirmación antes de descargar la imagen.
         */
        private void showDownloadDialog(String imageUrl) {
            new AlertDialog.Builder(binding.getRoot().getContext())
                    .setTitle("Descargar imagen")
                    .setMessage("¿Deseas descargar esta imagen?")
                    .setPositiveButton("Sí", (dialog, which) -> downloadImage(imageUrl))
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();
        }

        /**
         * Descarga la imagen usando el DownloadManager de Android.
         */
        private void downloadImage(String imageUrl) {
            DownloadManager downloadManager = (DownloadManager) binding.getRoot().getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(imageUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "SendMe/" + System.currentTimeMillis() + ".jpg");
            request.allowScanningByMediaScanner();

            try {
                downloadManager.enqueue(request);
                Toast.makeText(binding.getRoot().getContext(), "Descarga iniciada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error downloading image: " + e.getMessage());
                Toast.makeText(binding.getRoot().getContext(), "Error al descargar la imagen", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
