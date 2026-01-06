package com.example.sendme.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Clase singleton que centraliza el acceso a los servicios de Firebase en la app.
 *
 * Aquí tengo todo en un solo sitio: Firebase Auth (para login/registro),
 * Firestore (para guardar perfiles de usuarios y datos estructurados)
 * y Realtime Database (para chats, mensajes y todo lo que necesita actualización en tiempo real).
 *
 * Uso el patrón Singleton para evitar crear instancias múltiples por toda la app,
 * que sería innecesario y podría causar problemas de rendimiento o inconsistencias.
 * Además, el getInstance() está synchronized por si hay varios hilos accediendo al mismo tiempo
 * (aunque en Android no es súper común, mejor prevenir).
 */
public class FirebaseManager {

    private static FirebaseManager instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase database;

    /**
     * Constructor privado: nadie puede crear una instancia directamente.
     * Aquí inicializo los servicios de Firebase una sola vez.
     */
    private FirebaseManager() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        database = FirebaseDatabase.getInstance();
        // Si quieres una URL específica para Realtime Database, puedes ponerla aquí:
        // database.setPersistenceEnabled(true); // opcional, para offline
    }

    /**
     * Devuelve la única instancia de FirebaseManager.
     * Si no existe, la crea. Synchronized para que sea thread-safe.
     */
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    /** Devuelve la instancia de FirebaseAuth para login, registro, logout... */
    public FirebaseAuth getAuth() {
        return auth;
    }

    /** Devuelve Firestore, principalmente para la colección "users" (perfiles) */
    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    /**
     * Devuelve Realtime Database, usado para chats, mensajes, participantes,
     * lastMessage, unreadCount... todo lo que necesita ser súper rápido y en tiempo real.
     */
    public FirebaseDatabase getDatabase() {
        return database;
    }
}
