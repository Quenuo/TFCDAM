package com.example.sendme.view;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.Manifest;

import com.bumptech.glide.Glide;
import com.example.sendme.R;
import com.example.sendme.data.model.User;
import com.example.sendme.databinding.FragmentEditProfileBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.google.firebase.firestore.DocumentReference;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragmento para la edición del perfil de usuario.
 * Permite cambiar el nombre de usuario, el estado y la imagen de perfil.
 */
public class EditProfileFragment extends Fragment {

    private FragmentEditProfileBinding binding;
    private User currentUser;
    private String newImageUrl;
    private static final String TAG = "EditProfileFragment";
    private static final String KEY_NEW_IMAGE_URL = "new_image_url";

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_NEW_IMAGE_URL)) {
            newImageUrl = savedInstanceState.getString(KEY_NEW_IMAGE_URL);
        } else {
            newImageUrl = null;
        }

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openGallery();
            else handlePermissionDenied();
        });

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadImageToImgur(uri);
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);

        if (newImageUrl != null && !newImageUrl.isEmpty()) {
            Glide.with(this).load(newImageUrl).circleCrop().into(binding.profileImage);
        } else {
            loadCurrentUser();
        }

        binding.changeProfileImageButton.setOnClickListener(v -> openGallery());

        // Botón de retroceso visible
        binding.backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        // Botón físico de retroceso
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavHostFragment.findNavController(EditProfileFragment.this).popBackStack();
            }
        });

        // Cancelar
        binding.cancelButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        // Guardar
        binding.saveButton.setOnClickListener(v -> {
            String newUsername = binding.editUsername.getText().toString().trim();
            String newStatus = binding.editStatus.getText().toString().trim();

            if (newUsername.isEmpty()) {
                Toast.makeText(requireContext(), "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("username", newUsername);
            updates.put("status", newStatus.isEmpty() ? "" : newStatus);

            if (newImageUrl != null && !newImageUrl.isEmpty()) {
                updates.put("imageUrl", newImageUrl);
            } else if (currentUser != null && currentUser.getImageUrl() != null) {
                updates.put("imageUrl", currentUser.getImageUrl());
            } else {
                updates.put("imageUrl", "");
            }

            // ← USAMOS UID COMO ID DEL DOCUMENTO
            String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                    ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                    : null;

            if (currentUserUid == null) {
                Toast.makeText(requireContext(), "Error: usuario no autenticado", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseManager.getInstance().getFirestore()
                    .collection("users")
                    .document(currentUserUid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(this).navigate(R.id.action_editProfileFragment_to_chatListFragment);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "Error al actualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        return binding.getRoot();
    }

    private void loadCurrentUser() {
        String currentUserUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        if (currentUserUid == null) {
            Toast.makeText(requireContext(), "Error de autenticación", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        FirebaseManager.getInstance().getFirestore()
                .collection("users")
                .document(currentUserUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            binding.editUsername.setText(currentUser.getUsername());

                            // Teléfono (no editable)
                            String phone = currentUser.getPhone();
                            binding.editPhone.setText(phone != null && !phone.isEmpty() ? phone : "No disponible");

                            // Email (no editable)
                            String email = currentUser.getEmail();
                            binding.editEmail.setText(email != null && !email.isEmpty() ? email : "No disponible");

                            // Status
                            binding.editStatus.setText(currentUser.getStatus() != null ? currentUser.getStatus() : "");

                            // Foto
                            String imageUrlToLoad = (newImageUrl != null && !newImageUrl.isEmpty())
                                    ? newImageUrl
                                    : currentUser.getImageUrl();

                            Glide.with(this)
                                    .load(imageUrlToLoad != null && !imageUrlToLoad.isEmpty() ? imageUrlToLoad : R.drawable.default_profile)
                                    .error(R.drawable.default_profile)
                                    .circleCrop()
                                    .into(binding.profileImage);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show();
                });
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
        Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show();
    }

    private void uploadImageToImgur(Uri imageUri) {
        ImgurApiClient.getInstance().uploadImage(imageUri, requireContext().getContentResolver(),
                new ImgurApiClient.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            newImageUrl = imageUrl;
                            Glide.with(requireContext()).load(imageUrl).circleCrop().into(binding.profileImage);
                            Toast.makeText(requireContext(), "Foto actualizada", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            newImageUrl = null;
                            if (currentUser != null && currentUser.getImageUrl() != null) {
                                Glide.with(requireContext()).load(currentUser.getImageUrl()).into(binding.profileImage);
                            } else {
                                binding.profileImage.setImageResource(R.drawable.default_profile);
                            }
                            Toast.makeText(requireContext(), "Error al subir foto: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (newImageUrl != null) outState.putString(KEY_NEW_IMAGE_URL, newImageUrl);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}