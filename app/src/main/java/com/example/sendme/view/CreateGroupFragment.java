package com.example.sendme.view;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sendme.R;
import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentCreateGroupBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.ParticipantAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragmento para crear un grupo de chat.
 * Recibe participantes seleccionados desde SelectParticipantsFragment y los muestra en RecyclerView.
 */
public class CreateGroupFragment extends Fragment {

    private FragmentCreateGroupBinding binding;
    private NavController navController;
    private List<User> participants = new ArrayList<>();
    private ParticipantAdapter participantAdapter;
    private static final String TAG = "CreateGroupFragment";
    private Uri groupPhotoUri;
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    groupPhotoUri = uri;
                    binding.groupImage.setImageURI(uri);
                    Log.d(TAG, "Foto de grupo seleccionada: " + uri);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateGroupBinding.inflate(inflater, container, false);
        navController = NavHostFragment.findNavController(this);

        // RecyclerView horizontal para participantes
        participantAdapter = new ParticipantAdapter();
        binding.participantsRecycler.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.participantsRecycler.setAdapter(participantAdapter);
        updateParticipantsCount();

        // Botón de retorno visible + físico
        binding.backButton.setOnClickListener(v -> showCancelDialog());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showCancelDialog();
            }
        });

        // Añadir participantes
        binding.addParticipantsButton.setOnClickListener(v -> {
            if (isAdded()) {
                navController.navigate(R.id.action_createGroupFragment_to_selectParticipantsFragment);
            }
        });

        // Confirmar grupo
        binding.confirmGroupButton.setOnClickListener(v -> validateAndCreateGroup());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.groupImage.setOnClickListener(v -> openImagePicker());
        binding.cameraIcon.setOnClickListener(v -> openImagePicker());

        // Recibir participantes seleccionados
        NavBackStackEntry currentEntry = navController.getCurrentBackStackEntry();
        if (currentEntry != null) {
            currentEntry.getSavedStateHandle()
                    .getLiveData("selected_participants", new ArrayList<User>())
                    .observe(getViewLifecycleOwner(), selected -> {
                        if (selected != null && !selected.isEmpty()) {
                            participants.clear();
                            participants.addAll(selected);
                            participantAdapter.setParticipants(participants);
                            updateParticipantsCount();
                        }
                    });
        }
    }

    private void openImagePicker() {
        pickImageLauncher.launch("image/*");
    }

    private void updateParticipantsCount() {
        binding.participantsCount.setText("Miembros: " + participants.size());
    }

    private void validateAndCreateGroup() {
        String groupName = binding.groupNameEdit.getText().toString().trim();

        if (groupName.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe un nombre para el grupo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (participants.isEmpty()) {
            Toast.makeText(requireContext(), "Añade al menos un participante", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Toast.makeText(requireContext(), "Error: usuario no autenticado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Subir foto si existe
        if (groupPhotoUri != null) {
            binding.confirmGroupButton.setEnabled(false);
            ImgurApiClient.getInstance().uploadImage(groupPhotoUri, requireContext().getContentResolver(),
                    new ImgurApiClient.UploadCallback() {
                        @Override
                        public void onSuccess(String imageUrl) {
                            createGroupInFirebase(groupName, imageUrl, currentUserUid);
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(requireContext(), "Error subiendo foto: " + error, Toast.LENGTH_LONG).show();
                            binding.confirmGroupButton.setEnabled(true);
                        }
                    });
        } else {
            createGroupInFirebase(groupName, null, currentUserUid);
        }
    }

    private void createGroupInFirebase(String groupName, String groupPhotoUrl, String currentUserUid) {
        Map<String, Boolean> participantsMap = new HashMap<>();
        participantsMap.put(currentUserUid, true);

        for (User p : participants) {
            if (p.getUid() != null) {
                participantsMap.put(p.getUid(), true);
            }
        }

        Map<String, Integer> unreadCount = new HashMap<>();
        for (String uid : participantsMap.keySet()) {
            unreadCount.put(uid, 0);
        }

        String chatId = FirebaseManager.getInstance().getDatabase()
                .getReference("chats").push().getKey();

        if (chatId == null) {
            Toast.makeText(requireContext(), "Error al generar ID del grupo", Toast.LENGTH_SHORT).show();
            return;
        }

        Chat groupChat = new Chat();
        groupChat.setId(chatId);
        groupChat.setParticipants(participantsMap);
        groupChat.setLastMessage("Grupo creado");
        groupChat.setLastMessageTimestamp(System.currentTimeMillis());
        groupChat.setUnreadCount(unreadCount);
        groupChat.setGroupName(groupName);
        groupChat.setAdminUid(currentUserUid); // ← UID del admin
        groupChat.setGroupIcon(groupPhotoUrl != null ? groupPhotoUrl : "");
        groupChat.setGroup(true);

        FirebaseManager.getInstance().getDatabase()
                .getReference("chats").child(chatId)
                .setValue(groupChat)
                .addOnSuccessListener(aVoid -> {
                    // Asociar chat a todos los participantes (incluido admin)
                    DatabaseReference userChatsRef = FirebaseManager.getInstance().getDatabase().getReference("user-chats");
                    for (String uid : participantsMap.keySet()) {
                        userChatsRef.child(uid).child(chatId).setValue(true);
                    }

                    Toast.makeText(requireContext(), "Grupo '" + groupName + "' creado", Toast.LENGTH_SHORT).show();

                    // Limpiar y volver
                    groupPhotoUri = null;
                    navController.navigate(R.id.action_createGroupFragment_to_chatListFragment);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creando grupo", e);
                    Toast.makeText(requireContext(), "Error al crear grupo", Toast.LENGTH_LONG).show();
                    if (binding != null) binding.confirmGroupButton.setEnabled(true);
                });
    }

    private void showCancelDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Cancelar creación de grupo")
                .setMessage("¿Estás seguro? Se perderán los datos introducidos.")
                .setPositiveButton("Cancelar creación", (dialog, which) -> {
                    if (isAdded()) {
                        navController.navigate(R.id.action_createGroupFragment_to_chatListFragment);
                    }
                })
                .setNegativeButton("Seguir editando", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}