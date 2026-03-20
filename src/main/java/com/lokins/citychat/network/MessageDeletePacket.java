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
 * C→S 消息删除/撤回请求。
 * 服务端校验：发送者是消息作者 OR 频道管理员/群主。
 */
public class MessageDeletePacket {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String channelId;
    private final UUID messageId;

    public MessageDeletePacket(String channelId, UUID messageId) {
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public static void encode(MessageDeletePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.channelId, 128);
        buf.writeUUID(msg.messageId);
    }

    public static MessageDeletePacket decode(FriendlyByteBuf buf) {
        String channelId = buf.readUtf(128);
        UUID messageId = buf.readUUID();
        return new MessageDeletePacket(channelId, messageId);
    }

    public static void handle(MessageDeletePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        if (sender == null) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            ChatManager manager = ChatManager.getInstance();
            ChatChannel channel = manager.getChannelManager().getChannel(msg.channelId);
            if (channel == null) {
                ChatNetwork.sendOperationResult(sender, false, "cc.group.delete_failed");
                return;
            }

            UUID operatorId = sender.getUUID();
            ChatMessage targetMsg = channel.getMessage(msg.messageId);
            if (targetMsg == null) {
                ChatNetwork.sendOperationResult(sender, false, "cc.group.delete_failed");
                return;
            }

            boolean isAuthor = targetMsg.getSenderId().equals(operatorId);
            boolean isManager = channel.canManage(operatorId);
            if (!isAuthor && !isManager) {
                ChatNetwork.sendOperationResult(sender, false, "cc.group.delete_failed");
                return;
            }

            boolean removed = channel.removeMessage(msg.messageId);
            if (!removed) {
                ChatNetwork.sendOperationResult(sender, false, "cc.group.delete_failed");
                return;
            }

            ChatNetwork.sendOperationResult(sender, true, "cc.group.message_deleted");

            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                MessageDeleteBroadcastPacket broadcast = new MessageDeleteBroadcastPacket(msg.channelId, msg.messageId);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (channel.isMember(player.getUUID())) {
                        ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), broadcast);
                    }
                }
            }
        });

        ctx.setPacketHandled(true);
    }
}
