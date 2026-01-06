package com.example.sendme;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sendme.repository.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

/**
 * Actividad principal de la aplicación.
 * Es el contenedor para el Navigation Component, que gestiona la navegación entre los diferentes fragmentos.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseManager.getInstance().getAuth().getCurrentUser();

        if (currentUser != null) {
            // Usuario autenticado
            Log.d(TAG, "Usuario autenticado:");
            Log.d(TAG, "UID: " + currentUser.getUid());
            Log.d(TAG, "Email: " + currentUser.getEmail());

            setContentView(R.layout.activity_main);

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);

            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();

                // === MANEJO DE NOTIFICACIONES: abrir chat directo ===
                Intent intent = getIntent();
                if (intent != null && intent.hasExtra("openChatId")) {
                    String chatId = intent.getStringExtra("openChatId");
                    boolean isGroup = intent.getBooleanExtra("openIsGroup", false);

                    if (chatId != null) {
                        Log.d(TAG, "Abriendo chat desde notificación: " + chatId + " (grupo: " + isGroup + ")");

                        Bundle bundle = new Bundle();
                        bundle.putString("chatId", chatId);
                        bundle.putBoolean("isGroup", isGroup);

                        // Navega al fragment correcto
                        int destination = isGroup
                                ? R.id.groupChatFragment
                                : R.id.chatFragment;

                        navController.navigate(destination, bundle);
                    }
                }
                // Aquí puedes añadir más configuraciones globales si quieres
            }
        } else {
            // No hay usuario → ir a login
            Log.d(TAG, "No hay usuario autenticado → redirigiendo a AuthActivity");

            Intent intent = new Intent(this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}