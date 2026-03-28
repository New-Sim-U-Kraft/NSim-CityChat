package com.lokins.citychat.network;

import com.lokins.citychat.client.NotificationManager;
import com.lokins.citychat.client.gui.ChatScreen;
import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.data.MessageAction;
import com.lokins.citychat.manager.ChatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端广播消息给所有客户端。
 */
public class ChatMessageBroadcastPacket {
    private final String channelId;
    private final UUID senderId;
    private final String senderName;
    private final String content;
    private final long timestamp;
    private final UUID messageId;
    private final List<MessageAction> actions;

    public ChatMessageBroadcastPacket() {
        this("", null, "", "", 0L, null, Collections.emptyList());
    }

    public ChatMessageBroadcastPacket(String channelId, UUID senderId, String senderName, String content) {
        this(channelId, senderId, senderName, content, System.currentTimeMillis(), UUID.randomUUID(), Collections.emptyList());
    }

    public ChatMessageBroadcastPacket(String channelId, UUID senderId, String senderName,
                                       String content, long timestamp, UUID messageId) {
        this(channelId, senderId, senderName, content, timestamp, messageId, Collections.emptyList());
    }

    public ChatMessageBroadcastPacket(String channelId, UUID senderId, String senderName,
                                       String content, long timestamp, UUID messageId,
                                       List<MessageAction> actions) {
        this.channelId = channelId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.actions = actions == null ? Collections.emptyList() : actions;
    }

    public static void encode(ChatMessageBroadcastPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.channelId, 128);
        buf.writeUUID(msg.senderId);
        buf.writeUtf(msg.senderName, 32);
        buf.writeUtf(msg.content, 256);
        buf.writeLong(msg.timestamp);
        buf.writeUUID(msg.messageId);
        MessageAction.writeList(buf, msg.actions);
    }

    public static ChatMessageBroadcastPacket decode(FriendlyByteBuf buf) {
        String channelId = buf.readUtf(128);
        UUID senderId = buf.readUUID();
        String senderName = buf.readUtf(32);
        String content = buf.readUtf(256);
        long timestamp = buf.readLong();
        UUID messageId = buf.readUUID();
        List<MessageAction> actions = MessageAction.readList(buf);
        return new ChatMessageBroadcastPacket(channelId, senderId, senderName, content, timestamp, messageId, actions);
    }

    public static void handle(ChatMessageBroadcastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            com.mojang.logging.LogUtils.getLogger().info("[CC客户端] 收到 ChatMessageBroadcast: 频道={}, 发送者={}, 内容={}, actions={}",
                    msg.channelId, msg.senderName, msg.content, msg.actions.size());

            ChatManager chatManager = ChatManager.getInstance();
            ChatChannel targetChannel = chatManager.getChannelManager().getChannel(msg.channelId);

            if (targetChannel == null) {
                com.mojang.logging.LogUtils.getLogger().warn("[CC客户端] 频道 {} 在客户端不存在! 消息将被丢弃（可能快照还没到）", msg.channelId);
            }

            ChatMessage chatMessage = new ChatMessage(
                    msg.senderId,
                    msg.senderName,
                    msg.content,
                    msg.channelId,
                    false,
                    msg.timestamp,
                    msg.messageId,
                    msg.actions
            );
            chatManager.getChannelManager().addMessage(msg.channelId, chatMessage);

            // 通知逻辑
            boolean shouldNotify = true;
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null && msg.senderId.equals(mc.player.getUUID())) {
                shouldNotify = false;
            }

            if (shouldNotify && mc.screen instanceof ChatScreen chatScreen) {
                if (msg.channelId.equals(chatScreen.getCurrentChannelId())) {
                    shouldNotify = false;
                }
            }

            if (shouldNotify) {
                String channelName = targetChannel != null ? targetChannel.getDisplayName() : msg.channelId;
                com.mojang.logging.LogUtils.getLogger().info("[CC客户端] 触发右上角通知: 频道={}, 发送者={}", channelName, msg.senderName);
                NotificationManager.getInstance().addNotification(channelName, msg.senderName, msg.content, msg.actions);
            }
        });
        ctx.setPacketHandled(true);
    }
}
