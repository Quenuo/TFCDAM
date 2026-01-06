package com.example.sendme.data.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Modelo que representa a un usuario en SendMe.
 *
 * Contiene toda la información del perfil: UID (clave única de Firebase Auth),
 * nombre de usuario, estado, foto, teléfono, email...
 *
 * Se guarda en Firestore bajo la colección "users", con el UID como ID del documento.
 *
 * Implementa Parcelable porque pasamos objetos User entre fragments o activities
 * (por ejemplo, al abrir un chat 1:1 o ver detalles de un contacto).
 */
public class User implements Parcelable {

    private String uid;         // UID de Firebase Authentication (clave única)
    private String phone;       // Teléfono introducido manualmente
    private String username;    // Nombre visible en la app
    private String status;      // Estado o mensaje personal
    private String imageUrl;    // URL de la foto de perfil (Imgur)
    private String email;       // Email usado para login

    /** Constructor vacío necesario para Firebase (deserialización) */
    public User() {}

    /**
     * Constructor completo usado al crear un usuario nuevo.
     */
    public User(String uid, String phone, String username, String status, String imageUrl, String email) {
        this.uid = uid;
        this.phone = phone;
        this.username = username;
        this.status = status;
        this.imageUrl = imageUrl;
        this.email = email;
    }

    // === Parcelable boilerplate ===
    // Permite pasar el objeto entre componentes de Android

    protected User(Parcel in) {
        uid = in.readString();
        phone = in.readString();
        username = in.readString();
        status = in.readString();
        imageUrl = in.readString();
        email = in.readString();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uid);
        dest.writeString(phone);
        dest.writeString(username);
        dest.writeString(status);
        dest.writeString(imageUrl);
        dest.writeString(email);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // === Getters y setters ===
    // Simples, pero con algún null-check para evitar problemas

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Dos usuarios son iguales si tienen el mismo UID.
     * Útil para comparar en listas o maps.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(uid, user.uid);
    }

    /**
     * Hash basado solo en UID, para que equals y hashCode sean consistentes.
     */
    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }
}