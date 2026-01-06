package com.example.sendme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.sendme.databinding.ActivityAuthBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.AuthViewModel;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Activity principal para la autenticación de usuarios.
 * Gestiona el proceso de inicio de sesión por número de teléfono utilizando Firebase Authentication.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private AuthViewModel authViewModel;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseManager.getInstance().getAuth();
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Botón de login
        binding.loginButton.setOnClickListener(v -> attemptLogin());

        // Observar si el usuario tiene perfil
        authViewModel.getUserExists().observe(this, exists -> {
            if (exists == null) return;

            if (exists) {
                // Ya tiene perfil → ir a MainActivity
                startActivity(new Intent(this, MainActivity.class));
            } else {
                // Nuevo usuario → ir a ProfileSetup
                startActivity(new Intent(this, ProfileSetupActivity.class));
            }
            finish();
        });

        // Observar errores
        authViewModel.getError().observe(this, error -> {
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        // Botón físico: cerrar app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(AuthActivity.this)
                        .setTitle("¿Salir de SendMe?")
                        .setMessage("¿Estás seguro de que quieres cerrar la aplicación?")
                        .setPositiveButton("Salir", (dialog, which) -> finish())
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
    }

    private void attemptLogin() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString();

        // Validaciones
        if (TextUtils.isEmpty(email)) {
            binding.emailInputLayout.setError("El correo es obligatorio");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError("Introduce un correo válido");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.passwordInputLayout.setError("La contraseña es obligatoria");
            return;
        }
        if (password.length() < 6) {
            binding.passwordInputLayout.setError("Mínimo 6 caracteres");
            return;
        }
        if (password.trim().length() != password.length()) {
            binding.passwordInputLayout.setError("No puede tener espacios");
            return;
        }

        // Limpiar errores previos
        binding.emailInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Login correcto → verificar si tiene perfil
                        authViewModel.checkUser(); // ← SIN PARÁMETRO
                    } else {
                        // Error de login → intentar registro
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthInvalidUserException ||
                                exception instanceof FirebaseAuthInvalidCredentialsException) {

                            mAuth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(createTask -> {
                                        if (createTask.isSuccessful()) {
                                            // Registro correcto → va a ProfileSetup
                                            authViewModel.checkUser(); // ← SIN PARÁMETRO
                                        } else {
                                            authViewModel.setError(createTask.getException().getMessage());
                                        }
                                    });
                        } else {
                            authViewModel.setError(exception.getMessage());
                        }
                    }
                });
    }
}