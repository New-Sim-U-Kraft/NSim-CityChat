package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.manager.ChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * C→S 群组查询：客户端发送群名/#群号，服务端搜索全部频道后返回结果。
 */
public class GroupQueryPacket {
    private final String query;

    public GroupQueryPacket(String query) {
        this.query = query == null ? "" : query;
    }

    public static void encode(GroupQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.query, 64);
    }

    public static GroupQueryPacket decode(FriendlyByteBuf buf) {
        return new GroupQueryPacket(buf.readUtf(64));
    }

    public static void handle(GroupQueryPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        if (sender == null) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            ChatChannel channel = ChatManager.getInstance()
                    .getChannelManager().findChannelByNameOrNumber(msg.query);

            // 通知频道不可被搜索到
            if (channel != null && channel.isNotificationChannel()) {
                channel = null;
            }

            GroupQueryResultPacket result;
            if (channel == null) {
                result = GroupQueryResultPacket.notFound(msg.query);
            } else {
                result = GroupQueryResultPacket.found(
                        msg.query,
                        channel.getDisplayName(),
                        channel.getGroupNumber(),
                        channel.getAccess()
                );
            }

            ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), result);
        });

        ctx.setPacketHandled(true);
    }
}
