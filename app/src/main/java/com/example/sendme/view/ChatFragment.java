package com.example.sendme.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Message;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentChatBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.MessageAdapter;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragmento principal para la interfaz de chat.
 * Gestiona el envío/recepción de mensajes, la visualización y la interacción del usuario.
 */
public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private MessageAdapter adapter;
    private String chatId;
    private User otherUser; // Para chats 1:1
    private String groupName;
    private boolean isGroup;
    private String currentUserUid;
    private DatabaseReference messagesRef;
    private ChildEventListener messageListener;
    private static final String TAG = "ChatFragment";
    private List<String> displayedMessageIds = new ArrayList<>();
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;
    private NavController navController;

    public ChatFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        navController = NavHostFragment.findNavController(this);

        if (getArguments() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                otherUser = getArguments().getParcelable("user", User.class);
            } else {
                otherUser = getArguments().getParcelable("user");
            }
            chatId = getArguments().getString("chatId");
            isGroup = getArguments().getBoolean("isGroup", false);
            groupName = getArguments().getString("groupName");
        }

        if (chatId == null) {
            Log.e(TAG, "chatId nulo");
            if (isAdded()) navController.popBackStack();
            return;
        }

        if (!isGroup && otherUser == null) {
            Log.e(TAG, "otherUser nulo en chat 1:1");
            if (isAdded()) navController.popBackStack();
            return;
        }

        if (isGroup && (groupName == null || groupName.isEmpty())) {
            groupName = "Grupo";
        }

        currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Log.e(TAG, "UID del usuario actual es null");
            if (isAdded()) navController.popBackStack();
            return;
        }

        messagesRef = FirebaseManager.getInstance().getDatabase()
                .getReference("chats").child(chatId).child("messages");

        // Launchers de permisos e imágenes
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openGallery();
            else handlePermissionDenied();
        });

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadImageToImgur(uri);
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (chatId == null || currentUserUid == null) {
            if (isAdded()) navController.popBackStack();
            return;
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navController.popBackStack(R.id.chatListFragment, false);
            }
        });

        // Header para chat 1:1
        if (!isGroup && otherUser != null) {
            binding.contactName.setText(otherUser.getUsername());

            String imageUrl = otherUser.getImageUrl();
            Glide.with(this)
                    .load(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(binding.profileImage);

            binding.contactName.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable("user", otherUser);
                Navigation.findNavController(v).navigate(R.id.action_chatFragment_to_userDetailFragment, bundle);
            });
        } else if (isGroup) {
            binding.contactName.setText(groupName);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.messagesRecyclerView.setLayoutManager(layoutManager);

        adapter = new MessageAdapter(currentUserUid, navController);
        binding.messagesRecyclerView.setAdapter(adapter);

        resetUnreadCount();
        loadAllMessages();
        listenForMessages();

        binding.sendIcon.setOnClickListener(v -> {
            String content = binding.messageInput.getText().toString().trim();
            if (!content.isEmpty()) {
                sendMessage(content, null);
                binding.messageInput.setText("");
            }
        });

        binding.attachIcon.setOnClickListener(v -> openGallery());
    }

    private void sendMessage(String content, String imageUrl) {
        if (messagesRef == null) return;

        Message message = new Message(currentUserUid, content, imageUrl, System.currentTimeMillis());
        String messageId = messagesRef.push().getKey();
        message.setId(messageId);

        if (messageId != null && isAdded()) {
            adapter.addMessage(message);
            displayedMessageIds.add(messageId);
            binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mensaje enviado correctamente");
                    updateChatMetadata(content != null ? content : "Imagen");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error al enviar mensaje", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error enviando mensaje: " + e.getMessage());
                });
    }

    private void resetUnreadCount() {
        if (currentUserUid != null && chatId != null) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(chatId)
                    .child("unreadCount").child(currentUserUid)
                    .setValue(0);
        }
    }

    private void updateChatMetadata(String lastMessage) {
        if (chatId == null) return;

        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(chatId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());

        if (!isGroup && otherUser != null) {
            String otherUid = otherUser.getUid();
            updates.put("unreadCount/" + otherUid, ServerValue.increment(1));
            updates.put("unreadCount/" + currentUserUid, 0);
        }

        chatRef.updateChildren(updates);
    }

    private void loadAllMessages() {
        if (messagesRef == null) return;

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                List<Message> messages = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Message msg = snap.getValue(Message.class);
                    if (msg != null && msg.getId() != null) {
                        messages.add(msg);
                        displayedMessageIds.add(msg.getId());
                    }
                }
                adapter.setMessages(messages);
                binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error cargando mensajes: " + error.getMessage());
            }
        });
    }

    private void listenForMessages() {
        if (messagesRef == null) return;

        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (!isAdded()) return;

                Message message = snapshot.getValue(Message.class);
                if (message != null && message.getId() != null && !displayedMessageIds.contains(message.getId())) {
                    if (!message.getSender().equals(currentUserUid)) {
                        resetUnreadCount();
                    }
                    adapter.addMessage(message);
                    displayedMessageIds.add(message.getId());
                    binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error escuchando mensajes: " + error.getMessage());
            }
        };

        messagesRef.orderByChild("timestamp").addChildEventListener(messageListener);
    }

    private void openGallery() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void handlePermissionDenied() {
        Toast.makeText(requireContext(), "Permiso denegado para acceder a la galería", Toast.LENGTH_SHORT).show();
    }

    private void uploadImageToImgur(Uri imageUri) {
        ImgurApiClient.getInstance().uploadImage(imageUri, requireContext().getContentResolver(),
                new ImgurApiClient.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(requireContext(), "Imagen subida", Toast.LENGTH_SHORT).show();
                            sendMessage(null, imageUrl);
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(requireContext(), "Error al subir imagen: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesRef != null && messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }
        binding = null;
        displayedMessageIds.clear();
    }
}