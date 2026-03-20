package com.lokins.citychat.manager;

import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.data.ChatUser;
import com.lokins.citychat.data.PrivateChatRoom;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局聊天管理器 - 协调所有聊天相关的操作
 */
public class ChatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ChatManager instance;

    private final ChannelManager channelManager;
    private final ChatEventDispatcher eventDispatcher;
    private final Map<UUID, ChatUser> users;
    private final Map<String, PrivateChatRoom> privateChatRooms;

    public ChatManager() {
        this.eventDispatcher = new ChatEventDispatcher();
        this.channelManager = new ChannelManager(eventDispatcher);
        this.users = new ConcurrentHashMap<>();
        this.privateChatRooms = new ConcurrentHashMap<>();
        LOGGER.info("ChatManager initialized");
    }

    /**
     * 获取单例实例
     */
    public static synchronized ChatManager getInstance() {
        if (instance == null) {
            instance = new ChatManager();
        }
        return instance;
    }

    /**
     * 重置单例（用于测试）
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.clear();
        }
        instance = null;
    }

    /**
     * 注册玩家
     */
    public ChatUser registerUser(UUID playerId, String playerName) {
        ChatUser user = new ChatUser(playerId, playerName);
        users.put(playerId, user);
        LOGGER.info("Registered chat user: {} ({})", playerName, playerId);
        return user;
    }

    /**
     * 获取玩家
     */
    public ChatUser getUser(UUID playerId) {
        return users.get(playerId);
    }

    /**
     * 移除玩家
     */
    public void unregisterUser(UUID playerId) {
        ChatUser user = users.remove(playerId);
        if (user != null) {
            LOGGER.info("Unregistered chat user: {}", user.getPlayerName());
        }
    }

    /**
     * 发送消息到频道
     */
    public void sendChannelMessage(String channelId, UUID senderId, String senderName, String content) {
        if (channelId == null || channelId.isBlank()) {
            LOGGER.debug("Skip sending message from {}: no channel selected", senderName);
            return;
        }

        if (channelManager.getChannel(channelId) == null) {
            LOGGER.debug("Skip sending message from {}: channel {} does not exist", senderName, channelId);
            return;
        }

        ChatMessage message = new ChatMessage(senderId, senderName, content, channelId, false);
        channelManager.addMessage(channelId, message);
        eventDispatcher.dispatchMessageSent(message);
        LOGGER.debug("Message sent to channel {}: {}", channelId, senderName);
    }

    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(UUID senderId, String senderName, UUID recipientId, String content) {
        String roomId = PrivateChatRoom.generateRoomId(senderId, recipientId);
        PrivateChatRoom room = privateChatRooms.get(roomId);

        // 如果房间不存在，创建新房间
        if (room == null) {
            ChatUser recipient = users.get(recipientId);
            if (recipient == null) {
                LOGGER.warn("Recipient {} not found", recipientId);
                return;
            }
            room = new PrivateChatRoom(senderId, recipientId, senderName, recipient.getPlayerName());
            privateChatRooms.put(roomId, room);
        }

        ChatMessage message = new ChatMessage(senderId, senderName, content, roomId, true);
        eventDispatcher.dispatchMessageSent(message);
        eventDispatcher.dispatchMessageReceived(message, recipientId);
        LOGGER.debug("Private message sent from {} to {}", senderName, recipientId);
    }

    // Getters
    public ChannelManager getChannelManager() { return channelManager; }
    public ChatEventDispatcher getEventDispatcher() { return eventDispatcher; }
    public Map<UUID, ChatUser> getUsers() { return new HashMap<>(users); }

    /**
     * 清空所有数据
     */
    public void clear() {
        users.clear();
        privateChatRooms.clear();
        channelManager.clear();
        eventDispatcher.clearListeners();
        LOGGER.info("ChatManager cleared");
    }
}

/**
 * 辅助类：用于生成 PrivateChatRoom 的房间ID
 */
class PrivateChatRoomUtils {
    static String generateRoomId(UUID p1, UUID p2) {
        return (p1.compareTo(p2) < 0) ?
                p1.toString() + ":" + p2.toString() :
                p2.toString() + ":" + p1.toString();
    }
}
