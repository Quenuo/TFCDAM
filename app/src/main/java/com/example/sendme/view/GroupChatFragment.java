package com.example.sendme.view;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Message;
import com.example.sendme.databinding.FragmentChatBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.MessageAdapter;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private String groupId;
    private String groupName;
    private String groupIcon;
    private String currentUserUid;
    private MessageAdapter adapter;
    private DatabaseReference messagesRef;
    private ChildEventListener messageListener;
    private Set<String> displayedMessageIds = new HashSet<>();
    private static final String TAG = "GroupChatFragment";
    private NavController navController;

    private List<Message> allMessages = new ArrayList<>(); // Lista completa para búsqueda
    private String currentSearchQuery = "";

    private AlertDialog searchDialog; // ← Dialog para búsqueda

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "Imagen seleccionada: " + uri);
                    uploadImageToImgur(uri);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    pickImageLauncher.launch("image/*");
                } else {
                    Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = NavHostFragment.findNavController(this);

        if (FirebaseManager.getInstance().getAuth().getCurrentUser() == null) {
            Log.e(TAG, "Usuario no autenticado");
            return;
        }

        currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser().getUid();

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
            groupName = getArguments().getString("groupName");
            groupIcon = getArguments().getString("groupIcon");
        }

        if (groupId == null) {
            Log.e(TAG, "groupId es null");
            navController.popBackStack();
            return;
        }

        if (groupName == null || groupName.trim().isEmpty()) {
            groupName = "Grupo";
        }

        // Verificar membresía antes de cargar nada
        checkMembershipAndLoad();

        // Manejo del botón atrás del sistema (cierra dialog de búsqueda si está abierto)
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dismiss();
                } else {
                    navController.popBackStack(R.id.chatListFragment, false);
                }
            }
        });

        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    binding.emojiIcon.setVisibility(View.GONE);
                    binding.attachIcon.setVisibility(View.GONE);
                    binding.sendIcon.setVisibility(View.VISIBLE);
                } else {
                    binding.emojiIcon.setVisibility(View.VISIBLE);
                    binding.attachIcon.setVisibility(View.VISIBLE);
                    binding.sendIcon.setVisibility(View.GONE);
                }
            }
        });

        // Al pulsar el emoji: abrir el teclado (forzado para que siempre se muestre)
        binding.emojiIcon.setOnClickListener(v -> {
            binding.messageInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(binding.messageInput, InputMethodManager.SHOW_FORCED);
        });

        // Click en la lupa → abrir mini ventana (dialog) de búsqueda
        binding.searchIcon.setOnClickListener(v -> showSearchDialog());
    }

    private void showSearchDialog() {
        if (searchDialog != null && searchDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        final EditText input = new EditText(requireContext());
        input.setHint("Introduce un texto para buscar mensajes");
        input.setPadding(48, 48, 48, 32);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        builder.setView(input);
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());

        searchDialog = builder.create();

        // Filtrado y resaltado en tiempo real
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase();
                currentSearchQuery = query;
                performSearch(query);
                if (adapter != null) {
                    adapter.setSearchQuery(query);
                }
            }
        });

        // Al cerrar el dialog
        searchDialog.setOnDismissListener(dialog -> {
            currentSearchQuery = "";
            performSearch("");
            if (adapter != null) {
                adapter.setSearchQuery("");
            }
            searchDialog = null;
        });

        searchDialog.show();

        // Abrir teclado automáticamente
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    /** Verifica si el usuario sigue siendo miembro del grupo... */
    private void checkMembershipAndLoad() {
        DatabaseReference participantsRef = FirebaseManager.getInstance().getDatabase()
                .getReference("chats").child(groupId).child("participants");

        participantsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                Map<String, Boolean> participants = snapshot.getValue(new GenericTypeIndicator<Map<String, Boolean>>() {});

                if (participants == null || !participants.containsKey(currentUserUid)) {
                    Toast.makeText(requireContext(), "Ya no eres miembro de este grupo", Toast.LENGTH_LONG).show();
                    navController.popBackStack(R.id.chatListFragment, false);
                    return;
                }

                // Si eres miembro, cargar UI completa
                setupHeader();
                setupRecyclerView();
                setupEvents();

                // Cargar participantes y adapter
                loadParticipantsAndSetupAdapter();

                // Mensajes (se cargan dentro de loadParticipantsAndSetupAdapter)
                messagesRef = FirebaseManager.getInstance().getDatabase()
                        .getReference("chats").child(groupId).child("messages");

                resetUnreadCount();

                // Listener continuo para detectar si te eliminan mientras estás dentro
                participantsRef.addValueEventListener(membershipListener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error verificando membresía", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Listener que detecta si te eliminan del grupo mientras estás en el chat
    private final ValueEventListener membershipListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!isAdded()) return;
            Map<String, Boolean> participants = snapshot.getValue(new GenericTypeIndicator<Map<String, Boolean>>() {});
            if (participants == null || !participants.containsKey(currentUserUid)) {
                Toast.makeText(requireContext(), "Has sido eliminado del grupo", Toast.LENGTH_LONG).show();
                navController.popBackStack(R.id.chatListFragment, false);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {}
    };

    private void setupHeader() {
        binding.contactName.setText(groupName);

        if (groupIcon != null && !groupIcon.isEmpty()) {
            Glide.with(requireContext())
                    .load(groupIcon)
                    .placeholder(R.drawable.ic_group)
                    .error(R.drawable.ic_group)
                    .circleCrop()
                    .into(binding.profileImage);
        } else {
            DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId);
            chatRef.child("groupIcon").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!isAdded()) return;
                    String url = snapshot.getValue(String.class);
                    if (url != null && !url.isEmpty()) {
                        Glide.with(requireContext()).load(url).circleCrop().into(binding.profileImage);
                    } else {
                        binding.profileImage.setImageResource(R.drawable.ic_group);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (isAdded()) binding.profileImage.setImageResource(R.drawable.ic_group);
                }
            });
        }

        binding.contactName.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("chatId", groupId);
            bundle.putString("groupName", groupName);
            bundle.putString("groupIcon", groupIcon);
            Navigation.findNavController(v).navigate(R.id.action_groupChatFragment_to_groupDetailFragment, bundle);
        });
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.messagesRecyclerView.setLayoutManager(layoutManager);
    }

    private void setupEvents() {
        binding.attachIcon.setOnClickListener(v -> openGallery());

        binding.sendIcon.setOnClickListener(v -> {
            String content = binding.messageInput.getText().toString().trim();
            if (!content.isEmpty()) {
                sendMessage(content, null);
                binding.messageInput.setText("");
            }
        });
    }

    private void loadParticipantsAndSetupAdapter() {
        DatabaseReference participantsRef = FirebaseManager.getInstance().getDatabase()
                .getReference("chats").child(groupId).child("participants");

        participantsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GenericTypeIndicator<Map<String, Boolean>> t = new GenericTypeIndicator<Map<String, Boolean>>() {};
                Map<String, Boolean> participants = snapshot.getValue(t);

                if (participants == null || participants.isEmpty()) {
                    Toast.makeText(requireContext(), "Error: grupo sin participantes", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, String> uidToNameMap = new HashMap<>();
                AtomicInteger loadedCount = new AtomicInteger(0);
                int total = participants.size();

                for (String participantUid : participants.keySet()) {
                    FirebaseManager.getInstance().getFirestore()
                            .collection("users")
                            .document(participantUid)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String name = documentSnapshot.getString("username");
                                if (name == null || name.trim().isEmpty()) name = "Usuario";
                                uidToNameMap.put(participantUid, name);

                                if (loadedCount.incrementAndGet() == total) {
                                    finalizeAdapterAndLoadMessages(uidToNameMap);
                                }
                            })
                            .addOnFailureListener(e -> {
                                uidToNameMap.put(participantUid, "Usuario");
                                if (loadedCount.incrementAndGet() == total) {
                                    finalizeAdapterAndLoadMessages(uidToNameMap);
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Error cargando participantes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finalizeAdapterAndLoadMessages(Map<String, String> uidToNameMap) {
        adapter = new MessageAdapter(currentUserUid, navController, uidToNameMap);
        binding.messagesRecyclerView.setAdapter(adapter);

        loadAllMessages();
        listenForMessages();
    }

    private void openGallery() {
        String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                ? android.Manifest.permission.READ_MEDIA_IMAGES : android.Manifest.permission.READ_EXTERNAL_STORAGE;

        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void sendMessage(String content, String imageUrl) {
        if (messagesRef == null) return;

        Message message = new Message(currentUserUid, content, imageUrl, System.currentTimeMillis());
        String messageId = messagesRef.push().getKey();
        message.setId(messageId);

        if (messageId != null && isAdded()) {
            allMessages.add(message);
            if (adapter != null) {
                adapter.addMessage(message);
                displayedMessageIds.add(messageId);
                binding.messagesRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        }

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mensaje enviado en grupo");
                    updateChatMetadata(content != null ? content : "Imagen");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error al enviar mensaje", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error enviando mensaje: " + e.getMessage());
                });
    }

    private void updateChatMetadata(String lastMessage) {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("lastMessageTimestamp", System.currentTimeMillis());

        chatRef.child("participants").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GenericTypeIndicator<Map<String, Boolean>> t = new GenericTypeIndicator<Map<String, Boolean>>() {};
                Map<String, Boolean> participants = snapshot.getValue(t);
                if (participants != null) {
                    for (String uid : participants.keySet()) {
                        if (!uid.equals(currentUserUid)) {
                            chatRef.child("unreadCount").child(uid).runTransaction(new Transaction.Handler() {
                                @NonNull
                                @Override
                                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                    Integer count = currentData.getValue(Integer.class);
                                    if (count == null) count = 0;
                                    currentData.setValue(count + 1);
                                    return Transaction.success(currentData);
                                }

                                @Override
                                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
                            });
                        } else {
                            updates.put("unreadCount/" + currentUserUid, 0);
                        }
                    }
                }
                chatRef.updateChildren(updates);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error actualizando unread: " + error.getMessage());
            }
        });
    }

    private void resetUnreadCount() {
        if (currentUserUid != null && groupId != null) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId)
                    .child("unreadCount").child(currentUserUid)
                    .setValue(0);
        }
    }

    private void loadAllMessages() {
        if (messagesRef == null) return;

        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                allMessages.clear();
                displayedMessageIds.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    Message msg = snap.getValue(Message.class);
                    if (msg != null) {
                        String msgId = snap.getKey();
                        if (msg.getId() == null) msg.setId(msgId);
                        allMessages.add(msg);
                        displayedMessageIds.add(msgId);
                    }
                }

                if (adapter != null) {
                    adapter.setMessages(new ArrayList<>(allMessages));
                    binding.messagesRecyclerView.scrollToPosition(allMessages.size() - 1);
                }
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
                if (message == null) return;

                String messageId = snapshot.getKey();
                if (message.getId() == null) message.setId(messageId);

                if (displayedMessageIds.contains(messageId)) return;

                allMessages.add(message);
                displayedMessageIds.add(messageId);

                if (!message.getSender().equals(currentUserUid)) {
                    resetUnreadCount();
                }

                boolean matches = currentSearchQuery.isEmpty() ||
                        (message.getContent() != null && message.getContent().toLowerCase().contains(currentSearchQuery));

                if (matches && adapter != null) {
                    adapter.addMessage(message);
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

    private void uploadImageToImgur(Uri imageUri) {
        ImgurApiClient.getInstance().uploadImage(imageUri, requireContext().getContentResolver(), new ImgurApiClient.UploadCallback() {
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

    private void performSearch(String query) {
        if (adapter == null) return;

        List<Message> filtered = new ArrayList<>();

        if (query.isEmpty()) {
            filtered.addAll(allMessages);
        } else {
            for (Message msg : allMessages) {
                String content = msg.getContent();
                if (content != null && content.toLowerCase().contains(query)) {
                    filtered.add(msg);
                }
            }
        }

        adapter.setMessages(filtered);
        adapter.setSearchQuery(query);

        if (filtered.isEmpty()) {
            binding.messagesRecyclerView.scrollToPosition(0);
        } else {
            binding.messagesRecyclerView.scrollToPosition(filtered.size() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (messagesRef != null && messageListener != null) {
            messagesRef.removeEventListener(messageListener);
        }

        // Remover listener de membresía
        if (groupId != null) {
            DatabaseReference participantsRef = FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId).child("participants");
            participantsRef.removeEventListener(membershipListener);
        }

        allMessages.clear();
        displayedMessageIds.clear();
        binding = null;
    }
}