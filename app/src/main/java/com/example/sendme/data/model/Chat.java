package com.example.sendme.data.model;

import java.util.HashMap;
import java.util.Map;

// Esta clase representa un chat entre varios usuarios
public class Chat {
    // Identificador único del chat
    private String id;

    // Participantes del chat. La clave es el ID del usuario, y el valor es un booleano
    // que podría representar si está activo, o simplemente marcar su presencia
    private Map<String, Boolean> participants = new HashMap<>();

    // Último mensaje enviado en el chat
    private String lastMessage;

    // Momento en el que se envió el último mensaje (en formato timestamp)
    private long lastMessageTimestamp;

    // Mapa para llevar la cuenta de mensajes no leídos por usuario
    private Map<String, Integer> unreadCount = new HashMap<>();

    // Constructor vacío necesario para algunas operaciones como deserialización
    public Chat() {}

    // Constructor con todos los campos, útil para instanciar un chat ya con datos
    public Chat(String id, Map<String, Boolean> participants, String lastMessage, long lastMessageTimestamp, Map<String, Integer> unreadCount) {
        this.id = id;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unreadCount = unreadCount;
    }

    // Getters y setters clásicos

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Boolean> getParticipants() {
        return participants;
    }

    public void setParticipants(Map<String, Boolean> participants) {
        this.participants = participants;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public Map<String, Integer> getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Map<String, Integer> unreadCount) {
        this.unreadCount = unreadCount;
    }
}
