package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 聊天消息面板 —— Discord 风格
 */
public class ChatPanelWidget {
    private static final int HEADER_HEIGHT = 28;
    private static final int MESSAGE_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int PADDING = 8;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    private final int x, y, width, height;
    private final ChatScreen parent;

    private int scrollOffset = 0;
    private MessageContextMenu contextMenu;
    private final List<RenderedMessage> renderedMessages = new ArrayList<>();
    private final CCButton infoButton;

    private record RenderedMessage(ChatMessage message, int yPos) {}

    public ChatPanelWidget(int x, int y, int width, int height, ChatScreen parent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parent = parent;

        this.infoButton = new CCButton(x + width - 50, y + 4, 42, 20, "信息",
                b -> parent.openGroupInfoForCurrentChannel());
    }

    private int getVisibleMessageCount() {
        return Math.max(1, (height - HEADER_HEIGHT - PADDING * 2) / MESSAGE_HEIGHT);
    }

    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        // 主内容区背景
        gg.fill(x, y, x + width, y + height, UIStyles.BG_PRIMARY);

        String currentChannel = parent.getCurrentChannelId();
        var channel = parent.getChatManager().getChannelManager().getChannel(currentChannel);

        renderHeader(gg, channel, mouseX, mouseY, pt);

        if (channel == null) {
            gg.drawString(Minecraft.getInstance().font,
                    "暂无群组，先在左侧创建或加入。",
                    x + PADDING, y + HEADER_HEIGHT + 12, UIStyles.TEXT_MUTED);
            return;
        }

        List<ChatMessage> messages = channel.getMessageHistory();
        int visibleCount = getVisibleMessageCount();
        int maxScroll = Math.max(0, messages.size() - visibleCount);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(0, scrollOffset);

        int startIdx = Math.max(0, messages.size() - visibleCount - scrollOffset);
        int endIdx = Math.min(messages.size(), startIdx + visibleCount);

        renderedMessages.clear();
        int yOffset = y + HEADER_HEIGHT + PADDING;
        for (int i = startIdx; i < endIdx; i++) {
            ChatMessage msg = messages.get(i);
            if (yOffset >= y + HEADER_HEIGHT && yOffset < y + height - PADDING) {
                // 悬停高亮
                if (mouseY >= yOffset && mouseY < yOffset + MESSAGE_HEIGHT
                        && mouseX >= x && mouseX < x + width - SCROLLBAR_WIDTH) {
                    gg.fill(x, yOffset - 1, x + width - SCROLLBAR_WIDTH - 2, yOffset + MESSAGE_HEIGHT - 1,
                            UIStyles.BG_HOVER);
                }
                renderMessage(gg, msg, yOffset);
                renderedMessages.add(new RenderedMessage(msg, yOffset));
            }
            yOffset += MESSAGE_HEIGHT;
        }

        renderScrollbar(gg, messages.size(), visibleCount);
    }

    private void renderHeader(GuiGraphics gg, Object channelObj, int mouseX, int mouseY, float pt) {
        gg.fill(x, y, x + width, y + HEADER_HEIGHT, UIStyles.BG_HEADER);
        gg.fill(x, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, UIStyles.DIVIDER);

        Minecraft mc = Minecraft.getInstance();
        if (channelObj instanceof com.lokins.citychat.data.ChatChannel channel) {
            boolean isNotify = channel.isNotificationChannel();
            String prefix = isNotify ? "📢 " : "# ";
            gg.drawString(mc.font, prefix + channel.getDisplayName(),
                    x + PADDING, y + 6, UIStyles.TEXT_WHITE);

            if (isNotify) {
                gg.drawString(mc.font, "通知公告 · 只读",
                        x + PADDING, y + 16, UIStyles.TEXT_FAINT);
            } else {
                String memberInfo = channel.getMemberCount() + " 名成员";
                gg.drawString(mc.font, memberInfo,
                        x + PADDING, y + 16, UIStyles.TEXT_FAINT);
                // 仅非通知频道显示信息按钮
                infoButton.render(gg, mouseX, mouseY, pt);
            }
        } else {
            gg.drawString(mc.font, "未选择群组", x + PADDING, y + 10, UIStyles.TEXT_MUTED);
        }
    }

    private void renderMessage(GuiGraphics gg, ChatMessage message, int yPos) {
        Minecraft mc = Minecraft.getInstance();
        int textX = x + PADDING;

        // 时间戳
        String timeStr = TIME_FORMAT.format(new Date(message.getTimestamp()));
        gg.drawString(mc.font, timeStr, textX, yPos, UIStyles.TEXT_FAINT);
        textX += mc.font.width(timeStr) + 6;

        // 发送者
        String nameStr = message.getSenderName();
        gg.drawString(mc.font, nameStr, textX, yPos, UIStyles.TEXT_LINK);
        textX += mc.font.width(nameStr) + 6;

        // 消息内容：尝试解析 Component JSON（支持多语言翻译），失败则直接显示原文
        String rawContent = message.getContent();
        net.minecraft.network.chat.Component displayContent = tryParseComponentJson(rawContent);
        if (displayContent != null) {
            gg.drawString(mc.font, displayContent, textX, yPos, UIStyles.TEXT_PRIMARY);
        } else {
            gg.drawString(mc.font, rawContent, textX, yPos, UIStyles.TEXT_PRIMARY);
        }
    }

    private void renderScrollbar(GuiGraphics gg, int totalMessages, int visibleCount) {
        if (totalMessages <= visibleCount) return;

        int trackX = x + width - SCROLLBAR_WIDTH - 1;
        int trackY = y + HEADER_HEIGHT + 2;
        int trackHeight = height - HEADER_HEIGHT - 4;

        float ratio = (float) visibleCount / totalMessages;
        int thumbHeight = Math.max(16, (int) (trackHeight * ratio));
        int maxScroll = totalMessages - visibleCount;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = trackY + (int) ((trackHeight - thumbHeight) * (1f - scrollRatio));

        UIStyles.drawScrollbar(gg, trackX, trackY, SCROLLBAR_WIDTH, trackHeight, thumbY, thumbHeight, false);
    }

    private boolean isCurrentChannelNotification() {
        String channelId = parent.getCurrentChannelId();
        if (channelId == null) return false;
        var ch = parent.getChatManager().getChannelManager().getChannel(channelId);
        return ch != null && ch.isNotificationChannel();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu != null && contextMenu.isVisible()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (!isMouseOver((int) mouseX, (int) mouseY)) return false;

        // 通知频道：无信息按钮、无右键菜单
        if (isCurrentChannelNotification()) return false;

        if (button == 0 && infoButton.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 1) {
            for (RenderedMessage rm : renderedMessages) {
                if (mouseY >= rm.yPos && mouseY < rm.yPos + MESSAGE_HEIGHT
                        && mouseX >= x && mouseX < x + width - SCROLLBAR_WIDTH) {
                    contextMenu = new MessageContextMenu((int) mouseX, (int) mouseY, rm.message, parent);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (isMouseOver((int) pMouseX, (int) pMouseY)) {
            scrollOffset = Math.max(0, (int) (scrollOffset - pDelta));
            return true;
        }
        return false;
    }

    public void updateChannel(String channelId) { scrollOffset = 0; contextMenu = null; }

    public void closeContextMenu() {
        if (contextMenu != null) { contextMenu.close(); contextMenu = null; }
    }

    public void renderContextMenu(GuiGraphics gg) {
        if (contextMenu != null && contextMenu.isVisible()) contextMenu.render(gg);
    }

    private boolean isMouseOver(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /**
     * 尝试将字符串解析为 Minecraft Component JSON。
     * 如果字符串以 '{' 或 '[' 或 '"' 开头且能成功解析，返回 Component；否则返回 null。
     */
    private static net.minecraft.network.chat.Component tryParseComponentJson(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        char first = raw.charAt(0);
        if (first != '{' && first != '[' && first != '"') return null;
        try {
            return net.minecraft.network.chat.Component.Serializer.fromJson(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
