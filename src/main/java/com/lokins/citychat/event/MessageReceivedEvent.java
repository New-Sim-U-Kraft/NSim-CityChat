package com.lokins.citychat.event;

import com.lokins.citychat.data.ChatMessage;
import java.util.UUID;

/**
 * 消息接收事件
 */
public class MessageReceivedEvent extends ChatEvent {
    private final ChatMessage message;
    private final UUID receiverId;

    public MessageReceivedEvent(ChatMessage message, UUID receiverId) {
        this.message = message;
        this.receiverId = receiverId;
    }

    public ChatMessage getMessage() { return message; }
    public UUID getReceiverId() { return receiverId; }

    @Override
    public String getEventId() {
        return "message_received:" + message.getMessageId();
    }
}

