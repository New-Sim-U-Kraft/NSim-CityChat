package com.lokins.citychat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端请求服务端发送群组快照（仅返回该玩家可见的频道，不含密码）。
 */
public class ChannelSyncRequestPacket {

    public static void encode(ChannelSyncRequestPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static ChannelSyncRequestPacket decode(FriendlyByteBuf buf) {
        return new ChannelSyncRequestPacket();
    }

    public static void handle(ChannelSyncRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ChannelSnapshotPacket snapshot = ChannelSnapshotPacket.forPlayer(sender.getUUID());
                ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), snapshot);
            }
        });
        ctx.setPacketHandled(true);
    }
}
