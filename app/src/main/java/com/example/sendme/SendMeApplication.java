package com.example.sendme;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

/**
 * Clase principal de la aplicación que se ejecuta al iniciar la aplicación.
 * Utilizada para configuraciones globales, como habilitar la persistencia de Firebase Realtime Database.
 */
public class SendMeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Habilitar la persistencia offline de Firebase Realtime Database.
        // Esto permite que la aplicación funcione sin conexión a internet y sincronice los datos
        // cuando la conexión se restablezca. Se debe llamar una única vez antes de cualquier otra
        // operación de Realtime Database.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}