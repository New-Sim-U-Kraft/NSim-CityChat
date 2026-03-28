package com.lokins.citychat.data;

import java.util.Collections;
import java.util.List;
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
    private final List<MessageAction> actions;

    public ChatMessage(UUID senderId, String senderName, String content,
                       String channelId, boolean isPrivate) {
        this(senderId, senderName, content, channelId, isPrivate,
                System.currentTimeMillis(), UUID.randomUUID(), Collections.emptyList());
    }

    public ChatMessage(UUID senderId, String senderName, String content,
                       String channelId, boolean isPrivate, long timestamp, UUID messageId) {
        this(senderId, senderName, content, channelId, isPrivate, timestamp, messageId, Collections.emptyList());
    }

    public ChatMessage(UUID senderId, String senderName, String content,
                       String channelId, boolean isPrivate, long timestamp, UUID messageId,
                       List<MessageAction> actions) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.channelId = channelId;
        this.isPrivate = isPrivate;
        this.messageId = messageId == null ? UUID.randomUUID() : messageId;
        this.actions = actions == null ? Collections.emptyList() : List.copyOf(actions);
    }

    // Getters
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public String getChannelId() { return channelId; }
    public boolean isPrivate() { return isPrivate; }
    public UUID getMessageId() { return messageId; }
    public List<MessageAction> getActions() { return actions; }
    public boolean hasActions() { return !actions.isEmpty(); }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", senderName, channelId, content);
    }
}
