package com.lokins.citychat.client.gui;

import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.network.ChatNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * 现代化的聊天屏幕 —— 使用 LDLib 纹理体系渲染
 */
public class ChatScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int INPUT_HEIGHT = 42;
    private static final int COLLAPSED_SIDEBAR_WIDTH = 42;
    private static final int EXPANDED_SIDEBAR_MAX_WIDTH = 240;
    private static final int OUTER_MARGIN = 10;
    private static final int GUTTER = 8;

    private ChatInputWidget inputWidget;
    private ChatPanelWidget chatPanel;
    private ChannelListWidget channelList;

    private boolean channelListCollapsed = false;
    private String currentChannelId;
    private final ChatManager chatManager;
    private boolean initialSyncDone = false;

    public ChatScreen() {
        super(Component.literal("CityChat - 城市聊天"));
        this.chatManager = ChatManager.getInstance();
        this.currentChannelId = null;
    }

    @Override
    protected void init() {
        super.init();
        if (!initialSyncDone) {
            initialSyncDone = true;
            ChatNetwork.requestChannelSync();
        }
        relayout();
        LOGGER.info("ChatScreen initialized: {}x{}", this.width, this.height);
    }

    private void relayout() {
        int availableWidth = this.width - OUTER_MARGIN * 2;
        int sidebarWidth = channelListCollapsed
                ? COLLAPSED_SIDEBAR_WIDTH
                : Math.min(EXPANDED_SIDEBAR_MAX_WIDTH, (int) (availableWidth * 0.22f));

        int minChatWidth = (int) (availableWidth * 0.75f);
        if (!channelListCollapsed) {
            int maybeChatWidth = availableWidth - sidebarWidth - GUTTER;
            if (maybeChatWidth < minChatWidth) {
                sidebarWidth = Math.max(160, availableWidth - minChatWidth - GUTTER);
            }
        }

        int sidebarX = OUTER_MARGIN;
        int sidebarY = OUTER_MARGIN;
        int sidebarHeight = this.height - OUTER_MARGIN * 2;

        int chatX = sidebarX + sidebarWidth + GUTTER;
        int chatWidth = this.width - chatX - OUTER_MARGIN;
        int chatY = OUTER_MARGIN;

        // 通知频道：不显示输入栏，消息区占满
        boolean showInput = !isCurrentChannelNotification();
        int chatHeight = showInput
                ? this.height - OUTER_MARGIN * 2 - INPUT_HEIGHT - GUTTER
                : this.height - OUTER_MARGIN * 2;

        this.channelList = new ChannelListWidget(sidebarX, sidebarY, sidebarWidth, sidebarHeight, this);
        this.chatPanel = new ChatPanelWidget(chatX, chatY, chatWidth, chatHeight, this);
        this.inputWidget = showInput
                ? new ChatInputWidget(chatX, chatY + chatHeight + GUTTER, chatWidth, INPUT_HEIGHT, this)
                : null;

        ensureChannelSelection();
    }

    private boolean isCurrentChannelNotification() {
        if (currentChannelId == null) return false;
        var channel = chatManager.getChannelManager().getChannel(currentChannelId);
        return channel != null && channel.isNotificationChannel();
    }

    private void ensureChannelSelection() {
        if (currentChannelId != null && chatManager.getChannelManager().getChannel(currentChannelId) != null) return;
        var channels = chatManager.getChannelManager().getAllActiveChannels();
        currentChannelId = channels.isEmpty() ? null : channels.get(0).getChannelId();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg);

        if (channelList != null) channelList.render(gg, mouseX, mouseY, pt);
        if (chatPanel != null) chatPanel.render(gg, mouseX, mouseY, pt);
        if (inputWidget != null) inputWidget.render(gg, mouseX, mouseY, pt);

        super.render(gg, mouseX, mouseY, pt);

        if (chatPanel != null) chatPanel.renderContextMenu(gg);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chatPanel != null && chatPanel.mouseClicked(mouseX, mouseY, button)) return true;
        if (channelList != null && channelList.mouseClicked(mouseX, mouseY, button)) return true;
        if (inputWidget != null && inputWidget.mouseClicked(mouseX, mouseY, button)) return true;
        if (chatPanel != null) chatPanel.closeContextMenu();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (chatPanel != null && chatPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (channelList != null && channelList.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.onClose(); return true; }
        if (inputWidget != null && inputWidget.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inputWidget != null && inputWidget.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    public void toggleChannelList() {
        this.channelListCollapsed = !this.channelListCollapsed;
        relayout();
    }

    @Override
    public void onClose() {
        super.onClose();
        LOGGER.info("ChatScreen closed");
    }

    public boolean isChannelListCollapsed() { return channelListCollapsed; }
    public String getCurrentChannelId() { return currentChannelId; }

    public void setCurrentChannelId(String channelId) {
        boolean wasNotify = isCurrentChannelNotification();
        this.currentChannelId = channelId;
        boolean isNotify = isCurrentChannelNotification();
        // 输入栏显隐变化时重新布局
        if (wasNotify != isNotify) {
            relayout();
        }
        if (chatPanel != null) chatPanel.updateChannel(channelId);
    }

    public ChatManager getChatManager() { return chatManager; }

    public void openGroupInfoForCurrentChannel() {
        String channelId = getCurrentChannelId();
        if (channelId == null) return;
        var channel = chatManager.getChannelManager().getChannel(channelId);
        if (channel == null) return;
        Minecraft.getInstance().setScreen(new GroupInfoScreen(this, channel));
    }

    public void insertTextToInput(String text) {
        if (inputWidget != null) inputWidget.insertText(text);
    }
}
