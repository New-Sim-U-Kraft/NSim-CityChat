package com.lokins.citychat.client.gui;

import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.ChatMessagePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 聊天输入框组件
 */
public class ChatInputWidget extends AbstractChatWidget {
    private final EditBox inputBox;
    private static final int MAX_CHAT_LENGTH = 256;

    public ChatInputWidget(int x, int y, int width, int height, ChatScreen parent) {
        super(x, y, width, height, parent);

        Minecraft mc = Minecraft.getInstance();
        this.inputBox = new EditBox(mc.font, x + 5, y + 5, width - 10, height - 10,
                Component.literal("输入消息..."));
        this.inputBox.setMaxLength(MAX_CHAT_LENGTH);
        this.inputBox.setFocused(true);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, 0xFF2a2a2a);
        renderBorder(guiGraphics, 0xFF4a4a4a);
        inputBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        return inputBox.mouseClicked(mouseX, mouseY, button);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return inputBox.charTyped(codePoint, modifiers);
    }

    /**
     * 在输入框当前光标位置插入文本。
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String current = inputBox.getValue();
        int cursor = Math.min(Math.max(0, inputBox.getCursorPosition()), current.length());
        String newValue = current.substring(0, cursor) + text + current.substring(cursor);
        if (newValue.length() > MAX_CHAT_LENGTH) {
            newValue = newValue.substring(0, MAX_CHAT_LENGTH);
        }
        inputBox.setValue(newValue);
        inputBox.setCursorPosition(Math.min(cursor + text.length(), newValue.length()));
    }

    private void sendMessage(String content) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        String currentChannel = parent.getCurrentChannelId();
        if (currentChannel == null || currentChannel.isBlank()) {
            player.displayClientMessage(Component.literal("请先在左侧创建或选择群组"), true);
            return;
        }

        ChatNetwork.CHANNEL.sendToServer(new ChatMessagePacket(
                currentChannel,
                content
        ));
    }
}
