package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 聊天消息显示面板 - 支持动态布局、滚动条、时间戳、点击上下文菜单
 */
public class ChatPanelWidget extends AbstractChatWidget {
    private static final int HEADER_HEIGHT = 24;
    private static final int INFO_BUTTON_WIDTH = 42;
    private static final int MESSAGE_HEIGHT = 14;
    private static final int SCROLLBAR_WIDTH = 3;

    private int scrollOffset = 0;
    private MessageContextMenu contextMenu;

    /** 记录上一次渲染的消息位置，用于点击检测 */
    private final List<RenderedMessage> renderedMessages = new ArrayList<>();

    private record RenderedMessage(ChatMessage message, int yPos) {}

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    public ChatPanelWidget(int x, int y, int width, int height, ChatScreen parent) {
        super(x, y, width, height, parent);
    }

    private int getVisibleMessageCount() {
        int availableHeight = height - HEADER_HEIGHT - 6;
        return Math.max(1, availableHeight / MESSAGE_HEIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, 0xFF1a1a1a);
        renderBorder(guiGraphics, 0xFF4a4a4a);

        String currentChannel = parent.getCurrentChannelId();
        var channel = parent.getChatManager().getChannelManager().getChannel(currentChannel);

        renderHeader(guiGraphics, channel);

        if (channel == null) {
            guiGraphics.drawString(Minecraft.getInstance().font, "暂无群组，先在左侧创建或加入。", x + 8, y + HEADER_HEIGHT + 8, 0xFFBDBDBD);
            return;
        }

        List<ChatMessage> channelMessages = channel.getMessageHistory();
        int visibleCount = getVisibleMessageCount();

        // clamp scrollOffset
        int maxScroll = Math.max(0, channelMessages.size() - visibleCount);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(0, scrollOffset);

        int startIdx = Math.max(0, channelMessages.size() - visibleCount - scrollOffset);
        int endIdx = Math.min(channelMessages.size(), startIdx + visibleCount);

        renderedMessages.clear();
        int yOffset = y + HEADER_HEIGHT + 6;
        for (int i = startIdx; i < endIdx; i++) {
            ChatMessage msg = channelMessages.get(i);
            if (yOffset >= y + HEADER_HEIGHT && yOffset < y + height - 5) {
                renderMessage(guiGraphics, msg, yOffset);
                renderedMessages.add(new RenderedMessage(msg, yOffset));
            }
            yOffset += MESSAGE_HEIGHT;
        }

        // 渲染滚动条
        renderScrollbar(guiGraphics, channelMessages.size(), visibleCount);
    }

    private void renderHeader(GuiGraphics guiGraphics, Object channelObj) {
        guiGraphics.fill(x, y, x + width, y + HEADER_HEIGHT, 0xFF222833);
        guiGraphics.fill(x, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, 0xFF3E4A5A);

        if (channelObj instanceof com.lokins.citychat.data.ChatChannel channel) {
            String title = channel.getDisplayName() + "（" + channel.getMemberCount() + "）";
            guiGraphics.drawString(Minecraft.getInstance().font, title, x + 8, y + 8, 0xFFFFFFFF);
        } else {
            guiGraphics.drawString(Minecraft.getInstance().font, "未选择群组", x + 8, y + 8, 0xFFBDBDBD);
        }

        int btnX = x + width - INFO_BUTTON_WIDTH - 6;
        int btnY = y + 4;
        guiGraphics.fill(btnX, btnY, btnX + INFO_BUTTON_WIDTH, btnY + 16, 0xFF3A4658);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "信息", btnX + INFO_BUTTON_WIDTH / 2, btnY + 4, 0xFFFFFFFF);
    }

    private void renderMessage(GuiGraphics guiGraphics, ChatMessage message, int yPos) {
        Minecraft mc = Minecraft.getInstance();
        String senderName = message.getSenderName();
        String content = message.getContent();
        String timeStr = "[" + TIME_FORMAT.format(new Date(message.getTimestamp())) + "]";

        int textX = x + 5;
        // 时间戳 - 灰色
        guiGraphics.drawString(mc.font, timeStr, textX, yPos, 0xFF888888);
        textX += mc.font.width(timeStr) + 4;
        // 玩家名 - 蓝色
        String nameStr = senderName + ":";
        guiGraphics.drawString(mc.font, nameStr, textX, yPos, 0xFFaaaaff);
        textX += mc.font.width(nameStr) + 4;
        // 内容 - 白色
        guiGraphics.drawString(mc.font, content, textX, yPos, 0xFFffffff);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int totalMessages, int visibleCount) {
        if (totalMessages <= visibleCount) {
            return;
        }

        int trackX = x + width - SCROLLBAR_WIDTH - 1;
        int trackY = y + HEADER_HEIGHT;
        int trackHeight = height - HEADER_HEIGHT;

        // 轨道
        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, 0xFF333333);

        // 滑块
        float ratio = (float) visibleCount / totalMessages;
        int thumbHeight = Math.max(10, (int) (trackHeight * ratio));

        int maxScroll = totalMessages - visibleCount;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        // scrollOffset 越大越往上，滑块应该越靠上
        int thumbY = trackY + (int) ((trackHeight - thumbHeight) * (1f - scrollRatio));

        guiGraphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先检查上下文菜单
        if (contextMenu != null && contextMenu.isVisible()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (!isMouseOver((int) mouseX, (int) mouseY)) {
            return false;
        }

        // 左键点击信息按钮
        if (button == 0) {
            int btnX = x + width - INFO_BUTTON_WIDTH - 6;
            int btnY = y + 4;
            boolean clickInfo = mouseX >= btnX && mouseX <= btnX + INFO_BUTTON_WIDTH && mouseY >= btnY && mouseY <= btnY + 16;
            if (clickInfo) {
                parent.openGroupInfoForCurrentChannel();
                return true;
            }
        }

        // 右键点击消息 -> 弹出上下文菜单
        if (button == 1) {
            for (RenderedMessage rm : renderedMessages) {
                if (mouseY >= rm.yPos && mouseY < rm.yPos + MESSAGE_HEIGHT && mouseX >= x && mouseX < x + width - SCROLLBAR_WIDTH) {
                    contextMenu = new MessageContextMenu((int) mouseX, (int) mouseY, rm.message, parent);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (isMouseOver((int) pMouseX, (int) pMouseY)) {
            scrollOffset = Math.max(0, (int) (scrollOffset - pDelta));
            return true;
        }
        return false;
    }

    public void updateChannel(String channelId) {
        scrollOffset = 0;
        contextMenu = null;
    }

    public void closeContextMenu() {
        if (contextMenu != null) {
            contextMenu.close();
            contextMenu = null;
        }
    }

    /**
     * 由 ChatScreen 在所有组件渲染完毕后调用，保证菜单在最顶层。
     */
    public void renderContextMenu(GuiGraphics guiGraphics) {
        if (contextMenu != null && contextMenu.isVisible()) {
            contextMenu.render(guiGraphics);
        }
    }
}
