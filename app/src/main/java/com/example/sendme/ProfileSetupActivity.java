package com.example.sendme;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.sendme.databinding.ActivityProfileSetupBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.ProfileSetupViewModel;

public class ProfileSetupActivity extends AppCompatActivity {
    private ActivityProfileSetupBinding binding;
    private ProfileSetupViewModel viewModel;
    private String imageURL="";
    private static final String KEY_IMAGE_URI = "image_uri";

    // Lanzador para abrir la galería
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    imageURL = uri.toString(); // Guardo el Uri de la imagen como String
                    binding.profileImage.setImageURI(uri); // Muestro la imagen seleccionada

                }
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Verificar si hay un usuario autenticado
        if (FirebaseManager.getInstance().getAuth().getCurrentUser() == null) {
            new AlertDialog.Builder(this)
                    .setMessage("No estás autenticado. Por favor, inicia sesión.")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                    })
                    .show();
            return;
        }


        viewModel = new ViewModelProvider(this).get(ProfileSetupViewModel.class);
        // Restauro el estado si existe (e.g., tras girar la pantalla)
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_IMAGE_URI)) {
            imageURL = savedInstanceState.getString(KEY_IMAGE_URI);
            if (imageURL != null && !imageURL.isEmpty()) {
                binding.profileImage.setImageURI(Uri.parse(imageURL));
            } else {
                binding.profileImage.setImageResource(R.drawable.default_profile);
            }
        } else {
            // Primera vez que se crea la actividad
            binding.profileImage.setImageResource(R.drawable.default_profile);
        }

        // Abro la galería al hacer clic en la imagen
        binding.profileImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        binding.saveButton.setOnClickListener(v -> {
            String username = binding.usernameEditText.getText().toString().trim();
            String status = binding.statusEditText.getText().toString().trim();

            if (username.isEmpty()) {
                binding.usernameInputLayout.setError(getString(R.string.error_username_empty));
            } else {
                binding.usernameInputLayout.setError(null);
                viewModel.saveProfile(username, status.isEmpty() ? getString(R.string.status_msg) : status, imageURL);
            }
        });

        viewModel.getProfileSaved().observe(this, saved -> {
            if (saved) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });

        viewModel.getError().observe(this, error -> {
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Guardar el URI de la imagen seleccionada
        outState.putString(KEY_IMAGE_URI, imageURL);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restaurar el URI de la imagen al girar la pantalla
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_IMAGE_URI)) {
            imageURL = savedInstanceState.getString(KEY_IMAGE_URI);
            if (imageURL != null && !imageURL.isEmpty()) {
                binding.profileImage.setImageURI(Uri.parse(imageURL));
            }
        }
    }
}