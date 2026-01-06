package com.example.sendme.data.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Modelo que representa un chat en la app SendMe.
 *
 * Sirve tanto para chats individuales (1:1) como para grupos.
 * Se usa para guardar y leer datos de Realtime Database (colección "chats").
 *
 * Los campos clave son:
 * - participants: quiénes están en el chat (UID → true).
 * - lastMessage y lastMessageTimestamp: para mostrar el último mensaje en la lista de chats.
 * - unreadCount: cuántos mensajes no leídos tiene cada usuario.
 * - groupName, groupIcon, adminUid: solo para grupos.
 *
 * Firebase necesita el constructor vacío, por eso está.
 */
public class Chat {

    private String id;
    private Map<String, Boolean> participants = new HashMap<>();
    private String groupName = "";
    private String lastMessage = "";
    private boolean isGroup = false;
    private long lastMessageTimestamp = 0;
    private Map<String, Integer> unreadCount = new HashMap<>();
    private String groupIcon = "";
    private String adminUid = "";

    /** Constructor vacío requerido por Firebase para deserializar */
    public Chat() {}

    // Getters y setters (bastante estándar, no necesitan mucho comentario)

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
        this.participants = participants != null ? participants : new HashMap<>();
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName != null ? groupName : "";
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage != null ? lastMessage : "";
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
        this.unreadCount = unreadCount != null ? unreadCount : new HashMap<>();
    }

    public String getGroupIcon() {
        return groupIcon;
    }

    public void setGroupIcon(String groupIcon) {
        this.groupIcon = groupIcon != null ? groupIcon : "";
    }

    public String getAdminUid() {
        return adminUid;
    }

    public void setAdminUid(String adminUid) {
        this.adminUid = adminUid != null ? adminUid : "";
    }

    /** Indica si este chat es un grupo o individual */
    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    /**
     * Añade (o actualiza) un participante al chat.
     * Normalmente se usa true para indicar que está activo.
     */
    public void addParticipant(String uid, boolean active) {
        participants.put(uid, active);
    }

    /**
     * Incrementa en 1 el contador de mensajes no leídos para un usuario concreto.
     * Útil cuando alguien recibe un mensaje nuevo.
     */
    public void incrementUnreadForUser(String uid) {
        int current = getUnreadCountForUser(uid);
        unreadCount.put(uid, current + 1);
    }

    /**
     * Devuelve cuántos mensajes no leídos tiene un usuario en este chat.
     * Si no existe entrada, devuelve 0.
     */
    public int getUnreadCountForUser(String uid) {
        return unreadCount.getOrDefault(uid, 0);
    }

    @Override
    public String toString() {
        return "Chat{" +
                "id='" + id + '\'' +
                ", participants=" + participants.keySet() +
                ", groupName='" + groupName + '\'' +
                ", lastMessage='" + lastMessage + '\'' +
                ", timestamp=" + lastMessageTimestamp +
                ", isGroup=" + isGroup +
                '}';
    }
}
