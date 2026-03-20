package com.lokins.citychat.manager;

import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.event.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 聊天事件分派器 - 处理所有聊天事件的广播
 */
public class ChatEventDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<ChatEventListener> listeners = new CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface ChatEventListener {
        void onChatEvent(ChatEvent event);
    }

    /**
     * 注册事件监听器
     */
    public void registerListener(ChatEventListener listener) {
        listeners.add(listener);
    }

    /**
     * 注销事件监听器
     */
    public void unregisterListener(ChatEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * 分派消息发送事件
     */
    public void dispatchMessageSent(ChatMessage message) {
        MessageSentEvent event = new MessageSentEvent(message, message.getSenderId());
        dispatch(event);
    }

    /**
     * 分派消息接收事件
     */
    public void dispatchMessageReceived(ChatMessage message, UUID receiverId) {
        MessageReceivedEvent event = new MessageReceivedEvent(message, receiverId);
        dispatch(event);
    }

    /**
     * 分派频道加入事件
     */
    public void dispatchChannelJoined(String channelId, UUID playerId, String playerName) {
        ChannelJoinedEvent event = new ChannelJoinedEvent(channelId, playerId, playerName);
        dispatch(event);
    }

    /**
     * 分派频道离开事件
     */
    public void dispatchChannelLeft(String channelId, UUID playerId, String playerName) {
        ChannelLeftEvent event = new ChannelLeftEvent(channelId, playerId, playerName);
        dispatch(event);
    }

    /**
     * 分派事件给所有监听器
     */
    private void dispatch(ChatEvent event) {
        LOGGER.debug("Dispatching chat event: {}", event.getEventId());
        for (ChatEventListener listener : listeners) {
            try {
                listener.onChatEvent(event);
            } catch (Exception e) {
                LOGGER.error("Error while dispatching chat event: {}", event.getEventId(), e);
            }
        }
    }

    public int getListenerCount() {
        return listeners.size();
    }

    public void clearListeners() {
        listeners.clear();
    }
}

