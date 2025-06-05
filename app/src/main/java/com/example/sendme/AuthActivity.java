package com.example.sendme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.sendme.databinding.ActivityAuthBinding;
import com.example.sendme.repository.FirebaseManager;
import com.example.sendme.ui.AuthViewModel;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Activity principal para la autenticación de usuarios.
 * Gestiona el proceso de inicio de sesión por número de teléfono utilizando Firebase Authentication.
 */
public class AuthActivity extends AppCompatActivity {
    // View Binding para acceder a los elementos del layout de la actividad.
    private ActivityAuthBinding binding;
    // ViewModel para manejar la lógica de autenticación y los datos.
    private AuthViewModel authViewModel;
    // ID de verificación recibido de Firebase después de enviar el código SMS.
    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Infla el layout de la actividad usando View Binding.
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Inicializa el AuthViewModel.
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Configura el listener para el botón de inicio de sesión.
        binding.loginButton.setOnClickListener(v -> {
            String phone = binding.phoneEditText.getText().toString().trim();
            if (phone.isEmpty()) {
                // Muestra un error si el campo del teléfono está vacío.
                binding.phoneInputLayout.setError(getString(R.string.error_phone_empty));
            } else {
                binding.phoneInputLayout.setError(null); // Limpia cualquier error previo.
                startPhoneNumberVerification(phone); // Inicia el proceso de verificación.
            }
        });

        // Observa el estado de 'userExists' desde el ViewModel para decidir la siguiente actividad.
        authViewModel.getUserExists().observe(this, exists -> {
            if (exists == null) return; // Ignora valores nulos.
            if (exists) {
                // Si el usuario existe, navega a MainActivity.
                startActivity(new Intent(this, MainActivity.class));
            } else {
                // Si el usuario no existe, navega a ProfileSetupActivity para completar el registro.
                startActivity(new Intent(this, ProfileSetupActivity.class));
            }
            finish(); // Finaliza esta actividad para que el usuario no pueda volver a ella.
        });

        // Observa los errores desde el ViewModel para mostrarlos al usuario.
        authViewModel.getError().observe(this, error -> {
            if (error != null) {
                // Muestra el error en un AlertDialog.
                new AlertDialog.Builder(this)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    /**
     * Inicia el proceso de verificación del número de teléfono con Firebase Phone Authentication.
     *
     * @param phoneNumber El número de teléfono a verificar.
     */
    private void startPhoneNumberVerification(String phoneNumber) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60, // Tiempo de espera para la verificación en segundos.
                TimeUnit.SECONDS,
                this, // Contexto de la actividad.
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        // Callback cuando la verificación automática se completa (ej. Auto-retrieval de SMS).
                        signInWithPhoneAuthCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        // Callback cuando la verificación falla.
                        authViewModel.setError(e.getMessage());
                    }

                    @Override
                    public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                        // Callback cuando el código SMS ha sido enviado al teléfono.
                        verificationId = vId; // Guarda el ID de verificación para usarlo después.
                        showVerificationCodeDialog(); // Muestra el diálogo para que el usuario introduzca el código.
                    }
                });
    }

    /**
     * Muestra un diálogo al usuario para que introduzca el código de verificación recibido por SMS.
     */
    private void showVerificationCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title_enter_code));
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
        builder.setView(input); // Establece el campo de entrada en el diálogo.
        builder.setPositiveButton(getString(R.string.dialog_button_verify), (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (!code.isEmpty()) {
                verifyPhoneNumberWithCode(verificationId, code); // Intenta verificar con el código proporcionado.
            } else {
                authViewModel.setError(getString(R.string.error_empty_code)); // Muestra error si el código está vacío.
            }
        });
        builder.setNegativeButton(getString(R.string.dialog_button_cancel), null); // Botón de cancelar.
        builder.show();
    }

    /**
     * Verifica el número de teléfono utilizando el código y el ID de verificación.
     *
     * @param verificationId El ID de verificación recibido cuando se envió el código.
     * @param code           El código SMS introducido por el usuario.
     */
    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential); // Procede a iniciar sesión con la credencial.
    }

    /**
     * Inicia sesión en Firebase con la credencial de autenticación de teléfono.
     *
     * @param credential La credencial de autenticación de teléfono (generada con el número/código).
     */
    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        FirebaseManager.getInstance().getAuth().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String phone = task.getResult().getUser().getPhoneNumber();
                        authViewModel.checkUser(phone); // Si la sesión es exitosa, verifica si el usuario ya existe en Firestore.
                    } else {
                        authViewModel.setError(task.getException().getMessage()); // Muestra cualquier error de inicio de sesión.
                    }
                });
    }
}