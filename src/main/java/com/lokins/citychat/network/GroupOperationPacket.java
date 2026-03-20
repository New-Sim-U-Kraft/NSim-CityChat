package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
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
import java.util.regex.Pattern;

/**
 * 客户端发起的群组管理操作，请求服务端权威执行并同步快照。
 */
public class GroupOperationPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 服务端群组名称校验：1-24字符，中文/英文/数字/空格/_/- */
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9 _\\-]{1,24}$");

    private final OperationType type;
    private final String channelId;
    private final String displayName;
    private final String query;
    private final String password;
    private final String accessName;
    private final UUID targetId;
    private final boolean admin;

    private GroupOperationPacket(OperationType type, String channelId, String displayName,
                                 String query, String password, String accessName,
                                 UUID targetId, boolean admin) {
        this.type = type;
        this.channelId = channelId == null ? "" : channelId;
        this.displayName = displayName == null ? "" : displayName;
        this.query = query == null ? "" : query;
        this.password = password == null ? "" : password;
        this.accessName = accessName == null ? "" : accessName;
        this.targetId = targetId;
        this.admin = admin;
    }

    public static GroupOperationPacket create(String displayName, ChatChannel.GroupAccess access, String password) {
        String accessName = access == null ? ChatChannel.GroupAccess.NORMAL.name() : access.name();
        return new GroupOperationPacket(OperationType.CREATE, "", displayName, "", password, accessName, null, false);
    }

    public static GroupOperationPacket join(String query, String password) {
        return new GroupOperationPacket(OperationType.JOIN, "", "", query, password, "", null, false);
    }

    public static GroupOperationPacket leave(String channelId) {
        return new GroupOperationPacket(OperationType.LEAVE, channelId, "", "", "", "", null, false);
    }

    public static GroupOperationPacket setAdmin(String channelId, UUID targetId, boolean admin) {
        return new GroupOperationPacket(OperationType.SET_ADMIN, channelId, "", "", "", "", targetId, admin);
    }

    public static GroupOperationPacket changeAccess(String channelId, ChatChannel.GroupAccess access, String password) {
        String accessName = access == null ? "" : access.name();
        return new GroupOperationPacket(OperationType.CHANGE_ACCESS, channelId, "", "", password, accessName, null, false);
    }

    public static GroupOperationPacket changePassword(String channelId, String password) {
        return new GroupOperationPacket(OperationType.CHANGE_PASSWORD, channelId, "", "", password, "", null, false);
    }

    public static GroupOperationPacket dissolve(String channelId) {
        return new GroupOperationPacket(OperationType.DISSOLVE, channelId, "", "", "", "", null, false);
    }

    public static GroupOperationPacket kick(String channelId, UUID targetId) {
        return new GroupOperationPacket(OperationType.KICK, channelId, "", "", "", "", targetId, false);
    }

    public static GroupOperationPacket transferOwner(String channelId, UUID targetId) {
        return new GroupOperationPacket(OperationType.TRANSFER_OWNER, channelId, "", "", "", "", targetId, false);
    }

    public static GroupOperationPacket changeNumber(String channelId, int newNumber) {
        return new GroupOperationPacket(OperationType.CHANGE_NUMBER, channelId, "", String.valueOf(newNumber), "", "", null, false);
    }

    public static void encode(GroupOperationPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeUtf(msg.channelId, 128);
        buf.writeUtf(msg.displayName, 64);
        buf.writeUtf(msg.query, 64);
        buf.writeUtf(msg.password, 64);
        buf.writeUtf(msg.accessName, 32);
        buf.writeBoolean(msg.targetId != null);
        if (msg.targetId != null) {
            buf.writeUUID(msg.targetId);
        }
        buf.writeBoolean(msg.admin);
    }

    public static GroupOperationPacket decode(FriendlyByteBuf buf) {
        OperationType type = buf.readEnum(OperationType.class);
        String channelId = buf.readUtf(128);
        String displayName = buf.readUtf(64);
        String query = buf.readUtf(64);
        String password = buf.readUtf(64);
        String accessName = buf.readUtf(32);
        UUID targetId = buf.readBoolean() ? buf.readUUID() : null;
        boolean admin = buf.readBoolean();
        return new GroupOperationPacket(type, channelId, displayName, query, password, accessName, targetId, admin);
    }

    public static void handle(GroupOperationPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        if (sender == null) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            ChatManager manager = ChatManager.getInstance();
            UUID operatorId = sender.getUUID();
            String operatorName = sender.getName().getString();

            boolean success;
            String successKey;
            String failKey;

            switch (msg.type) {
                case CREATE -> {
                    success = handleCreate(manager, msg, operatorId, operatorName);
                    successKey = "cc.group.created";
                    failKey = "cc.group.create_failed";
                }
                case JOIN -> {
                    success = manager.getChannelManager().joinByNameOrNumber(msg.query, msg.password, operatorId, operatorName);
                    successKey = "cc.group.joined";
                    failKey = "cc.group.join_failed";
                }
                case LEAVE -> {
                    success = manager.getChannelManager().leaveChannel(msg.channelId, operatorId, operatorName);
                    successKey = "cc.group.left";
                    failKey = "cc.group.left_failed";
                }
                case SET_ADMIN -> {
                    success = msg.targetId != null && manager.getChannelManager().setAdmin(msg.channelId, operatorId, msg.targetId, msg.admin);
                    successKey = msg.admin ? "cc.group.admin_set" : "cc.group.admin_removed";
                    failKey = "cc.group.admin_failed";
                }
                case CHANGE_ACCESS -> {
                    success = handleChangeAccess(manager, msg, operatorId);
                    successKey = "cc.group.access_changed";
                    failKey = "cc.group.access_failed";
                }
                case CHANGE_PASSWORD -> {
                    success = manager.getChannelManager().changeGroupPassword(msg.channelId, operatorId, msg.password);
                    successKey = "cc.group.password_changed";
                    failKey = "cc.group.password_failed";
                }
                case DISSOLVE -> {
                    success = manager.getChannelManager().dissolveGroup(msg.channelId, operatorId);
                    successKey = "cc.group.dissolved";
                    failKey = "cc.group.dissolve_failed";
                }
                case KICK -> {
                    success = msg.targetId != null && manager.getChannelManager().kickMember(msg.channelId, operatorId, msg.targetId);
                    successKey = "cc.group.kicked";
                    failKey = "cc.group.kick_failed";
                }
                case TRANSFER_OWNER -> {
                    success = msg.targetId != null && manager.getChannelManager().transferOwnership(msg.channelId, operatorId, msg.targetId);
                    successKey = "cc.group.transferred";
                    failKey = "cc.group.transfer_failed";
                }
                case CHANGE_NUMBER -> {
                    int newNumber = 0;
                    try { newNumber = Integer.parseInt(msg.query); } catch (Exception ignored) {}
                    success = newNumber > 0 && manager.getChannelManager().changeGroupNumber(msg.channelId, operatorId, newNumber);
                    successKey = "cc.group.number_changed";
                    failKey = "cc.group.number_failed";
                }
                default -> {
                    success = false;
                    successKey = "";
                    failKey = "";
                }
            }

            ChatNetwork.sendOperationResult(sender, success, success ? successKey : failKey);

            if (success) {
                broadcastPerPlayerSnapshots();
            }
        });

        ctx.setPacketHandled(true);
    }

    /**
     * 给所有在线玩家各自发送其可见频道的快照。
     */
    public static void broadcastPerPlayerSnapshots() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChannelSnapshotPacket snapshot = ChannelSnapshotPacket.forPlayer(player.getUUID());
            ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
        }
    }

    private static boolean handleCreate(ChatManager manager, GroupOperationPacket msg, UUID operatorId, String operatorName) {
        String rawName = msg.displayName == null ? "" : msg.displayName.trim();
        if (rawName.isEmpty()) {
            return false;
        }

        // 服务端校验名称格式
        if (!DISPLAY_NAME_PATTERN.matcher(rawName).matches()) {
            LOGGER.warn("Player {} tried to create group with invalid name: {}", operatorName, rawName);
            return false;
        }

        ChatChannel.GroupAccess access = parseAccess(msg.accessName);
        String password = msg.password == null ? "" : msg.password.trim();
        if (access == ChatChannel.GroupAccess.ENCRYPTED && password.length() < 4) {
            return false;
        }

        String safeId = rawName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (safeId.isBlank()) {
            safeId = "group";
        }
        String channelId = "group_" + safeId + "_" + (System.currentTimeMillis() % 100000);

        ChatChannel channel = manager.getChannelManager().createChannel(
                channelId,
                rawName,
                "",
                ChatChannel.ChannelType.GROUP,
                access,
                access == ChatChannel.GroupAccess.ENCRYPTED ? password : "",
                operatorId
        );

        return channel != null && manager.getChannelManager().joinChannel(
                channel.getChannelId(),
                password,
                operatorId,
                operatorName
        );
    }

    private static boolean handleChangeAccess(ChatManager manager, GroupOperationPacket msg, UUID operatorId) {
        ChatChannel.GroupAccess access = parseAccess(msg.accessName);
        String password = msg.password == null ? "" : msg.password.trim();
        if (access == ChatChannel.GroupAccess.ENCRYPTED && password.length() < 4) {
            return false;
        }

        boolean changed = manager.getChannelManager().changeGroupAccess(msg.channelId, operatorId, access);
        if (!changed) {
            return false;
        }

        if (access == ChatChannel.GroupAccess.ENCRYPTED) {
            return manager.getChannelManager().changeGroupPassword(msg.channelId, operatorId, password);
        }
        return true;
    }

    private static ChatChannel.GroupAccess parseAccess(String raw) {
        try {
            return ChatChannel.GroupAccess.valueOf(raw);
        } catch (Exception ignored) {
            return ChatChannel.GroupAccess.NORMAL;
        }
    }

    private enum OperationType {
        CREATE,
        JOIN,
        LEAVE,
        SET_ADMIN,
        CHANGE_ACCESS,
        CHANGE_PASSWORD,
        DISSOLVE,
        KICK,
        TRANSFER_OWNER,
        CHANGE_NUMBER
    }
}
