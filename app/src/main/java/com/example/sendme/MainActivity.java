package com.example.sendme;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

/**
 * Actividad principal de la aplicación.
 * Es el contenedor para el Navigation Component, que gestiona la navegación entre los diferentes fragmentos.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Establece el layout de la actividad. Este layout contendrá el NavHostFragment.
        setContentView(R.layout.activity_main);

        // Configura el Navigation Component.
        // Se obtiene una referencia al NavHostFragment que está definido en el layout de esta actividad.
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        // Verifica que el NavHostFragment no sea nulo antes de intentar obtener el NavController.
        if (navHostFragment != null) {
            // Obtiene el NavController asociado con este NavHostFragment.
            // El NavController es el que se usa para realizar las operaciones de navegación (ej. navegar a otro fragmento).
            NavController navController = navHostFragment.getNavController();
            // A partir de aquí, 'navController' estaría listo para ser usado si fuera necesario
            // para configuraciones adicionales a nivel de actividad, aunque comúnmente la navegación
            // se maneja dentro de los fragmentos a través de sus propios NavControllers.
        }
    }
}