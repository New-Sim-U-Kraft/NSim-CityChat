package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.manager.ChatManager;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端发送消息到服务端，服务端验证身份后按成员广播。
 */
public class ChatMessagePacket {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String channelId;
    private final String content;

    public ChatMessagePacket(String channelId, String content) {
        this.channelId = channelId;
        this.content = content;
    }

    public static void encode(ChatMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.channelId, 128);
        buf.writeUtf(msg.content, 256);
    }

    public static ChatMessagePacket decode(FriendlyByteBuf buf) {
        String channelId = buf.readUtf(128);
        String content = buf.readUtf(256);
        return new ChatMessagePacket(channelId, content);
    }

    public static void handle(ChatMessagePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();

        if (ctx.getDirection().getReceptionSide().isServer()) {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                ctx.setPacketHandled(true);
                return;
            }

            ctx.enqueueWork(() -> {
                UUID senderId = sender.getUUID();
                String senderName = sender.getName().getString();

                ChatManager chatManager = ChatManager.getInstance();
                ChatChannel channel = chatManager.getChannelManager().getChannel(msg.channelId);

                if (channel == null) {
                    LOGGER.warn("Player {} tried to send message to non-existent channel {}", senderName, msg.channelId);
                    return;
                }

                if (!channel.isMember(senderId)) {
                    LOGGER.warn("Player {} tried to send message to channel {} without being a member", senderName, msg.channelId);
                    return;
                }

                ChatMessage chatMessage = new ChatMessage(senderId, senderName, msg.content, msg.channelId, false);
                chatManager.getChannelManager().addMessage(msg.channelId, chatMessage);

                ChatMessageBroadcastPacket broadcast = new ChatMessageBroadcastPacket(
                        msg.channelId,
                        senderId,
                        senderName,
                        msg.content,
                        chatMessage.getTimestamp(),
                        chatMessage.getMessageId()
                );

                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (channel.isMember(player.getUUID())) {
                            ChatNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    broadcast
                            );
                        }
                    }
                }
            });
        }

        ctx.setPacketHandled(true);
    }
}