package com.example.sendme.view;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.sendme.data.model.Chat;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentSelectParticipantsBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.SelectParticipantAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SelectParticipantsFragment extends Fragment {

    private FragmentSelectParticipantsBinding binding;
    private NavController navController;
    private List<User> allUsers = new ArrayList<>();
    private Set<String> selectedUserUids = new HashSet<>();
    private Set<String> currentGroupMemberUids = new HashSet<>();
    private SelectParticipantAdapter adapter;
    private ListenerRegistration usersListener;
    private String groupId;
    private boolean isCreatingNewGroup = true;
    private static final String TAG = "SelectParticipantsFragment";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
            isCreatingNewGroup = (groupId == null);
        } else {
            isCreatingNewGroup = true;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSelectParticipantsBinding.inflate(inflater, container, false);
        navController = NavHostFragment.findNavController(this);

        adapter = new SelectParticipantAdapter(selectedUserUids, uid -> {
            if (selectedUserUids.contains(uid)) {
                selectedUserUids.remove(uid);
            } else {
                selectedUserUids.add(uid);
            }
            adapter.notifyDataSetChanged();
        });

        binding.participantsListRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.participantsListRecycler.setAdapter(adapter);

        loadUsers();

        binding.confirmSelectionButton.setOnClickListener(v -> {
            if (selectedUserUids.isEmpty()) {
                Toast.makeText(requireContext(), "Selecciona al menos un participante", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isCreatingNewGroup) {
                // === CREACIÓN DE NUEVO GRUPO ===
                NavBackStackEntry previousEntry = navController.getPreviousBackStackEntry();
                if (previousEntry != null) {
                    List<User> selectedUsers = new ArrayList<>();
                    for (String uid : selectedUserUids) {
                        for (User user : allUsers) {
                            if (user.getUid() != null && user.getUid().equals(uid)) {
                                selectedUsers.add(user);
                                break;
                            }
                        }
                    }
                    previousEntry.getSavedStateHandle().set("selected_participants", selectedUsers);
                }
                navController.navigateUp();
            } else {
                // === AÑADIR A GRUPO EXISTENTE CON MULTI-PATH UPDATE ===
                DatabaseReference database = FirebaseManager.getInstance().getDatabase().getReference();

                Map<String, Object> childUpdates = new HashMap<>();

                for (String uid : selectedUserUids) {
                    // Añadir al chat
                    childUpdates.put("/chats/" + groupId + "/participants/" + uid, true);
                    childUpdates.put("/chats/" + groupId + "/unreadCount/" + uid, 0);

                    // Añadir al user-chats del nuevo participante
                    childUpdates.put("/user-chats/" + uid + "/" + groupId, true);
                }

                database.updateChildren(childUpdates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Participantes añadidos correctamente con multi-path update");
                            Toast.makeText(requireContext(), "Participantes añadidos al grupo", Toast.LENGTH_SHORT).show();
                            navController.navigateUp();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error añadiendo participantes: " + e.getMessage());
                            Toast.makeText(requireContext(), "Error al añadir participantes: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navController.navigateUp();
            }
        });

        return binding.getRoot();
    }

    private void loadUsers() {
        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Toast.makeText(requireContext(), "Error de autenticación", Toast.LENGTH_SHORT).show();
            navController.popBackStack();
            return;
        }

        if (!isCreatingNewGroup && groupId != null) {
            FirebaseManager.getInstance().getDatabase()
                    .getReference("chats").child(groupId).child("participants")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            currentGroupMemberUids.clear();
                            GenericTypeIndicator<Map<String, Boolean>> t = new GenericTypeIndicator<Map<String, Boolean>>() {};
                            Map<String, Boolean> participants = snapshot.getValue(t);
                            if (participants != null) {
                                currentGroupMemberUids.addAll(participants.keySet());
                            }
                            loadAllUsers(currentUserUid);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadAllUsers(currentUserUid);
                        }
                    });
        } else {
            currentGroupMemberUids.clear();
            loadAllUsers(currentUserUid);
        }
    }

    private void loadAllUsers(String currentUserUid) {
        usersListener = FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading users: " + error.getMessage());
                        return;
                    }

                    allUsers.clear();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            User user = doc.toObject(User.class);
                            if (user != null && user.getUid() != null &&
                                    !user.getUid().equals(currentUserUid) &&
                                    !currentGroupMemberUids.contains(user.getUid())) {
                                allUsers.add(user);
                            }
                        }
                    }
                    adapter.setUsers(allUsers);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (usersListener != null) {
            usersListener.remove();
        }
        binding = null;
    }
}