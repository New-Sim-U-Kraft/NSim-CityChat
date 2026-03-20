package com.lokins.citychat.integration;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.manager.NotificationStore;
import com.lokins.citychat.manager.NotificationStore.StoredNotification;
import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.ChatMessageBroadcastPacket;
import com.lokins.citychat.network.GroupOperationPacket;
import com.lokins.citychat.network.NotificationPushPacket;
import com.mojang.logging.LogUtils;
import com.xiaoliang.simukraft.notification.IMessageNotificationService;
import com.xiaoliang.simukraft.notification.MessageCategory;
import com.xiaoliang.simukraft.notification.MessageNotification;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CityChat 对 simukraft IMessageNotificationService 的完整实现。
 * 通知存储到 NotificationStore，同时桥接到 CityChat 频道系统。
 */
public class CityChatNotificationService implements IMessageNotificationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID SYSTEM_UUID = new UUID(0, 0);

    private final NotificationStore store;

    // "cityId_categoryKey" -> channelId 的映射缓存
    private final Map<String, String> channelCache = new HashMap<>();

    public CityChatNotificationService() {
        this.store = new NotificationStore();
        this.store.load();
    }

    public void saveAll() {
        store.saveAll();
    }

    @Override
    public boolean sendNotification(MessageNotification notification) {
        if (notification == null || notification.getRecipientId() == null) {
            LOGGER.warn("[CC通知] sendNotification 收到 null 通知或 null recipientId，跳过");
            return false;
        }

        String cat = notification.getCategory() != null ? notification.getCategory().getKey() : "null";
        LOGGER.info("[CC通知] ===== 收到通知 =====");
        LOGGER.info("[CC通知]   分类={}, 标题={}, 发送者={}", cat,
                notification.getTitle(), notification.getSender());
        LOGGER.info("[CC通知]   内容={}", notification.getContent());
        LOGGER.info("[CC通知]   接收者={}", notification.getRecipientId());

        // 1. 存储到 NotificationStore
        StoredNotification stored = toStored(notification);
        store.addNotification(notification.getRecipientId(), stored);
        LOGGER.info("[CC通知] 步骤1: 已存储到 NotificationStore");

        // 2. 桥接到 CityChat 频道：将通知转为消息投递到对应分类频道
        //    bridgeToChannel 内部的 broadcastToChannelMembers 已经会触发客户端弹窗，
        //    不再额外调 pushToClient，避免同一条通知弹两次
        bridgeToChannel(notification);

        // 3. 定期保存
        store.saveAll();

        LOGGER.info("[CC通知] ===== 通知处理完毕 =====");
        return true;
    }

    @Override
    public List<MessageNotification> getNotifications(UUID playerId) {
        if (playerId == null) return Collections.emptyList();
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        synchronized (list) {
            return list.stream()
                    .sorted(Comparator.comparingLong((StoredNotification n) -> n.timestamp).reversed())
                    .map(this::fromStored)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<MessageNotification> getNotificationsByCategory(UUID playerId, MessageCategory category) {
        if (playerId == null || category == null) return Collections.emptyList();
        String catKey = category.getKey();
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        synchronized (list) {
            return list.stream()
                    .filter(n -> catKey.equals(n.category))
                    .sorted(Comparator.comparingLong((StoredNotification n) -> n.timestamp).reversed())
                    .map(this::fromStored)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public int getUnreadCount(UUID playerId) {
        if (playerId == null) return 0;
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        synchronized (list) {
            return (int) list.stream().filter(n -> !n.isRead).count();
        }
    }

    @Override
    public int getUnreadCountByCategory(UUID playerId, MessageCategory category) {
        if (playerId == null || category == null) return 0;
        String catKey = category.getKey();
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        synchronized (list) {
            return (int) list.stream().filter(n -> !n.isRead && catKey.equals(n.category)).count();
        }
    }

    @Override
    public boolean markAsRead(UUID notificationId) {
        if (notificationId == null) return false;
        StoredNotification found = store.findById(notificationId);
        if (found != null) {
            found.isRead = true;
            return true;
        }
        return false;
    }

    @Override
    public int markAllAsRead(UUID playerId) {
        if (playerId == null) return 0;
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        int count = 0;
        synchronized (list) {
            for (StoredNotification n : list) {
                if (!n.isRead) {
                    n.isRead = true;
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean deleteNotification(UUID notificationId) {
        if (notificationId == null) return false;
        return store.removeById(notificationId);
    }

    @Override
    public int deleteAllNotifications(UUID playerId) {
        if (playerId == null) return 0;
        return store.removeAllForPlayer(playerId);
    }

    @Override
    public int deleteNotificationsByCategory(UUID playerId, MessageCategory category) {
        if (playerId == null || category == null) return 0;
        String catKey = category.getKey();
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        int count = 0;
        synchronized (list) {
            Iterator<StoredNotification> it = list.iterator();
            while (it.hasNext()) {
                if (catKey.equals(it.next().category)) {
                    it.remove();
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public MessageNotification getNotification(UUID notificationId) {
        if (notificationId == null) return null;
        StoredNotification found = store.findById(notificationId);
        return found != null ? fromStored(found) : null;
    }

    @Override
    public int clearNotificationsBefore(UUID playerId, long beforeTimestamp) {
        if (playerId == null) return 0;
        List<StoredNotification> list = store.getPlayerNotifications(playerId);
        int count = 0;
        synchronized (list) {
            Iterator<StoredNotification> it = list.iterator();
            while (it.hasNext()) {
                if (it.next().timestamp < beforeTimestamp) {
                    it.remove();
                    count++;
                }
            }
        }
        return count;
    }

    // ========== 桥接逻辑 ==========

    /**
     * 将通知桥接到 CityChat 频道系统。
     * 根据 category 找到或创建对应的通知频道，将通知作为消息投递。
     */
    private void bridgeToChannel(MessageNotification notification) {
        try {
            ChatManager chatManager = ChatManager.getInstance();
            var channelManager = chatManager.getChannelManager();

            // 从通知中提取城市 ID（relatedEntityId），每个城市独立频道
            UUID relatedEntityId = notification.getRelatedEntityId();
            String cityIdStr = relatedEntityId != null ? relatedEntityId.toString() : "global";
            String categoryKey = notification.getCategory() != null ? notification.getCategory().getKey() : "other";
            String cacheKey = cityIdStr + "_" + categoryKey;

            // 查找或创建城市专属通知频道
            String channelId = channelCache.get(cacheKey);
            ChatChannel channel = channelId != null ? channelManager.getChannel(channelId) : null;

            if (channel == null) {
                // 尝试通过 channelId 模式匹配已有频道
                channelId = "sk_notify_" + cityIdStr + "_" + categoryKey;
                channel = channelManager.getChannel(channelId);
                if (channel != null) {
                    channelCache.put(cacheKey, channelId);
                }
            }

            boolean channelCreated = false;
            if (channel == null) {
                // 构造频道显示名：尝试获取城市名
                String cityName = resolveCityName(cityIdStr);
                String categoryLabel = getCategoryLabel(notification.getCategory());
                String channelDisplayName = cityName + categoryLabel;

                LOGGER.info("[CC桥接] 创建城市通知频道: id={}, 城市={}, 分类={}", channelId, cityName, categoryKey);
                channel = channelManager.createChannel(
                        channelId, channelDisplayName, "SimuKraft 通知频道 - " + cityName,
                        ChatChannel.ChannelType.NOTIFICATION, ChatChannel.GroupAccess.NORMAL,
                        "", SYSTEM_UUID
                );
                if (channel != null) {
                    channelCache.put(cacheKey, channelId);
                    channelCreated = true;
                    LOGGER.info("[CC桥接] 频道创建成功: #{} {}", channel.getGroupNumber(), channelDisplayName);
                } else {
                    LOGGER.warn("[CC桥接] 频道创建失败! channelId={}", channelId);
                }
            }

            if (channel == null) {
                LOGGER.warn("[CC桥接] 无法获取或创建频道，桥接中止");
                return;
            }

            // 确保接收者是频道成员
            boolean memberAdded = false;
            UUID recipientId = notification.getRecipientId();
            if (!channel.isMember(recipientId)) {
                channelManager.joinChannel(channel.getChannelId(), recipientId, "");
                memberAdded = true;
                LOGGER.info("[CC桥接] 已将接收者 {} 加入频道 {}", recipientId, channel.getChannelId());
            }

            // 频道结构变更（新建频道/新增成员）时广播快照，让客户端刷新频道列表
            if (channelCreated || memberAdded) {
                LOGGER.info("[CC桥接] 频道结构变更(created={}, memberAdded={})，广播快照", channelCreated, memberAdded);
                GroupOperationPacket.broadcastPerPlayerSnapshots();
            }

            // 投递消息到频道
            String senderName = notification.getSender() != null ? notification.getSender() : "系统";
            String title = notification.getTitle() != null ? notification.getTitle() : "";
            String content = notification.getContent() != null ? notification.getContent() : "";
            String messageContent = title.isEmpty() ? content : "[" + title + "] " + content;

            ChatMessage chatMessage = new ChatMessage(SYSTEM_UUID, senderName, messageContent, channel.getChannelId(), false);
            channelManager.addMessage(channel.getChannelId(), chatMessage);
            LOGGER.info("[CC桥接] 步骤2: 消息已投递到频道 {}, 内容={}", channel.getChannelId(), messageContent);

            // 广播到频道成员的客户端
            broadcastToChannelMembers(channel, chatMessage);
        } catch (Exception e) {
            LOGGER.warn("[CC桥接] 桥接通知到频道失败", e);
        }
    }

    private void broadcastToChannelMembers(ChatChannel channel, ChatMessage message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("[CC广播] server==null，无法广播消息");
            return;
        }

        ChatMessageBroadcastPacket packet = new ChatMessageBroadcastPacket(
                message.getChannelId(), message.getSenderId(), message.getSenderName(),
                message.getContent(), message.getTimestamp(), message.getMessageId()
        );

        int onlineCount = 0;
        int totalMembers = channel.getMembers().size();
        for (UUID memberId : channel.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                onlineCount++;
            }
        }
        LOGGER.info("[CC广播] 步骤2b: ChatMessageBroadcast 已发送到 {}/{} 名在线成员, 频道={}", onlineCount, totalMembers, channel.getChannelId());
    }

    /**
     * 推送通知到在线客户端（触发右上角弹窗）。
     */
    private void pushToClient(MessageNotification notification) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("[CC推送] server==null，无法推送");
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(notification.getRecipientId());
        if (target == null) {
            LOGGER.info("[CC推送] 步骤3: 接收者 {} 不在线，跳过推送", notification.getRecipientId());
            return;
        }

        String title = notification.getTitle() != null ? notification.getTitle() : "通知";
        String content = notification.getContent() != null ? notification.getContent() : "";
        String senderName = notification.getSender() != null ? notification.getSender() : "系统";
        String category = notification.getCategory() != null ? notification.getCategory().getKey() : "other";

        NotificationPushPacket packet = new NotificationPushPacket(title, content, category, senderName);
        ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), packet);
        LOGGER.info("[CC推送] 步骤3: NotificationPush 已发送给 {}({}), 标题={}", target.getName().getString(), notification.getRecipientId(), title);
    }

    /** 分类标签（不含城市名，用于拼接频道显示名） */
    private String getCategoryLabel(MessageCategory category) {
        if (category == null) return "[其他]";
        return switch (category) {
            case SYSTEM -> "[系统]";
            case NPC -> "[NPC]";
            case CITY -> "[城市]";
            case OFFICIAL -> "[官员]";
            case PLAYER_MESSAGE -> "[玩家]";
            case GROUP_MESSAGE -> "[群组]";
            case BUSINESS -> "[商业]";
            case OTHER -> "[其他]";
        };
    }

    /** 解析城市名：优先从 CityRoleBridge 获取，否则用 UUID 前 8 位 */
    private String resolveCityName(String cityIdStr) {
        if ("global".equals(cityIdStr)) {
            return "全局";
        }
        String name = CityRoleBridge.getInstance().getCityName(cityIdStr);
        if (name != null && !name.isBlank()) {
            return name;
        }
        // 截取 UUID 前 8 位作为简称
        return cityIdStr.length() > 8 ? cityIdStr.substring(0, 8) : cityIdStr;
    }

    // ========== 序列化转换 ==========

    private StoredNotification toStored(MessageNotification n) {
        StoredNotification s = new StoredNotification();
        s.id = n.getId().toString();
        s.timestamp = n.getTimestamp();
        s.sender = n.getSender();
        s.senderType = n.getSenderType();
        s.title = n.getTitle();
        s.content = n.getContent();
        s.recipientId = n.getRecipientId() != null ? n.getRecipientId().toString() : null;
        s.isRead = n.isRead();
        s.category = n.getCategory() != null ? n.getCategory().getKey() : "other";
        s.relatedEntityId = n.getRelatedEntityId() != null ? n.getRelatedEntityId().toString() : null;
        s.relatedEntityType = n.getRelatedEntityType();
        s.metadata = n.getMetadata() != null ? new HashMap<>(n.getMetadata()) : new HashMap<>();
        return s;
    }

    private MessageNotification fromStored(StoredNotification s) {
        MessageNotification n = new MessageNotification();
        n.setId(parseUuid(s.id));
        n.setTimestamp(s.timestamp);
        n.setSender(s.sender);
        n.setSenderType(s.senderType);
        n.setTitle(s.title);
        n.setContent(s.content);
        n.setRecipientId(parseUuid(s.recipientId));
        n.setRead(s.isRead);
        n.setCategory(MessageCategory.fromKey(s.category));
        n.setRelatedEntityId(parseUuid(s.relatedEntityId));
        n.setRelatedEntityType(s.relatedEntityType);
        n.setMetadata(s.metadata != null ? new HashMap<>(s.metadata) : new HashMap<>());
        return n;
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
