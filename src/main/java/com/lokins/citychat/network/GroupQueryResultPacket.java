package com.lokins.citychat.network;

import com.lokins.citychat.data.ChatChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C 群组查询结果，客户端据此更新 JoinGroupScreen 的 UI。
 * 通过静态回调通知当前打开的 JoinGroupScreen。
 */
public class GroupQueryResultPacket {
    private final boolean found;
    private final String query;
    private final String displayName;
    private final int groupNumber;
    private final String accessName;

    /** 客户端回调，JoinGroupScreen 在打开时注册，关闭时清除 */
    private static volatile Listener listener;

    public interface Listener {
        void onQueryResult(boolean found, String query, String displayName, int groupNumber, String accessName);
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    private GroupQueryResultPacket(boolean found, String query, String displayName, int groupNumber, String accessName) {
        this.found = found;
        this.query = query;
        this.displayName = displayName;
        this.groupNumber = groupNumber;
        this.accessName = accessName;
    }

    public static GroupQueryResultPacket found(String query, String displayName, int groupNumber, ChatChannel.GroupAccess access) {
        return new GroupQueryResultPacket(true, query, displayName, groupNumber, access.name());
    }

    public static GroupQueryResultPacket notFound(String query) {
        return new GroupQueryResultPacket(false, query, "", 0, "");
    }

    public static void encode(GroupQueryResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.found);
        buf.writeUtf(msg.query, 64);
        buf.writeUtf(msg.displayName, 64);
        buf.writeVarInt(msg.groupNumber);
        buf.writeUtf(msg.accessName, 32);
    }

    public static GroupQueryResultPacket decode(FriendlyByteBuf buf) {
        boolean found = buf.readBoolean();
        String query = buf.readUtf(64);
        String displayName = buf.readUtf(64);
        int groupNumber = buf.readVarInt();
        String accessName = buf.readUtf(32);
        return new GroupQueryResultPacket(found, query, displayName, groupNumber, accessName);
    }

    public static void handle(GroupQueryResultPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Listener l = listener;
            if (l != null) {
                l.onQueryResult(msg.found, msg.query, msg.displayName, msg.groupNumber, msg.accessName);
            }
        });
        ctx.setPacketHandled(true);
    }
}
