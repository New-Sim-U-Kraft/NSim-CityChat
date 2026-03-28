package com.lokins.citychat.integration;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.data.MessageAction;
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
            LOGGER.warn("[SK-NOTIFY] sendNotification 收到 null 通知或 null recipientId，跳过");
            return false;
        }

        String notifyId = notification.getId() != null ? notification.getId().toString() : "?";
        String cat = notification.getCategory() != null ? notification.getCategory().getKey() : "null";
        String cityName = getNotificationCityName(notification,
                notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().toString() : "global");
        String contentPreview = notification.getContent() != null
                ? notification.getContent().substring(0, Math.min(60, notification.getContent().length())) : "";

        // RECEIVE — 链路日志
        LOGGER.info("[SK-NOTIFY] RECEIVE | id={} | recipient={} | category={} | cityName={} | content={}",
                notifyId, notification.getRecipientId(), cat, cityName, contentPreview);

        // 1. 存储到 NotificationStore
        StoredNotification stored = toStored(notification);
        store.addNotification(notification.getRecipientId(), stored);
        int storeSize = store.getPlayerNotifications(notification.getRecipientId()).size();
        // STORE — 链路日志
        LOGGER.info("[SK-NOTIFY] STORE | id={} | recipient={} | storeSize={}",
                notifyId, notification.getRecipientId(), storeSize);

        // 2. 桥接到 CityChat 频道
        bridgeToChannel(notification);

        // 3. 定期保存
        store.saveAll();
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
                // 尝试通过 channelId 模式匹配已有频道（新格式：大写 key）
                channelId = "sk_notify_" + cityIdStr + "_" + categoryKey;
                channel = channelManager.getChannel(channelId);
                // 兼容旧格式（小写 key）
                if (channel == null) {
                    String legacyId = "sk_notify_" + cityIdStr + "_" + categoryKey.toLowerCase();
                    channel = channelManager.getChannel(legacyId);
                    if (channel != null) {
                        // 迁移旧频道：更新 channelId 缓存，使用旧 ID 继续
                        channelId = legacyId;
                    }
                }
                if (channel != null) {
                    channelCache.put(cacheKey, channelId);
                }
            }

            boolean channelCreated = false;
            if (channel == null) {
                // 构造频道显示名：优先使用通知自带的城市名（新 API），fallback 到旧方式
                String cityName = getNotificationCityName(notification, cityIdStr);
                String categoryLabel = getNotificationCategoryLabel(notification);
                String channelDisplayName = cityName + categoryLabel;

                LOGGER.info("[SK-NOTIFY] 创建城市通知频道: id={}, 城市={}, 分类={}", channelId, cityName, categoryKey);
                channel = channelManager.createChannel(
                        channelId, channelDisplayName, "SimuKraft 通知频道 - " + cityName,
                        ChatChannel.ChannelType.NOTIFICATION, ChatChannel.GroupAccess.NORMAL,
                        "", SYSTEM_UUID
                );
                if (channel != null) {
                    channelCache.put(cacheKey, channelId);
                    channelCreated = true;
                    LOGGER.info("[SK-NOTIFY] 频道创建成功: #{} {}", channel.getGroupNumber(), channelDisplayName);
                } else {
                    LOGGER.warn("[SK-NOTIFY] 频道创建失败! channelId={}", channelId);
                }
            }

            if (channel == null) {
                LOGGER.warn("[SK-NOTIFY] 无法获取或创建频道，桥接中止");
                return;
            }

            // 确保接收者是频道成员
            boolean memberAdded = false;
            UUID recipientId = notification.getRecipientId();
            if (!channel.isMember(recipientId)) {
                channelManager.joinChannel(channel.getChannelId(), recipientId, "");
                memberAdded = true;
                LOGGER.info("[SK-NOTIFY] 已将接收者 {} 加入频道 {}", recipientId, channel.getChannelId());
            }

            // 频道结构变更（新建频道/新增成员）时广播快照，让客户端刷新频道列表
            if (channelCreated || memberAdded) {
                LOGGER.info("[SK-NOTIFY] 频道结构变更(created={}, memberAdded={})，广播快照", channelCreated, memberAdded);
                GroupOperationPacket.broadcastPerPlayerSnapshots();
            }

            // 投递消息到频道
            String senderName = notification.getSender() != null ? notification.getSender() : "系统";
            String title = notification.getTitle() != null ? notification.getTitle() : "";
            String content = notification.getContent() != null ? notification.getContent() : "";
            String messageContent = title.isEmpty() ? content : "[" + title + "] " + content;

            List<MessageAction> actions = extractActions(notification);
            ChatMessage chatMessage = new ChatMessage(SYSTEM_UUID, senderName, messageContent,
                    channel.getChannelId(), false, System.currentTimeMillis(), UUID.randomUUID(), actions);
            channelManager.addMessage(channel.getChannelId(), chatMessage);

            String notifyId = notification.getId() != null ? notification.getId().toString() : "?";
            // BRIDGE — 链路日志
            LOGGER.info("[SK-NOTIFY] BRIDGE | id={} | channelId={} | channelName={} | memberAdded={}",
                    notifyId, channel.getChannelId(), channel.getDisplayName(), memberAdded);

            // 广播到频道成员的客户端
            broadcastToChannelMembers(channel, chatMessage, notifyId);
        } catch (Exception e) {
            LOGGER.warn("[SK-NOTIFY] 桥接通知到频道失败", e);
        }
    }

    private void broadcastToChannelMembers(ChatChannel channel, ChatMessage message, String notifyId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("[SK-NOTIFY] BROADCAST | id={} | channelId={} | error=server_null", notifyId, channel.getChannelId());
            return;
        }

        ChatMessageBroadcastPacket packet = new ChatMessageBroadcastPacket(
                message.getChannelId(), message.getSenderId(), message.getSenderName(),
                message.getContent(), message.getTimestamp(), message.getMessageId(),
                message.getActions()
        );

        int onlineCount = 0;
        for (UUID memberId : channel.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                onlineCount++;
            }
        }
        // BROADCAST — 链路日志
        LOGGER.info("[SK-NOTIFY] BROADCAST | id={} | channelId={} | onlineRecipients={}",
                notifyId, channel.getChannelId(), onlineCount);
    }

    /**
     * 推送通知到在线客户端（触发右上角弹窗）。
     */
    private void pushToClient(MessageNotification notification) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("[SK-NOTIFY] server==null，无法推送");
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(notification.getRecipientId());
        if (target == null) {
            LOGGER.info("[SK-NOTIFY] 步骤3: 接收者 {} 不在线，跳过推送", notification.getRecipientId());
            return;
        }

        String title = notification.getTitle() != null ? notification.getTitle() : "通知";
        String content = notification.getContent() != null ? notification.getContent() : "";
        String senderName = notification.getSender() != null ? notification.getSender() : "系统";
        String category = notification.getCategory() != null ? notification.getCategory().getKey() : "other";

        List<MessageAction> actions = extractActions(notification);
        NotificationPushPacket packet = new NotificationPushPacket(title, content, category, senderName, actions);
        ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), packet);
        LOGGER.info("[SK-NOTIFY] 步骤3: NotificationPush 已发送给 {}({}), 标题={}", target.getName().getString(), notification.getRecipientId(), title);
    }

    /**
     * 从 MessageNotification 的 metadata 中提取 action 按钮定义。
     * 约定格式：action.count, action.N.label, action.N.command, action.N.color
     */
    private List<MessageAction> extractActions(MessageNotification notification) {
        Map<String, String> meta = notification.getMetadata();
        if (meta == null) return Collections.emptyList();

        int count = notification.getActionCount();
        if (count <= 0) return Collections.emptyList();

        List<MessageAction> actions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = meta.get("action." + i + ".label");
            String command = meta.get("action." + i + ".command");
            String colorStr = meta.get("action." + i + ".color");
            if (label == null || command == null) continue;

            int color = 0xFFFFFF;
            if (colorStr != null) {
                try { color = Integer.parseInt(colorStr, 16); }
                catch (NumberFormatException ignored) {}
            }
            actions.add(new MessageAction(label, command, color));
        }
        LOGGER.debug("[SK-NOTIFY] 提取到 {} 个 action 按钮", actions.size());
        return actions;
    }

    /** 获取通知的城市名：优先用新 API getCityName()，fallback 到 CityRoleBridge */
    private String getNotificationCityName(MessageNotification notification, String cityIdStr) {
        String name = notification.getCityName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return resolveCityName(cityIdStr);
    }

    /** 获取通知的分类标签：优先用新 API getCategoryDisplayName()，fallback 到硬编码 */
    private String getNotificationCategoryLabel(MessageNotification notification) {
        String label = notification.getCategoryDisplayName();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return getCategoryLabel(notification.getCategory());
    }

    /** 分类标签：直接使用枚举自带的显示名，无需硬编码映射 */
    private String getCategoryLabel(MessageCategory category) {
        if (category == null) return "[其他]";
        return category.getDisplayName();
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
        // 兼容旧数据：小写 key → 大写匹配
        n.setCategory(MessageCategory.fromKey(
                s.category != null ? s.category.toUpperCase() : "OTHER"));
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
