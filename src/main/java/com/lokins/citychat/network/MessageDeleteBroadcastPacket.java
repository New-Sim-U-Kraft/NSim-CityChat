package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.manager.ChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C 广播消息删除，客户端收到后从本地频道移除对应消息。
 */
public class MessageDeleteBroadcastPacket {
    private final String channelId;
    private final UUID messageId;

    public MessageDeleteBroadcastPacket(String channelId, UUID messageId) {
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public static void encode(MessageDeleteBroadcastPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.channelId, 128);
        buf.writeUUID(msg.messageId);
    }

    public static MessageDeleteBroadcastPacket decode(FriendlyByteBuf buf) {
        String channelId = buf.readUtf(128);
        UUID messageId = buf.readUUID();
        return new MessageDeleteBroadcastPacket(channelId, messageId);
    }

    public static void handle(MessageDeleteBroadcastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ChatChannel channel = ChatManager.getInstance().getChannelManager().getChannel(msg.channelId);
            if (channel != null) {
                channel.removeMessage(msg.messageId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
