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

public class AuthActivity extends AppCompatActivity {
    private ActivityAuthBinding binding;
    private AuthViewModel authViewModel;
    private String verificationId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.loginButton.setOnClickListener(v -> {
            String phone = binding.phoneEditText.getText().toString().trim();
            if (phone.isEmpty()) {
                binding.phoneInputLayout.setError(getString(R.string.error_phone_empty));
            } else {
                binding.phoneInputLayout.setError(null);
                startPhoneNumberVerification(phone);
            }
        });

        authViewModel.getUserExists().observe(this, exists -> {
            if (exists == null) return;
            if (exists) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, ProfileSetupActivity.class));
            }
            finish();
        });

        authViewModel.getError().observe(this, error -> {
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                this,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        signInWithPhoneAuthCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        authViewModel.setError(e.getMessage());
                    }

                    @Override
                    public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = vId;
                        showVerificationCodeDialog();
                    }
                });
    }

    private void showVerificationCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title_enter_code));
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.dialog_button_verify), (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (!code.isEmpty()) {
                verifyPhoneNumberWithCode(verificationId, code);
            } else {
                authViewModel.setError(getString(R.string.error_empty_code));
            }
        });
        builder.setNegativeButton(getString(R.string.dialog_button_cancel), null);
        builder.show();
    }

    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        FirebaseManager.getInstance().getAuth().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String phone = task.getResult().getUser().getPhoneNumber();
                        authViewModel.checkUser(phone); // Uso el ViewModel para verificar el usuario
                    } else {
                        authViewModel.setError(task.getException().getMessage());
                    }
                });
    }
}