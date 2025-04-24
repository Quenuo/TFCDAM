package com.example.sendme;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

//Creo esta clase que extienda Application para inicializar la persistencia de Realtime Database al arrancar la aplicación
public class SendMeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Habilitar persistencia de Realtime Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }

}
