package com.lokins.citychat.network;

import com.lokins.citychat.client.NotificationManager;
import com.lokins.citychat.data.MessageAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C 通知推送包。
 * 当 simukraft 通知到达时，服务端推送到客户端触发右上角弹窗。
 */
public class NotificationPushPacket {
    private final String title;
    private final String content;
    private final String category;
    private final String senderName;
    private final List<MessageAction> actions;

    public NotificationPushPacket(String title, String content, String category, String senderName) {
        this(title, content, category, senderName, Collections.emptyList());
    }

    public NotificationPushPacket(String title, String content, String category, String senderName,
                                   List<MessageAction> actions) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.senderName = senderName;
        this.actions = actions == null ? Collections.emptyList() : actions;
    }

    public static void encode(NotificationPushPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, 128);
        buf.writeUtf(msg.content, 512);
        buf.writeUtf(msg.category, 32);
        buf.writeUtf(msg.senderName, 64);
        MessageAction.writeList(buf, msg.actions);
    }

    public static NotificationPushPacket decode(FriendlyByteBuf buf) {
        String title = buf.readUtf(128);
        String content = buf.readUtf(512);
        String category = buf.readUtf(32);
        String senderName = buf.readUtf(64);
        List<MessageAction> actions = MessageAction.readList(buf);
        return new NotificationPushPacket(title, content, category, senderName, actions);
    }

    public static void handle(NotificationPushPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            String channelName = "[" + msg.category + "]";
            com.mojang.logging.LogUtils.getLogger().info("[CC客户端] 收到 NotificationPush: 分类={}, 发送者={}, 标题={}, 内容={}, actions={}",
                    msg.category, msg.senderName, msg.title, msg.content, msg.actions.size());
            NotificationManager.getInstance().addNotification(channelName, msg.senderName, msg.content, msg.actions);
        });
        ctx.setPacketHandled(true);
    }
}
