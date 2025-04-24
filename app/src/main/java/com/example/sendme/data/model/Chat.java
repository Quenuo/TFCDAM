package com.example.sendme.data.model;

import java.util.List;
import java.util.Map;

public class Chat {
    private String id;
    private List<String> participants;
    private String lastMessage;
    private long lastMessageTimestamp;
    private Map<String, Integer> unreadCount;

    public Chat() {}

    public Chat(String id, List<String> participants, String lastMessage, long lastMessageTimestamp, Map<String, Integer> unreadCount) {
        this.id = id;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unreadCount = unreadCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
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
