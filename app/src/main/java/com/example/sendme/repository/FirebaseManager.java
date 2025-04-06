package com.example.sendme.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
/**
 * Clase para centralizar el acceso a FirebaseAuth y FirebaseFirestore
 * mediante el patrón Singleton. Así evito crear múltiples instancias
 * innecesarias a lo largo de la app.
 */
public class FirebaseManager {
    private static FirebaseManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    // Constructor privado: así nos aseguramos de que no se puede crear
    // una instancia directamente desde fuera de la clase.
    private FirebaseManager() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Devuelve la instancia única de FirebaseManager.
     * Si no existe aún, la crea. Es synchronized para que sea seguro
     * en entornos con múltiples hilos, ya que usare hilos
     */
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    /**
     * Devuelve la instancia de FirebaseAuth para autenticar usuarios.
     */
    public FirebaseAuth getAuth() {
        return auth;
    }

    /**
     * Devuelve la instancia de FirebaseFirestore para acceder a la base de datos.
     */
    public FirebaseFirestore getFirestore() {
        return firestore;
    }
}
