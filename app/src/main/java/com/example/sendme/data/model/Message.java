package com.example.sendme.data.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Modelo que representa un mensaje individual en un chat.
 *
 * Se usa para guardar y leer mensajes en Realtime Database (dentro de "chats/{chatId}/messages").
 * Soporta mensajes de texto puro o con imagen (nunca ambos a la vez).
 *
 * Implementa Parcelable porque a veces pasamos mensajes entre fragments o activities
 * (por ejemplo, para ver una imagen en pantalla completa).
 *
 * El campo fcmToken está aquí por si en el futuro queremos algo relacionado con notificaciones,
 * pero ahora mismo no se usa.
 */
public class Message implements Parcelable {

    private String id;              // ID del mensaje (generado con push().getKey())
    private String sender;          // UID del usuario que envió el mensaje
    private String content;         // Texto del mensaje (puede ser null si es solo imagen)
    private String imageUrl;        // URL de la imagen en Imgur (puede ser null si es texto)
    private long timestamp;         // Cuando se envió el mensaje (System.currentTimeMillis())
    private String fcmToken = "";   // Token FCM del dispositivo (no usado actualmente)

    /** Constructor vacío obligatorio para que Firebase pueda deserializar */
    public Message() {}

    /**
     * Constructor principal usado cuando creamos un mensaje nuevo.
     *
     * @param sender UID del remitente
     * @param content Texto del mensaje (null si es solo imagen)
     * @param imageUrl URL de la imagen (null si es texto)
     * @param timestamp Marca de tiempo del envío
     */
    public Message(String sender, String content, String imageUrl, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
    }

    // === Parcelable implementation ===
    // Necesario para pasar el objeto entre componentes de Android

    protected Message(Parcel in) {
        id = in.readString();
        sender = in.readString();
        content = in.readString();
        imageUrl = in.readString();
        timestamp = in.readLong();
        fcmToken = in.readString();
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(sender);
        dest.writeString(content);
        dest.writeString(imageUrl);
        dest.writeLong(timestamp);
        dest.writeString(fcmToken);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // === Getters y setters ===
    // Bastante directos, solo algunos con protección básica contra null

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken != null ? fcmToken : "";
    }

    /**
     * Comprueba si el mensaje contiene una imagen en lugar de texto.
     *
     * Útil en el adapter para decidir qué vista mostrar (texto o imagen).
     */
    public boolean isImageMessage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }
}