package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.ChatMessagePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 聊天输入框 —— 通知频道时显示只读提示，普通频道可输入发送。
 */
public class ChatInputWidget {
    private static final int MAX_CHAT_LENGTH = 256;
    private static final int PADDING = 6;

    private final int x, y, width, height;
    private final ChatScreen parent;
    private final EditBox inputBox;

    public ChatInputWidget(int x, int y, int width, int height, ChatScreen parent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parent = parent;

        Minecraft mc = Minecraft.getInstance();
        this.inputBox = new EditBox(mc.font,
                x + PADDING + 2, y + (height - 16) / 2, width - PADDING * 2 - 4, 16,
                Component.literal("输入消息..."));
        this.inputBox.setMaxLength(MAX_CHAT_LENGTH);
        this.inputBox.setBordered(false);
        this.inputBox.setFocused(true);
    }

    private boolean isNotificationChannel() {
        String channelId = parent.getCurrentChannelId();
        if (channelId == null) return false;
        var channel = parent.getChatManager().getChannelManager().getChannel(channelId);
        return channel != null && channel.isNotificationChannel();
    }

    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        gg.fill(x, y, x + width, y + height, UIStyles.BG_SURFACE);
        gg.fill(x, y, x + width, y + 1, UIStyles.DIVIDER_SUBTLE);

        if (isNotificationChannel()) {
            // 只读模式：显示公告栏提示
            Minecraft mc = Minecraft.getInstance();
            gg.drawString(mc.font, "📢 此频道为通知公告，不可发送消息",
                    x + PADDING + 2, y + (height - 8) / 2, UIStyles.TEXT_MUTED);
        } else {
            inputBox.render(gg, mouseX, mouseY, pt);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isNotificationChannel()) return false;
        if (keyCode == 257 || keyCode == 335) {
            String message = inputBox.getValue().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                inputBox.setValue("");
            }
            return true;
        }
        return inputBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isNotificationChannel()) return false;
        return inputBox.mouseClicked(mouseX, mouseY, button);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (isNotificationChannel()) return false;
        return inputBox.charTyped(codePoint, modifiers);
    }

    public void insertText(String text) {
        if (isNotificationChannel()) return;
        if (text == null || text.isEmpty()) return;
        String current = inputBox.getValue();
        int cursor = Math.min(Math.max(0, inputBox.getCursorPosition()), current.length());
        String newValue = current.substring(0, cursor) + text + current.substring(cursor);
        if (newValue.length() > MAX_CHAT_LENGTH) newValue = newValue.substring(0, MAX_CHAT_LENGTH);
        inputBox.setValue(newValue);
        inputBox.setCursorPosition(Math.min(cursor + text.length(), newValue.length()));
    }

    private void sendMessage(String content) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        String currentChannel = parent.getCurrentChannelId();
        if (currentChannel == null || currentChannel.isBlank()) {
            player.displayClientMessage(Component.literal("请先在左侧创建或选择群组"), true);
            return;
        }
        ChatNetwork.CHANNEL.sendToServer(new ChatMessagePacket(currentChannel, content));
    }
}
