package com.example.sendme.data.model;
//Clase para reprensetar el mensaje individual

import android.os.Parcel;
import android.os.Parcelable;

public class Message implements Parcelable {
    private String id;
    private String sender; // Teléfono del remitente
    private String content; // Contenido del mensaje (texto)
    private String imageUrl; // URL de la imagen (si es un mensaje de imagen)
    private long timestamp; // Marca de tiempo del mensaje

    public Message() {
        // Constructor vacío requerido para Firebase
    }

    public Message(String sender, String content, String imageUrl, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
    }

    protected Message(Parcel in) {
        id = in.readString();
        sender = in.readString();
        content = in.readString();
        imageUrl = in.readString();
        timestamp = in.readLong();
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
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Getters y setters
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

    // Método para determinar si el mensaje es de texto o de imagen
    public boolean isImageMessage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }
}