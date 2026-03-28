package com.lokins.citychat.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息中的可点击按钮动作。
 *
 * @param label   按钮显示文本，如 "[接受]"
 * @param command 点击执行的命令（不带 /），如 "skofficial accept xxx"
 * @param color   按钮文字颜色，如 0x55FF55
 */
public record MessageAction(String label, String command, int color) {

    // ── 网络序列化 ──

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeUtf(label, 64);
        buf.writeUtf(command, 256);
        buf.writeInt(color);
    }

    public static MessageAction readFromBuf(FriendlyByteBuf buf) {
        return new MessageAction(buf.readUtf(64), buf.readUtf(256), buf.readInt());
    }

    public static void writeList(FriendlyByteBuf buf, List<MessageAction> actions) {
        buf.writeVarInt(actions.size());
        for (MessageAction action : actions) {
            action.writeToBuf(buf);
        }
    }

    public static List<MessageAction> readList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MessageAction> actions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            actions.add(readFromBuf(buf));
        }
        return actions;
    }
}
