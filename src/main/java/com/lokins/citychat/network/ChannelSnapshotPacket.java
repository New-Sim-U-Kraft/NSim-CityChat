package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.manager.ChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端下发群组快照，客户端用来刷新列表与搜索。
 * 密码字段永远不发送给客户端。
 * 包含最近 50 条消息历史。
 */
public class ChannelSnapshotPacket {
    private static final int MAX_SNAPSHOT_MESSAGES = 50;

    private final List<ChannelEntry> channels;

    public ChannelSnapshotPacket(List<ChannelEntry> channels) {
        this.channels = channels;
    }

    /**
     * 从频道列表创建快照（不包含密码）。
     */
    public static ChannelSnapshotPacket fromChannels(List<ChatChannel> source) {
        List<ChannelEntry> entries = new ArrayList<>();
        for (ChatChannel channel : source) {
            entries.add(ChannelEntry.fromChannel(channel));
        }
        return new ChannelSnapshotPacket(entries);
    }

    /**
     * 为指定玩家创建快照：只包含该玩家可见的频道，不含密码。
     */
    public static ChannelSnapshotPacket forPlayer(UUID playerId) {
        List<ChatChannel> visible = ChatManager.getInstance()
                .getChannelManager().getVisibleChannelsForPlayer(playerId);
        long notifyCount = visible.stream().filter(ch -> ch.getType() == ChatChannel.ChannelType.NOTIFICATION).count();
        com.mojang.logging.LogUtils.getLogger().info("[CC快照] 为玩家 {} 生成快照: 共 {} 个频道(其中 NOTIFICATION={})",
                playerId, visible.size(), notifyCount);
        for (ChatChannel ch : visible) {
            if (ch.getType() == ChatChannel.ChannelType.NOTIFICATION) {
                com.mojang.logging.LogUtils.getLogger().info("[CC快照]   通知频道: id={}, name={}, members={}", ch.getChannelId(), ch.getDisplayName(), ch.getMemberCount());
            }
        }
        return fromChannels(visible);
    }

    public static void encode(ChannelSnapshotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.channels.size());
        for (ChannelEntry entry : msg.channels) {
            entry.encode(buf);
        }
    }

    public static ChannelSnapshotPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ChannelEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(ChannelEntry.decode(buf));
        }
        return new ChannelSnapshotPacket(entries);
    }

    public static void handle(ChannelSnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();

        // 如果本机同时是服务端（LAN 房主 / 单人游戏），不用快照覆盖，
        // 因为服务端的 ChannelManager 才是权威数据源，覆盖会丢失其他玩家的频道。
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            ctx.setPacketHandled(true);
            return;
        }

        List<ChatChannel> rebuilt = new ArrayList<>();

        for (ChannelEntry entry : msg.channels) {
            ChatChannel channel = new ChatChannel(
                    entry.channelId,
                    entry.groupNumber,
                    entry.displayName,
                    entry.description,
                    entry.type,
                    entry.access,
                    "",  // 客户端不需要密码
                    entry.ownerId
            );

            for (UUID memberId : entry.members) {
                channel.addMember(memberId);
            }
            for (UUID adminId : entry.admins) {
                channel.addAdmin(entry.ownerId, adminId);
            }

            // 客户端翻译通知频道名（服务端存的是英文）
            if (channel.getType() == ChatChannel.ChannelType.NOTIFICATION) {
                String translated = resolveNotifyDisplayNameClient(entry.channelId, entry.displayName);
                if (translated != null) channel.setDisplayName(translated);
            }

            // 恢复消息历史（如有 Component JSON 则在客户端翻译）
            for (MessageEntry msgEntry : entry.messages) {
                String displayContent = msgEntry.content;
                String displaySender = msgEntry.senderName;
                if (msgEntry.componentJson != null) {
                    try {
                        var comp = net.minecraft.network.chat.Component.Serializer.fromJson(msgEntry.componentJson);
                        if (comp != null) displayContent = comp.getString();
                    } catch (Exception ignored) {}
                }
                if (msgEntry.senderJson != null) {
                    try {
                        var comp = net.minecraft.network.chat.Component.Serializer.fromJson(msgEntry.senderJson);
                        if (comp != null) displaySender = comp.getString();
                    } catch (Exception ignored) {}
                }
                ChatMessage chatMessage = new ChatMessage(
                        msgEntry.senderId,
                        displaySender,
                        displayContent,
                        entry.channelId,
                        false,
                        msgEntry.timestamp,
                        msgEntry.messageId
                );
                channel.addMessage(chatMessage);
            }

            rebuilt.add(channel);
        }

        // 注册成员名字到客户端 ChatManager（解决 UUID 显示问题）
        ChatManager cm = ChatManager.getInstance();
        for (ChannelEntry entry : msg.channels) {
            if (entry.memberNames != null) {
                for (var nameEntry : entry.memberNames.entrySet()) {
                    if (cm.getUser(nameEntry.getKey()) == null) {
                        cm.registerUser(nameEntry.getKey(), nameEntry.getValue());
                    }
                }
            }
        }

        cm.getChannelManager().replaceFromSnapshot(rebuilt);
        ctx.setPacketHandled(true);
    }

    /**
     * 客户端翻译通知频道名：从频道 ID 解析 categoryKey，用客户端语言翻译分类标签。
     */
    private static String resolveNotifyDisplayNameClient(String channelId, String fallback) {
        if (!channelId.startsWith("sk_notify_")) return null;
        String rest = channelId.substring("sk_notify_".length());
        String cityName;
        String categoryKey;
        if (rest.startsWith("global_")) {
            cityName = "global";
            categoryKey = rest.substring("global_".length());
        } else if (rest.length() > 37 && rest.charAt(36) == '_') {
            // 城市名保留服务端传来的（已是正确的城市名）
            cityName = null; // 从 fallback 中提取
            categoryKey = rest.substring(37);
        } else {
            return null;
        }

        // 用翻译键在客户端翻译分类标签
        String translatedCategory;
        try {
            var category = com.xiaoliang.simukraft.notification.MessageCategory.fromKey(categoryKey.toUpperCase());
            translatedCategory = net.minecraft.network.chat.Component.translatable(category.getTranslationKey()).getString();
        } catch (Exception e) {
            return null;
        }

        // 从 fallback 显示名中提取城市名部分（去掉英文分类后缀）
        if (cityName == null) {
            // fallback 格式: "城市名[English Category]"，提取城市名
            int bracketIdx = fallback.lastIndexOf('[');
            cityName = bracketIdx > 0 ? fallback.substring(0, bracketIdx) : fallback;
        }

        return cityName + translatedCategory;
    }

    static class MessageEntry {
        UUID senderId;
        String senderName;
        String content;
        long timestamp;
        UUID messageId;
        String componentJson; // 可选的 Component JSON，客户端翻译用
        String senderJson;    // 可选的发送者 Component JSON

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(senderId);
            buf.writeUtf(senderName, 32);
            buf.writeUtf(content, 256);
            buf.writeLong(timestamp);
            buf.writeUUID(messageId);
            buf.writeUtf(componentJson != null ? componentJson : "", 2048);
            buf.writeUtf(senderJson != null ? senderJson : "", 512);
        }

        static MessageEntry decode(FriendlyByteBuf buf) {
            MessageEntry entry = new MessageEntry();
            entry.senderId = buf.readUUID();
            entry.senderName = buf.readUtf(32);
            entry.content = buf.readUtf(256);
            entry.timestamp = buf.readLong();
            entry.messageId = buf.readUUID();
            entry.componentJson = buf.readUtf(2048);
            if (entry.componentJson.isEmpty()) entry.componentJson = null;
            entry.senderJson = buf.readUtf(512);
            if (entry.senderJson.isEmpty()) entry.senderJson = null;
            return entry;
        }
    }

    private static class ChannelEntry {
        String channelId;
        int groupNumber;
        String displayName;
        String description;
        ChatChannel.ChannelType type;
        ChatChannel.GroupAccess access;
        UUID ownerId;
        List<UUID> members;
        List<UUID> admins;
        List<MessageEntry> messages;
        /** 成员 UUID → 玩家名映射（服务端解析后下发） */
        Map<UUID, String> memberNames;

        static ChannelEntry fromChannel(ChatChannel channel) {
            ChannelEntry entry = new ChannelEntry();
            entry.channelId = channel.getChannelId();
            entry.groupNumber = channel.getGroupNumber();
            entry.displayName = resolveNotifyDisplayName(channel);
            entry.description = channel.getDescription();
            entry.type = channel.getType();
            entry.access = channel.getAccess();
            entry.ownerId = channel.getOwnerId();
            entry.members = new ArrayList<>(channel.getMembers());
            entry.admins = new ArrayList<>(channel.getAdmins());

            // 解析成员名字
            entry.memberNames = new HashMap<>();
            ChatManager cm = ChatManager.getInstance();
            for (UUID memberId : entry.members) {
                var user = cm.getUser(memberId);
                if (user != null && user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                    entry.memberNames.put(memberId, user.getDisplayName());
                }
            }

            // 取最近 MAX_SNAPSHOT_MESSAGES 条消息
            List<ChatMessage> history = channel.getMessageHistory(MAX_SNAPSHOT_MESSAGES);
            entry.messages = new ArrayList<>();
            for (ChatMessage msg : history) {
                MessageEntry me = new MessageEntry();
                me.senderId = msg.getSenderId();
                me.senderName = msg.getSenderName();
                me.content = msg.getContent();
                me.timestamp = msg.getTimestamp();
                me.messageId = msg.getMessageId();
                me.componentJson = msg.getComponentJson();
                me.senderJson = msg.getSenderJson();
                entry.messages.add(me);
            }
            return entry;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(channelId, 128);
            buf.writeVarInt(groupNumber);
            buf.writeUtf(displayName, 64);
            buf.writeUtf(description == null ? "" : description, 256);
            buf.writeEnum(type);
            buf.writeEnum(access);
            buf.writeUUID(ownerId);

            buf.writeVarInt(members.size());
            for (UUID member : members) {
                buf.writeUUID(member);
            }

            buf.writeVarInt(admins.size());
            for (UUID admin : admins) {
                buf.writeUUID(admin);
            }

            // 序列化成员名字映射
            buf.writeVarInt(memberNames.size());
            for (var e : memberNames.entrySet()) {
                buf.writeUUID(e.getKey());
                buf.writeUtf(e.getValue(), 64);
            }

            // 序列化消息历史
            buf.writeVarInt(messages.size());
            for (MessageEntry msg : messages) {
                msg.encode(buf);
            }
        }

        static ChannelEntry decode(FriendlyByteBuf buf) {
            ChannelEntry entry = new ChannelEntry();
            entry.channelId = buf.readUtf(128);
            entry.groupNumber = buf.readVarInt();
            entry.displayName = buf.readUtf(64);
            entry.description = buf.readUtf(256);
            entry.type = buf.readEnum(ChatChannel.ChannelType.class);
            entry.access = buf.readEnum(ChatChannel.GroupAccess.class);
            entry.ownerId = buf.readUUID();

            int memberSize = buf.readVarInt();
            Set<UUID> memberSet = new HashSet<>();
            for (int i = 0; i < memberSize; i++) {
                memberSet.add(buf.readUUID());
            }
            entry.members = new ArrayList<>(memberSet);

            int adminSize = buf.readVarInt();
            Set<UUID> adminSet = new HashSet<>();
            for (int i = 0; i < adminSize; i++) {
                adminSet.add(buf.readUUID());
            }
            entry.admins = new ArrayList<>(adminSet);

            // 反序列化成员名字映射
            int nameCount = buf.readVarInt();
            entry.memberNames = new HashMap<>(nameCount);
            for (int i = 0; i < nameCount; i++) {
                UUID uid = buf.readUUID();
                String name = buf.readUtf(64);
                entry.memberNames.put(uid, name);
            }

            // 反序列化消息历史
            int msgSize = buf.readVarInt();
            entry.messages = new ArrayList<>(msgSize);
            for (int i = 0; i < msgSize; i++) {
                entry.messages.add(MessageEntry.decode(buf));
            }

            return entry;
        }

        /**
         * 通知频道：每次快照时尝试用最新城市名刷新显示名。
         * 频道 ID 格式 sk_notify_<cityId>_<categoryKey>，
         * 如果当前显示名还包含 UUID 样式的前缀，说明创建时城市名没解析到，现在重试。
         */
        private static String resolveNotifyDisplayName(ChatChannel channel) {
            if (channel.getType() != ChatChannel.ChannelType.NOTIFICATION) {
                return channel.getDisplayName();
            }

            String id = channel.getChannelId();
            if (!id.startsWith("sk_notify_")) {
                return channel.getDisplayName();
            }

            // 解析 cityId 和 categoryKey：sk_notify_<cityId>_<categoryKey>
            String rest = id.substring("sk_notify_".length());
            // cityId 可能是 UUID (36 chars) 或 "global"
            String cityIdStr;
            String categoryKey;
            if (rest.startsWith("global_")) {
                cityIdStr = "global";
                categoryKey = rest.substring("global_".length());
            } else if (rest.length() > 37 && rest.charAt(36) == '_') {
                cityIdStr = rest.substring(0, 36);
                categoryKey = rest.substring(37);
            } else {
                return channel.getDisplayName();
            }

            // 尝试获取城市名
            var bridge = com.lokins.citychat.integration.CityRoleBridge.getInstance();
            String cityName = "global".equals(cityIdStr) ? "全局" : bridge.getCityName(cityIdStr);
            if (cityName == null || cityName.isBlank()) {
                // 仍然解析不到，保留现有名称
                return channel.getDisplayName();
            }

            // 通过 MessageCategory.fromKey 解析显示名（自动兼容新旧 key）
            String categoryLabel = com.xiaoliang.simukraft.notification.MessageCategory
                    .fromKey(categoryKey.toUpperCase()).getDisplayName();

            String newName = cityName + categoryLabel;

            // 如果名字变了，同时更新源频道对象（持久化修复）
            if (!newName.equals(channel.getDisplayName())) {
                channel.setDisplayName(newName);
            }

            return newName;
        }
    }
}
