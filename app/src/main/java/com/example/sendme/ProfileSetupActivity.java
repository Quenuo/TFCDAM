package com.example.sendme;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.sendme.databinding.ActivityProfileSetupBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.repository.ImgurApiClient;
import com.example.sendme.ui.ProfileSetupViewModel;

/**
 * Actividad para la configuración inicial del perfil del usuario.
 * Permite al usuario establecer un nombre de usuario, un estado y una imagen de perfil.
 */
public class ProfileSetupActivity extends AppCompatActivity {
    // View Binding para acceder a los elementos del layout.
    private ActivityProfileSetupBinding binding;
    // ViewModel para gestionar la lógica de negocio y los datos del perfil.
    private ProfileSetupViewModel viewModel;
    // URL de la imagen de perfil seleccionada o subida.
    private String imageURL = "";
    // Clave para guardar y restaurar la URL de la imagen en el estado de la actividad.
    private static final String KEY_IMAGE_URI = "image_uri";

    // Lanzador para abrir la galería y seleccionar una imagen.
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadImageToImgur(uri); // Sube la imagen seleccionada a Imgur.
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Infla el layout de la actividad usando View Binding.
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Verifica si hay un usuario autenticado. Si no, redirige a AuthActivity.
        if (FirebaseManager.getInstance().getAuth().getCurrentUser() == null) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.error_not_authenticated))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        startActivity(new Intent(this, AuthActivity.class));
                        finish(); // Finaliza esta actividad.
                    })
                    .show();
            return;
        }

        // Inicializa el ProfileSetupViewModel.
        viewModel = new ViewModelProvider(this).get(ProfileSetupViewModel.class);

        // Restaura el estado de la actividad (ej. tras una rotación de pantalla).
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_IMAGE_URI)) {
            imageURL = savedInstanceState.getString(KEY_IMAGE_URI);
            if (imageURL != null && !imageURL.isEmpty()) {
                Glide.with(this)
                        .load(imageURL)
                        .error(R.drawable.default_profile)
                        .into(binding.profileImage);
            } else {
                binding.profileImage.setImageResource(R.drawable.default_profile);
            }
        } else {
            // Si no hay estado guardado, establece la imagen de perfil por defecto.
            binding.profileImage.setImageResource(R.drawable.default_profile);
        }

        // Configura el clic en la imagen de perfil para abrir la galería.
        binding.profileImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        // Configura el botón de guardar para guardar el perfil del usuario.
        binding.saveButton.setOnClickListener(v -> {
            String username = binding.usernameEditText.getText().toString().trim();
            String status = binding.statusEditText.getText().toString().trim();

            if (username.isEmpty()) {
                binding.usernameInputLayout.setError(getString(R.string.error_username_empty));
            } else {
                binding.usernameInputLayout.setError(null);
                // Guarda el perfil usando el ViewModel. Si el estado está vacío, usa un mensaje por defecto.
                viewModel.saveProfile(username, status.isEmpty() ? getString(R.string.status_msg) : status, imageURL);
            }
        });

        // Observa el estado de 'profileSaved' desde el ViewModel.
        viewModel.getProfileSaved().observe(this, saved -> {
            if (saved) {
                // Si el perfil se guardó con éxito, navega a MainActivity y finaliza esta actividad.
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });

        // Observa los errores desde el ViewModel para mostrarlos al usuario.
        viewModel.getError().observe(this, error -> {
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    /**
     * Sube la imagen seleccionada a Imgur y actualiza la UI con la URL obtenida.
     * En caso de fallo, revierte la imagen a la por defecto y muestra un mensaje de error.
     *
     * @param imageUri La URI local de la imagen a subir.
     */
    private void uploadImageToImgur(Uri imageUri) {
        ImgurApiClient.getInstance().uploadImage(imageUri, getContentResolver(), new ImgurApiClient.UploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                // Asegura que la actualización de la UI se realiza en el hilo principal.
                new Handler(Looper.getMainLooper()).post(() -> {
                    imageURL = imageUrl; // Almacena la URL de la imagen subida.
                    Glide.with(ProfileSetupActivity.this)
                            .load(imageURL)
                            .error(R.drawable.default_profile)
                            .into(binding.profileImage);
                    Toast.makeText(ProfileSetupActivity.this, "Imagen subida con éxito", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String error) {
                // Asegura que la actualización de la UI se realiza en el hilo principal.
                new Handler(Looper.getMainLooper()).post(() -> {
                    imageURL = ""; // Limpia la URL si la subida falla.
                    binding.profileImage.setImageResource(R.drawable.default_profile); // Vuelve a la imagen por defecto.
                    Toast.makeText(ProfileSetupActivity.this, "Error al subir la imagen: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Guarda la URL de la imagen actual en el Bundle para restaurarla si la actividad se recrea.
        outState.putString(KEY_IMAGE_URI, imageURL);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restaura la URL de la imagen desde el Bundle y la carga en la UI.
        if (savedInstanceState.containsKey(KEY_IMAGE_URI)) {
            imageURL = savedInstanceState.getString(KEY_IMAGE_URI);
            if (imageURL != null && !imageURL.isEmpty()) {
                Glide.with(this)
                        .load(imageURL)
                        .error(R.drawable.default_profile)
                        .into(binding.profileImage);
            } else {
                binding.profileImage.setImageResource(R.drawable.default_profile);
            }
        }
    }
}