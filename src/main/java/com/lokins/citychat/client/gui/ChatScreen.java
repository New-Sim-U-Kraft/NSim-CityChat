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
 * 现代化的聊天屏幕
 * 左侧为可折叠频道栏，右侧 75%-80% 为聊天区域。
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
        // 仅首次打开时请求同步；从子页面返回时不重复请求，
        // 服务端在任何操作成功后会主动广播快照。
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
        int chatHeight = this.height - OUTER_MARGIN * 2 - INPUT_HEIGHT - GUTTER;

        this.channelList = new ChannelListWidget(sidebarX, sidebarY, sidebarWidth, sidebarHeight, this);
        this.chatPanel = new ChatPanelWidget(chatX, chatY, chatWidth, chatHeight, this);
        this.inputWidget = new ChatInputWidget(chatX, chatY + chatHeight + GUTTER, chatWidth, INPUT_HEIGHT, this);

        ensureChannelSelection();
    }

    private void ensureChannelSelection() {
        if (currentChannelId != null && chatManager.getChannelManager().getChannel(currentChannelId) != null) {
            return;
        }

        var channels = chatManager.getChannelManager().getAllActiveChannels();
        currentChannelId = channels.isEmpty() ? null : channels.get(0).getChannelId();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (channelList != null) {
            channelList.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (chatPanel != null) {
            chatPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (inputWidget != null) {
            inputWidget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 上下文菜单最后渲染，保证在最顶层
        if (chatPanel != null) {
            chatPanel.renderContextMenu(guiGraphics);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chatPanel != null && chatPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (channelList != null && channelList.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (inputWidget != null && inputWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 点击其他区域关闭上下文菜单
        if (chatPanel != null) {
            chatPanel.closeContextMenu();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (chatPanel != null && chatPanel.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (channelList != null && channelList.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }

        if (inputWidget != null && inputWidget.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inputWidget != null && inputWidget.charTyped(codePoint, modifiers)) {
            return true;
        }
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

    public boolean isChannelListCollapsed() {
        return channelListCollapsed;
    }

    public String getCurrentChannelId() {
        return currentChannelId;
    }

    public void setCurrentChannelId(String channelId) {
        this.currentChannelId = channelId;
        if (chatPanel != null) {
            chatPanel.updateChannel(channelId);
        }
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public void openGroupInfoForCurrentChannel() {
        String channelId = getCurrentChannelId();
        if (channelId == null) {
            return;
        }
        var channel = chatManager.getChannelManager().getChannel(channelId);
        if (channel == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new GroupInfoScreen(this, channel));
    }

    /**
     * 桥接方法：向输入框插入文本（供 MessageContextMenu 调用）。
     */
    public void insertTextToInput(String text) {
        if (inputWidget != null) {
            inputWidget.insertText(text);
        }
    }
}
