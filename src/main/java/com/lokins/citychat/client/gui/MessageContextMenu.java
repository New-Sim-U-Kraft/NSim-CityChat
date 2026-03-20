package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 消息右键点击上下文菜单。
 * 菜单项：引用、@、撤回（权限可见）。
 */
public class MessageContextMenu {
    private static final int ITEM_HEIGHT = 16;
    private static final int MENU_WIDTH = 80;
    private static final int PADDING = 4;

    private final int menuX;
    private final int menuY;
    private final ChatMessage message;
    private final ChatScreen chatScreen;
    private final List<MenuItem> items = new ArrayList<>();
    private boolean visible = true;

    public record MenuItem(String label, Runnable action) {}

    public MessageContextMenu(int x, int y, ChatMessage message, ChatScreen chatScreen) {
        this.menuX = x;
        this.menuY = y;
        this.message = message;
        this.chatScreen = chatScreen;

        // 引用
        items.add(new MenuItem("引用", () -> {
            String quoteText = "> " + message.getSenderName() + ": " + message.getContent() + "\n";
            chatScreen.insertTextToInput(quoteText);
            close();
        }));

        // @
        items.add(new MenuItem("@", () -> {
            chatScreen.insertTextToInput("@" + message.getSenderName() + " ");
            close();
        }));

        // 撤回：群主/管理员/消息发送者可见
        UUID currentPlayerId = getCurrentPlayerId();
        if (currentPlayerId != null && message.getSenderId() != null) {
            String channelId = chatScreen.getCurrentChannelId();
            ChatChannel channel = channelId != null ? chatScreen.getChatManager().getChannelManager().getChannel(channelId) : null;
            boolean isAuthor = currentPlayerId.equals(message.getSenderId());
            boolean isManager = channel != null && channel.canManage(currentPlayerId);
            if (isAuthor || isManager) {
                items.add(new MenuItem("撤回", () -> {
                    ChatNetwork.requestDeleteMessage(message.getChannelId(), message.getMessageId());
                    close();
                }));
            }
        }
    }

    private UUID getCurrentPlayerId() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    public void render(GuiGraphics guiGraphics) {
        if (!visible) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int totalHeight = items.size() * ITEM_HEIGHT + PADDING * 2;

        // 背景
        guiGraphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + totalHeight, 0xEE333333);
        // 边框
        guiGraphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + 1, 0xFF555555);
        guiGraphics.fill(menuX, menuY + totalHeight - 1, menuX + MENU_WIDTH, menuY + totalHeight, 0xFF555555);
        guiGraphics.fill(menuX, menuY, menuX + 1, menuY + totalHeight, 0xFF555555);
        guiGraphics.fill(menuX + MENU_WIDTH - 1, menuY, menuX + MENU_WIDTH, menuY + totalHeight, 0xFF555555);

        int itemY = menuY + PADDING;
        for (MenuItem item : items) {
            guiGraphics.drawString(mc.font, item.label(), menuX + PADDING, itemY + 3, 0xFFFFFFFF);
            itemY += ITEM_HEIGHT;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) {
            return false;
        }

        int totalHeight = items.size() * ITEM_HEIGHT + PADDING * 2;

        // 点击菜单外部则关闭
        if (mouseX < menuX || mouseX > menuX + MENU_WIDTH || mouseY < menuY || mouseY > menuY + totalHeight) {
            close();
            return true;
        }

        // 点击了哪个菜单项
        int index = (int) ((mouseY - menuY - PADDING) / ITEM_HEIGHT);
        if (index >= 0 && index < items.size()) {
            items.get(index).action().run();
            return true;
        }

        return false;
    }

    public void close() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }
}
