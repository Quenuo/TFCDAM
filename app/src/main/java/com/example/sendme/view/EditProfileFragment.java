package com.example.sendme.view;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

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
    // View Binding para acceder a los elementos del layout.
    private FragmentEditProfileBinding binding;
    // Objeto User que representa la información actual del usuario loggeado.
    private User currentUser;
    // URL temporal de la nueva imagen de perfil seleccionada por el usuario antes de guardarla.
    private String newImageUrl;

    // Launcher para solicitar permisos de almacenamiento.
    private ActivityResultLauncher<String> requestPermissionLauncher;
    // Launcher para iniciar el selector de imágenes de la galería.
    private ActivityResultLauncher<String> pickImageLauncher;

    // Etiqueta para mensajes de log.
    private static final String TAG = "EditProfileFragment";
    // Clave para guardar y restaurar la URL de la imagen en el estado del fragmento.
    private static final String KEY_NEW_IMAGE_URL = "new_image_url";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Si el fragmento se está recreando (ej. por rotación), restauramos la URL de la imagen.
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_NEW_IMAGE_URL)) {
            newImageUrl = savedInstanceState.getString(KEY_NEW_IMAGE_URL);
            Log.d(TAG, "Restaurando newImageUrl de savedInstanceState: " + newImageUrl);
        } else {
            // Si es la primera vez que se crea o no hay estado guardado, inicializamos a null.
            newImageUrl = null;
        }

        // Inicializa el lanzador de permisos.
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Log.d(TAG, "Permiso concedido, abriendo galería");
                pickImageLauncher.launch("image/*"); // Abre la galería.
            } else {
                // Determina el permiso a solicitar según la versión de Android.
                String permissionDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                        Manifest.permission.READ_MEDIA_IMAGES :
                        Manifest.permission.READ_EXTERNAL_STORAGE;

                // Si el permiso fue denegado permanentemente, guía al usuario a la configuración de la app.
                if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissionDenied)) {
                    Log.w(TAG, "Permiso de almacenamiento denegado permanentemente");
                    Toast.makeText(requireContext(), getString(R.string.toast_permission_denied_permanently), Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    // Si el permiso fue denegado una sola vez.
                    Log.w(TAG, "Permiso de almacenamiento denegado");
                    Toast.makeText(requireContext(), getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Inicializa el lanzador para seleccionar imágenes de la galería.
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                Log.d(TAG, "Imagen seleccionada con URI: " + uri);
                uploadImageToImgur(uri); // Sube la imagen a Imgur.
            } else {
                Log.w(TAG, "No se seleccionó ninguna imagen");
                Toast.makeText(requireContext(), getString(R.string.toast_no_image_selected), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        // Si hay una newImageUrl restaurada, la carga directamente. De lo contrario, carga los datos del usuario actual.
        if (newImageUrl != null && !newImageUrl.isEmpty()) {
            Log.d(TAG, "Cargando imagen restaurada en onCreateView: " + newImageUrl);
            Glide.with(this)
                    .load(newImageUrl)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(binding.profileImage);
        } else {
            loadCurrentUser();
        }

        // Configura el listener para cambiar la imagen de perfil.
        binding.changeProfileImageButton.setOnClickListener(v -> openGallery());

        // Configura el botón de retroceso de la barra superior.
        binding.backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        // Configura el botón de cancelar.
        binding.cancelButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        // Configura el botón de guardar cambios.
        binding.saveButton.setOnClickListener(v -> {
            String newUsername = binding.editUsername.getText().toString().trim();
            String newStatus = binding.editStatus.getText().toString().trim();

            if (!newUsername.isEmpty()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("username", newUsername);
                // Si el estado está vacío, se guarda como String vacío.
                updates.put("status", newStatus.isEmpty() ? "" : newStatus);

                // Si hay una nueva URL de imagen, la incluye en las actualizaciones.
                if (newImageUrl != null && !newImageUrl.isEmpty()) {
                    updates.put("imageUrl", newImageUrl);
                } else if (currentUser != null && currentUser.getImageUrl() != null) {
                    // Si no se seleccionó una nueva imagen, se mantiene la actual del usuario.
                    updates.put("imageUrl", currentUser.getImageUrl());
                } else {
                    // Si no hay nueva imagen y el usuario tampoco tenía una, se establece a vacío.
                    updates.put("imageUrl", "");
                }

                // Obtiene la referencia al documento del usuario actual en Firestore y lo actualiza.
                String currentPhone = FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber();
                DocumentReference userRef = FirebaseManager.getInstance().getFirestore().collection("users").document(currentPhone);
                userRef.update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(requireContext(), getString(R.string.toast_profile_updated), Toast.LENGTH_SHORT).show();
                            // Navega de vuelta a la lista de chats tras la actualización.
                            NavHostFragment.findNavController(this).navigate(R.id.action_editProfileFragment_to_chatListFragment);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(requireContext(), getString(R.string.toast_update_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_username_empty), Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * Carga la información del usuario actual desde Firestore y actualiza la UI.
     */
    private void loadCurrentUser() {
        String currentPhone = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getPhoneNumber() : null;
        if (currentPhone == null) {
            Toast.makeText(requireContext(), getString(R.string.toast_not_authenticated), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        // Obtiene el documento del usuario desde Firestore.
        FirebaseManager.getInstance().getFirestore().collection("users").document(currentPhone)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            // Rellena los campos de texto con la información del usuario.
                            binding.editUsername.setText(currentUser.getUsername());
                            binding.editPhone.setText(currentUser.getPhone() != null ? currentUser.getPhone() : "Número no disponible");
                            binding.editStatus.setText(currentUser.getStatus() != null && !currentUser.getStatus().isEmpty() ? currentUser.getStatus() : "");

                            // Prioriza newImageUrl si existe, de lo contrario usa la imagen actual del usuario.
                            String imageUrlToLoad = (newImageUrl != null && !newImageUrl.isEmpty()) ? newImageUrl : currentUser.getImageUrl();

                            // Carga la imagen de perfil usando Glide.
                            Glide.with(this)
                                    .load(imageUrlToLoad != null && !imageUrlToLoad.isEmpty() ? imageUrlToLoad : R.drawable.default_profile)
                                    .error(R.drawable.default_profile)
                                    .circleCrop()
                                    .into(binding.profileImage);
                        }
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.toast_user_not_found), Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(this).popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), getString(R.string.toast_profile_load_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).popBackStack();
                });
    }

    /**
     * Abre la galería de imágenes, solicitando los permisos necesarios si aún no se tienen.
     */
    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Para Android 13 (API 33) y superiores.
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Solicitando permiso READ_MEDIA_IMAGES");
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                Log.d(TAG, "Permiso READ_MEDIA_IMAGES ya concedido, lanzando selector de imagen");
                pickImageLauncher.launch("image/*");
            }
        } else { // Para Android 12 (API 32) y anteriores.
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Solicitando permiso READ_EXTERNAL_STORAGE");
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                Log.d(TAG, "Permiso READ_EXTERNAL_STORAGE ya concedido, lanzando selector de imagen");
                pickImageLauncher.launch("image/*");
            }
        }
    }

    /**
     * Sube la imagen seleccionada a Imgur y actualiza la UI con la URL de la imagen cargada.
     * En caso de fallo, revierte la imagen de perfil a la original del usuario o a una por defecto.
     *
     * @param imageUri La URI de la imagen seleccionada para subir.
     */
    private void uploadImageToImgur(Uri imageUri) {
        Log.d(TAG, "Intentando subir imagen con URI: " + imageUri);
        ImgurApiClient.getInstance().uploadImage(imageUri, requireContext().getContentResolver(), new ImgurApiClient.UploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    newImageUrl = imageUrl; // Guarda la URL de la imagen subida.
                    Glide.with(requireContext())
                            .load(newImageUrl)
                            .error(R.drawable.default_profile)
                            .circleCrop()
                            .into(binding.profileImage);
                    Toast.makeText(requireContext(), getString(R.string.toast_image_uploaded_success), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Imagen subida con éxito, URL: " + imageUrl);
                });
            }

            @Override
            public void onFailure(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    newImageUrl = null; // Resetea newImageUrl a null si la subida falla.
                    // Carga la imagen de perfil original del usuario o una por defecto.
                    if (currentUser != null && currentUser.getImageUrl() != null && !currentUser.getImageUrl().isEmpty()) {
                        Glide.with(requireContext())
                                .load(currentUser.getImageUrl())
                                .error(R.drawable.default_profile)
                                .circleCrop()
                                .into(binding.profileImage);
                    } else {
                        binding.profileImage.setImageResource(R.drawable.default_profile);
                    }
                    Toast.makeText(requireContext(), getString(R.string.toast_image_load_error) + error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Fallo al subir la imagen: " + error);
                });
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Guarda la URL de la imagen temporalmente seleccionada para restaurarla si el fragmento se recrea.
        if (newImageUrl != null) {
            outState.putString(KEY_NEW_IMAGE_URL, newImageUrl);
            Log.d(TAG, "Guardando newImageUrl en onSaveInstanceState: " + newImageUrl);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Libera la referencia al binding para evitar memory leaks.
    }
}