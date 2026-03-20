package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.manager.ChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

            // 恢复消息历史
            for (MessageEntry msgEntry : entry.messages) {
                ChatMessage chatMessage = new ChatMessage(
                        msgEntry.senderId,
                        msgEntry.senderName,
                        msgEntry.content,
                        entry.channelId,
                        false,
                        msgEntry.timestamp,
                        msgEntry.messageId
                );
                channel.addMessage(chatMessage);
            }

            rebuilt.add(channel);
        }

        ChatManager.getInstance().getChannelManager().replaceFromSnapshot(rebuilt);
        ctx.setPacketHandled(true);
    }

    static class MessageEntry {
        UUID senderId;
        String senderName;
        String content;
        long timestamp;
        UUID messageId;

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(senderId);
            buf.writeUtf(senderName, 32);
            buf.writeUtf(content, 256);
            buf.writeLong(timestamp);
            buf.writeUUID(messageId);
        }

        static MessageEntry decode(FriendlyByteBuf buf) {
            MessageEntry entry = new MessageEntry();
            entry.senderId = buf.readUUID();
            entry.senderName = buf.readUtf(32);
            entry.content = buf.readUtf(256);
            entry.timestamp = buf.readLong();
            entry.messageId = buf.readUUID();
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

        static ChannelEntry fromChannel(ChatChannel channel) {
            ChannelEntry entry = new ChannelEntry();
            entry.channelId = channel.getChannelId();
            entry.groupNumber = channel.getGroupNumber();
            entry.displayName = channel.getDisplayName();
            entry.description = channel.getDescription();
            entry.type = channel.getType();
            entry.access = channel.getAccess();
            entry.ownerId = channel.getOwnerId();
            entry.members = new ArrayList<>(channel.getMembers());
            entry.admins = new ArrayList<>(channel.getAdmins());

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

            // 反序列化消息历史
            int msgSize = buf.readVarInt();
            entry.messages = new ArrayList<>(msgSize);
            for (int i = 0; i < msgSize; i++) {
                entry.messages.add(MessageEntry.decode(buf));
            }

            return entry;
        }
    }
}
