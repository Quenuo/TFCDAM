package com.example.sendme.data.model;

import android.os.Parcel;
import android.os.Parcelable;

// Esta clase representa a un usuario y es Parcelable, lo cual permite pasar objetos entre Activities o Fragments
public class User implements Parcelable {
    private String phone;      // Número de teléfono del usuario (clave única)
    private String username;   // Nombre de usuario
    private String status;     // Estado personalizado (tipo "Disponible", "Ocupado", etc.)
    private String imageUrl;   // URL de la imagen de perfil

    // Constructor vacío necesario para Firebase, serialización, etc.
    public User() {}

    // Constructor principal que se usa cuando se crean objetos manualmente
    public User(String phone, String username, String status, String imageUrl) {
        this.phone = phone;
        this.username = username;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    // Constructor que reconstruye el objeto a partir de un Parcel (esto es obligatorio para Parcelable)
    protected User(Parcel in) {
        phone = in.readString();
        username = in.readString();
        status = in.readString();
        imageUrl = in.readString();
    }

    // Este objeto CREATOR es necesario para que Android pueda crear instancias de User desde un Parcel
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

    // Getters y setters normales

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

    // Describe el contenido del objeto, por lo general se deja en 0
    @Override
    public int describeContents() {
        return 0;
    }

    // Escribe los datos del objeto en el Parcel en el mismo orden en que luego se leerán
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(phone);
        dest.writeString(username);
        dest.writeString(status);
        dest.writeString(imageUrl);
    }
}
