package com.lokins.citychat.network;

import com.lokins.citychat.CityChatMod;
import com.lokins.citychat.data.ChatChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * CityChat 网络通道与消息注册。
 * <p>
 * C→S 包使用 consumer + 内部 enqueueWork（标准 Forge 模式，确保服务端主线程执行）。
 * S→C 包使用 consumerMainThread（确保客户端主线程执行 UI 更新）。
 */
public class ChatNetwork {
    private static final String PROTOCOL_VERSION = "3";
    private static boolean registered = false;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CityChatMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // === C→S 包：consumer + handler 内部 enqueueWork ===

        CHANNEL.messageBuilder(ChannelSyncRequestPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChannelSyncRequestPacket::encode)
                .decoder(ChannelSyncRequestPacket::decode)
                .consumerNetworkThread(ChannelSyncRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(GroupOperationPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroupOperationPacket::encode)
                .decoder(GroupOperationPacket::decode)
                .consumerNetworkThread(GroupOperationPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChatMessagePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChatMessagePacket::encode)
                .decoder(ChatMessagePacket::decode)
                .consumerNetworkThread(ChatMessagePacket::handle)
                .add();

        CHANNEL.messageBuilder(MessageDeletePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MessageDeletePacket::encode)
                .decoder(MessageDeletePacket::decode)
                .consumerNetworkThread(MessageDeletePacket::handle)
                .add();

        CHANNEL.messageBuilder(GroupQueryPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroupQueryPacket::encode)
                .decoder(GroupQueryPacket::decode)
                .consumerNetworkThread(GroupQueryPacket::handle)
                .add();

        // === S→C 包：consumerMainThread（客户端主线程） ===

        CHANNEL.messageBuilder(ChannelSnapshotPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChannelSnapshotPacket::encode)
                .decoder(ChannelSnapshotPacket::decode)
                .consumerMainThread(ChannelSnapshotPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChatMessageBroadcastPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChatMessageBroadcastPacket::encode)
                .decoder(ChatMessageBroadcastPacket::decode)
                .consumerMainThread(ChatMessageBroadcastPacket::handle)
                .add();

        CHANNEL.messageBuilder(OperationResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OperationResultPacket::encode)
                .decoder(OperationResultPacket::decode)
                .consumerMainThread(OperationResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(MessageDeleteBroadcastPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MessageDeleteBroadcastPacket::encode)
                .decoder(MessageDeleteBroadcastPacket::decode)
                .consumerMainThread(MessageDeleteBroadcastPacket::handle)
                .add();

        CHANNEL.messageBuilder(GroupQueryResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GroupQueryResultPacket::encode)
                .decoder(GroupQueryResultPacket::decode)
                .consumerMainThread(GroupQueryResultPacket::handle)
                .add();
    }

    // ========== 辅助方法 ==========

    public static void sendOperationResult(ServerPlayer player, boolean success, String messageKey) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OperationResultPacket(success, messageKey));
    }

    public static void requestChannelSync() {
        CHANNEL.sendToServer(new ChannelSyncRequestPacket());
    }

    public static void requestCreateGroup(String displayName, ChatChannel.GroupAccess access, String password) {
        CHANNEL.sendToServer(GroupOperationPacket.create(displayName, access, password));
    }

    public static void requestJoinGroup(String query, String password) {
        CHANNEL.sendToServer(GroupOperationPacket.join(query, password));
    }

    public static void requestLeaveGroup(String channelId) {
        CHANNEL.sendToServer(GroupOperationPacket.leave(channelId));
    }

    public static void requestSetAdmin(String channelId, UUID targetId, boolean admin) {
        CHANNEL.sendToServer(GroupOperationPacket.setAdmin(channelId, targetId, admin));
    }

    public static void requestChangeAccess(String channelId, ChatChannel.GroupAccess access, String password) {
        CHANNEL.sendToServer(GroupOperationPacket.changeAccess(channelId, access, password));
    }

    public static void requestChangePassword(String channelId, String password) {
        CHANNEL.sendToServer(GroupOperationPacket.changePassword(channelId, password));
    }

    public static void requestDissolveGroup(String channelId) {
        CHANNEL.sendToServer(GroupOperationPacket.dissolve(channelId));
    }

    public static void requestKickMember(String channelId, UUID targetId) {
        CHANNEL.sendToServer(GroupOperationPacket.kick(channelId, targetId));
    }

    public static void requestTransferOwnership(String channelId, UUID targetId) {
        CHANNEL.sendToServer(GroupOperationPacket.transferOwner(channelId, targetId));
    }

    public static void requestDeleteMessage(String channelId, UUID messageId) {
        CHANNEL.sendToServer(new MessageDeletePacket(channelId, messageId));
    }

    public static void requestGroupQuery(String query) {
        CHANNEL.sendToServer(new GroupQueryPacket(query));
    }

    public static void requestChangeGroupNumber(String channelId, int newNumber) {
        CHANNEL.sendToServer(GroupOperationPacket.changeNumber(channelId, newNumber));
    }
}
