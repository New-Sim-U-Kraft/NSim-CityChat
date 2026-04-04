package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatMessage;
import com.lokins.citychat.data.MessageAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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

    private static final int ACTION_ROW_HEIGHT = 14;
    private static final int ACTION_BTN_PADDING = 2;

    private final int x, y, width, height;
    private final ChatScreen parent;

    private int scrollOffset = 0;
    private MessageContextMenu contextMenu;
    private final List<RenderedMessage> renderedMessages = new ArrayList<>();
    private final List<ActionButtonRect> actionButtonRects = new ArrayList<>();
    private final CCButton infoButton;

    private record RenderedMessage(ChatMessage message, int yPos) {}
    private record ActionButtonRect(int x1, int y1, int x2, int y2, String command) {}

    public ChatPanelWidget(int x, int y, int width, int height, ChatScreen parent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parent = parent;

        this.infoButton = new CCButton(x + width - 50, y + 4, 42, 20, "信息",
                b -> parent.openGroupInfoForCurrentChannel());
    }

    /** 计算单条消息占用的像素高度（含换行和 action 按钮行） */
    private int getMessageHeight(ChatMessage msg) {
        Minecraft mc = Minecraft.getInstance();
        int contentWidth = width - SCROLLBAR_WIDTH - PADDING * 2;
        // 前缀宽度：时间 + 发送者
        String timeStr = TIME_FORMAT.format(new Date(msg.getTimestamp()));
        int prefixWidth = mc.font.width(timeStr) + 6 + mc.font.width(msg.getSenderName()) + 6;
        int availableWidth = contentWidth - prefixWidth;
        if (availableWidth < 40) availableWidth = contentWidth; // 前缀太长时内容独占一行

        String content = msg.getContent();
        int lines = 1;
        if (availableWidth > 0 && mc.font.width(content) > availableWidth) {
            List<FormattedCharSequence> wrapped = mc.font.split(Component.literal(content), availableWidth);
            lines = Math.max(1, wrapped.size());
        }
        int h = MESSAGE_HEIGHT + (lines - 1) * (mc.font.lineHeight + 1);
        if (msg.hasActions()) h += ACTION_ROW_HEIGHT;
        return h;
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
        int contentAreaHeight = height - HEADER_HEIGHT - PADDING * 2;

        // 计算所有消息的总高度用于滚动
        int totalHeight = 0;
        for (ChatMessage msg : messages) totalHeight += getMessageHeight(msg);
        int maxScroll = Math.max(0, totalHeight - contentAreaHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(0, scrollOffset);

        // 从底部开始渲染（最新消息在底部）
        renderedMessages.clear();
        actionButtonRects.clear();
        int areaTop = y + HEADER_HEIGHT + PADDING;
        int areaBottom = y + height - PADDING;

        // 计算起始 y：所有消息从 areaTop 开始，向下排列，再减去 scrollOffset
        int yOffset = areaTop - scrollOffset;
        // 如果消息总高度不足面板，从底部对齐
        if (totalHeight < contentAreaHeight) {
            yOffset = areaBottom - totalHeight;
        }

        // 启用裁剪区域
        gg.enableScissor(x, areaTop, x + width, areaBottom);

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            int msgHeight = getMessageHeight(msg);
            int msgBottom = yOffset + msgHeight;
            if (msgBottom > areaTop && yOffset < areaBottom) {
                // 悬停高亮
                if (mouseY >= yOffset && mouseY < msgBottom
                        && mouseX >= x && mouseX < x + width - SCROLLBAR_WIDTH) {
                    gg.fill(x, yOffset - 1, x + width - SCROLLBAR_WIDTH - 2, msgBottom - 1, UIStyles.BG_HOVER);
                }
                renderMessage(gg, msg, yOffset);
                renderedMessages.add(new RenderedMessage(msg, yOffset));
            }
            yOffset += msgHeight;
        }

        gg.disableScissor();

        renderScrollbar(gg, totalHeight, contentAreaHeight);
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

        // 消息内容（自动换行）
        String content = message.getContent();
        int availableWidth = x + width - SCROLLBAR_WIDTH - PADDING - textX;
        if (availableWidth < 40) {
            availableWidth = width - SCROLLBAR_WIDTH - PADDING * 2;
            textX = x + PADDING;
            yPos += mc.font.lineHeight + 1;
        }

        if (mc.font.width(content) <= availableWidth) {
            gg.drawString(mc.font, content, textX, yPos, UIStyles.TEXT_PRIMARY);
            yPos += mc.font.lineHeight + 1;
        } else {
            List<FormattedCharSequence> lines = mc.font.split(Component.literal(content), availableWidth);
            for (FormattedCharSequence line : lines) {
                gg.drawString(mc.font, line, textX, yPos, UIStyles.TEXT_PRIMARY);
                yPos += mc.font.lineHeight + 1;
            }
        }

        // 渲染 action 按钮
        if (message.hasActions()) {
            int btnX = x + PADDING;
            for (MessageAction action : message.getActions()) {
                int btnTextWidth = mc.font.width(action.label());
                int btnWidth = btnTextWidth + ACTION_BTN_PADDING * 2 + 4;
                int btnColor = action.color() | 0xFF000000;
                int btnBgColor = 0xFF3A3A3A;

                gg.fill(btnX, yPos, btnX + btnWidth, yPos + ACTION_ROW_HEIGHT - 2, btnBgColor);
                gg.drawString(mc.font, action.label(), btnX + ACTION_BTN_PADDING + 2, yPos + 2, btnColor);

                actionButtonRects.add(new ActionButtonRect(btnX, yPos, btnX + btnWidth, yPos + ACTION_ROW_HEIGHT - 2, action.command()));
                btnX += btnWidth + 4;
            }
        }
    }

    private void renderScrollbar(GuiGraphics gg, int totalHeight, int areaHeight) {
        if (totalHeight <= areaHeight) return;

        int trackX = x + width - SCROLLBAR_WIDTH - 1;
        int trackY = y + HEADER_HEIGHT + 2;
        int trackHeight = height - HEADER_HEIGHT - 4;

        float ratio = (float) areaHeight / totalHeight;
        int thumbHeight = Math.max(16, (int) (trackHeight * ratio));
        int maxScroll = totalHeight - areaHeight;
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

        // 检查 action 按钮点击（通知频道也允许点击 action 按钮）
        if (button == 0) {
            for (ActionButtonRect rect : actionButtonRects) {
                if (mouseX >= rect.x1 && mouseX <= rect.x2 && mouseY >= rect.y1 && mouseY <= rect.y2) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        String command = rect.command;
                        if (command.startsWith("/")) command = command.substring(1);
                        mc.player.connection.sendCommand(command);
                    }
                    return true;
                }
            }
        }

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
            scrollOffset = Math.max(0, scrollOffset - (int) (pDelta * MESSAGE_HEIGHT));
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

}
