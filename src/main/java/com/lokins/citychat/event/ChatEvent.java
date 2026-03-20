package com.lokins.citychat.event;

import com.lokins.citychat.data.ChatMessage;

/**
 * 基础聊天事件抽象类
 */
public abstract class ChatEvent {
    private boolean cancelled = false;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * 获取事件的唯一标识符，用于事件跟踪
     */
    public abstract String getEventId();
}

