package com.lokins.citychat.event;

import com.lokins.citychat.data.ChatMessage;
import java.util.UUID;

/**
 * 消息发送事件（可被取消）
 */
public class MessageSentEvent extends ChatEvent {
    private final ChatMessage message;
    private final UUID senderId;

    public MessageSentEvent(ChatMessage message, UUID senderId) {
        this.message = message;
        this.senderId = senderId;
    }

    public ChatMessage getMessage() { return message; }
    public UUID getSenderId() { return senderId; }

    @Override
    public String getEventId() {
        return "message_sent:" + message.getMessageId();
    }
}

