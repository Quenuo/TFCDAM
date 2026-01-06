package com.example.sendme.view;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentGroupDetailBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.GroupParticipantAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupDetailFragment extends Fragment {

    private FragmentGroupDetailBinding binding;
    private String groupId;
    private String currentUserUid;
    private String adminUid;
    private boolean isAdmin;
    private GroupParticipantAdapter adapter;
    private List<User> participantsList = new ArrayList<>();
    private NavController navController;
    private ValueEventListener groupDataListener;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isAdmin) {
                    uploadAndUpdateGroupPhoto(uri);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGroupDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Toast.makeText(requireContext(), "Error de autenticación", Toast.LENGTH_SHORT).show();
            return;
        }

        navController = NavHostFragment.findNavController(this);

        if (getArguments() != null) {
            groupId = getArguments().getString("chatId");
        }

        if (groupId == null) {
            navController.popBackStack();
            return;
        }

        loadGroupData();
        setupClickListeners();

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navController.navigateUp();
            }
        });
    }

    private void setupClickListeners() {
        binding.groupImage.setOnClickListener(v -> {
            if (isAdmin) {
                pickImageLauncher.launch("image/*");
            } else {
                Toast.makeText(requireContext(), "Solo el administrador puede cambiar la foto", Toast.LENGTH_SHORT).show();
            }
        });

        binding.groupNameText.setOnClickListener(v -> {
            if (isAdmin) {
                showEditGroupNameDialog();
            } else {
                Toast.makeText(requireContext(), "Solo el administrador puede cambiar el nombre", Toast.LENGTH_SHORT).show();
            }
        });

        binding.addParticipantsButton.setOnClickListener(v -> {
            if (!isAdmin) {
                Toast.makeText(requireContext(), "Solo el administrador puede añadir participantes", Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putString("groupId", groupId);
            navController.navigate(R.id.action_groupDetailFragment_to_selectParticipantsFragment, bundle);
        });

        binding.leaveGroupButton.setOnClickListener(v -> showLeaveGroupDialog());
    }

    private void loadGroupData() {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);

        groupDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                Chat chat = snapshot.getValue(Chat.class);
                if (chat == null) return;

                adminUid = chat.getAdminUid();
                isAdmin = currentUserUid.equals(adminUid);

                String groupIcon = chat.getGroupIcon();
                if (groupIcon != null && !groupIcon.isEmpty()) {
                    Glide.with(requireContext())
                            .load(groupIcon)
                            .placeholder(R.drawable.ic_group)
                            .error(R.drawable.ic_group)
                            .circleCrop()
                            .into(binding.groupImage);
                } else {
                    binding.groupImage.setImageResource(R.drawable.ic_group);
                }

                String name = chat.getGroupName();
                if (name == null || name.trim().isEmpty()) name = "Grupo";
                binding.groupNameText.setText(name);

                int count = chat.getParticipants() != null ? chat.getParticipants().size() : 0;
                binding.participantsCountText.setText("Grupo · " + count + " participantes");

                if (chat.getParticipants() != null) {
                    loadParticipantsList(new ArrayList<>(chat.getParticipants().keySet()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error cargando grupo", Toast.LENGTH_SHORT).show();
                }
            }
        };

        chatRef.addValueEventListener(groupDataListener);
    }

    /**
     * Carga los participantes sin duplicados usando un Map (clave = UID).
     */
    private void loadParticipantsList(List<String> participantUids) {
        participantsList.clear();

        Map<String, User> userMap = new HashMap<>();

        adapter = new GroupParticipantAdapter(adminUid, currentUserUid, this::showRemoveParticipantDialog);
        binding.participantsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.participantsRecycler.setAdapter(adapter);

        AtomicInteger loaded = new AtomicInteger(0);
        int total = participantUids.size();

        if (total == 0) {
            adapter.setParticipants(participantsList);
            return;
        }

        for (String uid : participantUids) {
            FirebaseManager.getInstance().getFirestore()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        User user = doc.toObject(User.class);
                        if (user == null) {
                            user = new User();
                            user.setUsername("Usuario");
                            user.setUid(uid);
                        }

                        userMap.put(uid, user);

                        if (loaded.incrementAndGet() == total) {
                            participantsList = new ArrayList<>(userMap.values());

                            // Admin primero
                            participantsList.sort((u1, u2) -> {
                                if (u1.getUid().equals(adminUid)) return -1;
                                if (u2.getUid().equals(adminUid)) return 1;
                                return 0;
                            });

                            adapter.setParticipants(participantsList);
                        }
                    })
                    .addOnFailureListener(e -> {
                        User fallback = new User();
                        fallback.setUsername("Usuario");
                        fallback.setUid(uid);
                        userMap.put(uid, fallback);

                        if (loaded.incrementAndGet() == total) {
                            participantsList = new ArrayList<>(userMap.values());
                            participantsList.sort((u1, u2) -> {
                                if (u1.getUid().equals(adminUid)) return -1;
                                if (u2.getUid().equals(adminUid)) return 1;
                                return 0;
                            });

                            adapter.setParticipants(participantsList);
                        }
                    });
        }
    }

    private void showRemoveParticipantDialog(User userToRemove) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar participante")
                .setMessage("¿Eliminar a " + userToRemove.getUsername() + " del grupo?")
                .setPositiveButton("Eliminar", (d, w) -> removeParticipantFromGroup(userToRemove))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void removeParticipantFromGroup(User userToRemove) {
        String uidToRemove = userToRemove.getUid();
        if (uidToRemove == null) return;

        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);
        DatabaseReference userChatsRef = FirebaseManager.getInstance().getDatabase().getReference("user-chats").child(uidToRemove);

        Map<String, Object> updates = new HashMap<>();
        updates.put("participants/" + uidToRemove, null);
        updates.put("unreadCount/" + uidToRemove, null);

        if (uidToRemove.equals(adminUid)) {
            chatRef.child("participants").get().addOnSuccessListener(snapshot -> {
                Map<String, Boolean> remaining = snapshot.getValue(new GenericTypeIndicator<Map<String, Boolean>>() {});
                if (remaining != null && remaining.size() > 1) {
                    remaining.remove(uidToRemove);
                    if (!remaining.isEmpty()) {
                        List<String> keys = new ArrayList<>(remaining.keySet());
                        String newAdminUid = keys.get(new Random().nextInt(keys.size()));
                        updates.put("adminUid", newAdminUid);
                    }
                }
                applyGroupUpdates(updates, userChatsRef);
            });
        } else {
            applyGroupUpdates(updates, userChatsRef);
        }
    }

    private void applyGroupUpdates(Map<String, Object> updates, DatabaseReference userChatsRef) {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);

        chatRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                userChatsRef.child(groupId).removeValue();

                // Check si el grupo quedó vacío (en background)
                chatRef.get().addOnSuccessListener(snapshot -> {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat == null || chat.getParticipants() == null || chat.getParticipants().isEmpty()) {
                        chatRef.removeValue();
                    }
                });

                Toast.makeText(requireContext(), "Participante eliminado", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadAndUpdateGroupPhoto(Uri uri) {
        ImgurApiClient.getInstance().uploadImage(uri, requireContext().getContentResolver(),
                new ImgurApiClient.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        FirebaseManager.getInstance().getDatabase()
                                .getReference("chats").child(groupId)
                                .child("groupIcon")
                                .setValue(imageUrl)
                                .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Foto actualizada", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(requireContext(), "Error subiendo foto: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showEditGroupNameDialog() {
        EditText input = new EditText(requireContext());
        input.setText(binding.groupNameText.getText());
        input.setSelection(input.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Cambiar nombre del grupo")
                .setView(input)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId)
                    .child("groupName")
                    .setValue(newName)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(requireContext(), "Nombre actualizado", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });
    }

    private void showLeaveGroupDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Salir del grupo")
                .setMessage("¿Estás seguro de que quieres salir del grupo?")
                .setPositiveButton("Salir", (d, w) -> leaveGroup())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void leaveGroup() {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);
        DatabaseReference userChatsRef = FirebaseManager.getInstance().getDatabase().getReference("user-chats").child(currentUserUid);

        Map<String, Object> updates = new HashMap<>();
        updates.put("participants/" + currentUserUid, null);
        updates.put("unreadCount/" + currentUserUid, null);

        if (currentUserUid.equals(adminUid)) {
            chatRef.child("participants").get().addOnSuccessListener(snapshot -> {
                Map<String, Boolean> remaining = snapshot.getValue(new GenericTypeIndicator<Map<String, Boolean>>() {});
                if (remaining != null && remaining.size() > 1) {
                    remaining.remove(currentUserUid);
                    if (!remaining.isEmpty()) {
                        List<String> keys = new ArrayList<>(remaining.keySet());
                        String newAdmin = keys.get(new Random().nextInt(keys.size()));
                        updates.put("adminUid", newAdmin);
                    }
                }
                applyLeaveUpdates(updates, userChatsRef);
            });
        } else {
            applyLeaveUpdates(updates, userChatsRef);
        }
    }

    private void applyLeaveUpdates(Map<String, Object> updates, DatabaseReference userChatsRef) {
        DatabaseReference chatRef = FirebaseManager.getInstance().getDatabase().getReference("chats").child(groupId);

        chatRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                userChatsRef.child(groupId).removeValue();

                // Check si quedó vacío y borrarlo (en background)
                chatRef.get().addOnSuccessListener(snapshot -> {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat == null || chat.getParticipants() == null || chat.getParticipants().isEmpty()) {
                        chatRef.removeValue();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Has salido y el grupo se ha eliminado", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Has salido del grupo", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                // Volver a la lista inmediatamente (el listener en ChatListFragment removerá el grupo en tiempo real)
                navController.popBackStack(R.id.chatListFragment, false);
            } else {
                Toast.makeText(requireContext(), "Error al salir del grupo", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (groupDataListener != null && groupId != null) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId)
                    .removeEventListener(groupDataListener);
        }
        binding = null;
    }
}