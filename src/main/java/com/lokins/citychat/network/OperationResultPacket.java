package com.lokins.citychat.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端向客户端发送操作结果反馈，显示在 action bar。
 */
public class OperationResultPacket {
    private final boolean success;
    private final String messageKey;

    public OperationResultPacket(boolean success, String messageKey) {
        this.success = success;
        this.messageKey = messageKey;
    }

    public static void encode(OperationResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.messageKey, 128);
    }

    public static OperationResultPacket decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        String messageKey = buf.readUtf(128);
        return new OperationResultPacket(success, messageKey);
    }

    public static void handle(OperationResultPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable(msg.messageKey), true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
