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
 * 消息右键菜单 —— Discord 风格弹出菜单
 */
public class MessageContextMenu {
    private static final int ITEM_HEIGHT = 22;
    private static final int MENU_WIDTH = 120;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 6;

    private final int menuX, menuY;
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

        items.add(new MenuItem("引用", () -> {
            chatScreen.insertTextToInput("> " + message.getSenderName() + ": " + message.getContent() + "\n");
            close();
        }));

        items.add(new MenuItem("@提及", () -> {
            chatScreen.insertTextToInput("@" + message.getSenderName() + " ");
            close();
        }));

        UUID currentPlayerId = getCurrentPlayerId();
        if (currentPlayerId != null && message.getSenderId() != null) {
            String channelId = chatScreen.getCurrentChannelId();
            ChatChannel channel = channelId != null
                    ? chatScreen.getChatManager().getChannelManager().getChannel(channelId) : null;
            boolean isAuthor = currentPlayerId.equals(message.getSenderId());
            boolean isManager = channel != null && channel.canManage(currentPlayerId);
            if (isAuthor || isManager) {
                items.add(new MenuItem("撤回消息", () -> {
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

    public void render(GuiGraphics gg) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        int totalHeight = items.size() * ITEM_HEIGHT + PADDING_Y * 2;

        // 阴影
        UIStyles.drawShadow(gg, menuX, menuY, MENU_WIDTH, totalHeight);
        // 背景
        gg.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + totalHeight, UIStyles.BG_POPUP);
        // 微妙边框
        gg.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + 1, UIStyles.DIVIDER);
        gg.fill(menuX, menuY + totalHeight - 1, menuX + MENU_WIDTH, menuY + totalHeight, UIStyles.DIVIDER);
        gg.fill(menuX, menuY, menuX + 1, menuY + totalHeight, UIStyles.DIVIDER);
        gg.fill(menuX + MENU_WIDTH - 1, menuY, menuX + MENU_WIDTH, menuY + totalHeight, UIStyles.DIVIDER);

        int itemY = menuY + PADDING_Y;
        int mx = mc.mouseHandler.isLeftPressed() ? -1 : (int) mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
        int my = (int) mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

        for (MenuItem item : items) {
            boolean hovered = mx >= menuX + 4 && mx < menuX + MENU_WIDTH - 4
                    && my >= itemY && my < itemY + ITEM_HEIGHT;
            if (hovered) {
                gg.fill(menuX + 4, itemY, menuX + MENU_WIDTH - 4, itemY + ITEM_HEIGHT, UIStyles.ACCENT_MUTED);
            }
            gg.drawString(mc.font, item.label(),
                    menuX + PADDING_X, itemY + (ITEM_HEIGHT - 8) / 2,
                    hovered ? UIStyles.TEXT_WHITE : UIStyles.TEXT_SECONDARY);
            itemY += ITEM_HEIGHT;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int totalHeight = items.size() * ITEM_HEIGHT + PADDING_Y * 2;
        if (mouseX < menuX || mouseX > menuX + MENU_WIDTH || mouseY < menuY || mouseY > menuY + totalHeight) {
            close();
            return true;
        }

        int index = (int) ((mouseY - menuY - PADDING_Y) / ITEM_HEIGHT);
        if (index >= 0 && index < items.size()) {
            items.get(index).action().run();
            return true;
        }
        return false;
    }

    public void close() { this.visible = false; }
    public boolean isVisible() { return visible; }
}
