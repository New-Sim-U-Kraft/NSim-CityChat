package com.lokins.citychat.data;

import java.util.UUID;

/**
 * 表示聊天消息的数据模型
 */
public class ChatMessage {
    private final UUID senderId;
    private final String senderName;
    private final String content;
    private final long timestamp;
    private final String channelId;
    private final boolean isPrivate;
    private final UUID messageId;

    public ChatMessage(UUID senderId, String senderName, String content, 
                      String channelId, boolean isPrivate) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.channelId = channelId;
        this.isPrivate = isPrivate;
        this.messageId = UUID.randomUUID();
    }

        public ChatMessage(UUID senderId, String senderName, String content,
                       String channelId, boolean isPrivate, long timestamp, UUID messageId) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.channelId = channelId;
        this.isPrivate = isPrivate;
        this.messageId = messageId == null ? UUID.randomUUID() : messageId;
    }

    // Getters
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public String getChannelId() { return channelId; }
    public boolean isPrivate() { return isPrivate; }
    public UUID getMessageId() { return messageId; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", senderName, channelId, content);
    }
}

