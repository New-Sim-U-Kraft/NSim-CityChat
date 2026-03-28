package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.integration.SimukraftDetector;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道列表 —— Discord 风格：分组、选中指示条、悬停高亮
 */
public class ChannelListWidget {
    private static final int LINE_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 44;
    private static final int SECTION_HEIGHT = 22;
    private static final int PADDING = 6;

    private final int x, y, width, height;
    private final ChatScreen parent;

    private int selectedIndex = -1;
    private boolean groupCollapsed = false;
    private boolean privateCollapsed = false;
    private boolean notifyCollapsed = false;
    private int scrollOffset = 0;

    private final List<RowEntry> renderedRows = new ArrayList<>();

    public ChannelListWidget(int x, int y, int width, int height, ChatScreen parent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parent = parent;
    }

    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        // 侧边栏背景
        gg.fill(x, y, x + width, y + height, UIStyles.BG_BASE);

        Minecraft mc = Minecraft.getInstance();

        // 折叠态
        if (width <= 24 || parent.isChannelListCollapsed()) {
            renderCollapsedMode(gg, mc);
            return;
        }

        // 头部
        gg.fill(x, y, x + width, y + HEADER_HEIGHT, UIStyles.BG_HEADER);
        gg.drawString(mc.font, "频道", x + PADDING + 2, y + 10, UIStyles.TEXT_WHITE);
        gg.drawString(mc.font, "«", x + width - 14, y + 10, UIStyles.TEXT_MUTED);
        UIStyles.drawDivider(gg, x, y + HEADER_HEIGHT - 1, width);

        // 分组频道
        List<ChatChannel> allChannels = getVisibleChannelsForCurrentPlayer();
        List<ChatChannel> groups = new ArrayList<>(), privates = new ArrayList<>(), notifications = new ArrayList<>();
        for (ChatChannel ch : allChannels) {
            switch (ch.getType()) {
                case NOTIFICATION -> notifications.add(ch);
                case PRIVATE -> privates.add(ch);
                default -> groups.add(ch);
            }
        }

        renderedRows.clear();
        int contentAreaTop = y + HEADER_HEIGHT;
        int contentAreaBottom = y + height - FOOTER_HEIGHT;
        int yOffset = contentAreaTop + 4 - scrollOffset;
        String activeChannelId = parent.getCurrentChannelId();

        // 城市通知优先显示（仅 SimuKraft 可用时）
        if (SimukraftDetector.isAvailable() && !notifications.isEmpty()) {
            yOffset = renderSection(gg, mc, "城市通知", notifications, notifyCollapsed,
                    yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.NOTIFY_HEADER, true, mouseX, mouseY);
        }
        yOffset = renderSection(gg, mc, "群组", groups, groupCollapsed,
                yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.GROUP_HEADER, false, mouseX, mouseY);
        renderSection(gg, mc, "私聊", privates, privateCollapsed,
                yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.PRIVATE_HEADER, false, mouseX, mouseY);

        // 底部分隔线
        int footerY = y + height - FOOTER_HEIGHT;
        UIStyles.drawDivider(gg, x, footerY, width);

        // Footer 按钮
        renderFooterButton(gg, mc, footerY + 2, "+ 新建群组", UIStyles.COLOR_OK, mouseX, mouseY);
        renderFooterButton(gg, mc, footerY + 22, "J 加入群组", UIStyles.COLOR_INFO, mouseX, mouseY);
    }

    private void renderCollapsedMode(GuiGraphics gg, Minecraft mc) {
        gg.drawCenteredString(mc.font, "»", x + width / 2, y + 8, UIStyles.TEXT_MUTED);

        String groupNoMini = "#-";
        String currentId = parent.getCurrentChannelId();
        if (currentId != null) {
            ChatChannel current = parent.getChatManager().getChannelManager().getChannel(currentId);
            if (current != null) groupNoMini = "#" + current.getGroupNumber();
        }
        gg.fill(x + 4, y + 20, x + width - 4, y + 34, UIStyles.BG_SURFACE);
        gg.drawCenteredString(mc.font, groupNoMini, x + width / 2, y + 23, UIStyles.TEXT_SECONDARY);

        gg.drawCenteredString(mc.font, "+", x + width / 2, y + height - 28, UIStyles.COLOR_OK);
        gg.drawCenteredString(mc.font, "J", x + width / 2, y + height - 14, UIStyles.COLOR_INFO);
    }

    private int renderSection(GuiGraphics gg, Minecraft mc, String title,
                              List<ChatChannel> channels, boolean collapsed,
                              int yOffset, int contentTop, int contentBottom,
                              String activeChannelId, RowType headerType,
                              boolean isNotification, int mouseX, int mouseY) {
        // 分组头：大写标题 + 折叠箭头
        String arrow = collapsed ? "▸" : "▾";
        String headerText = arrow + "  " + title.toUpperCase() + " — " + channels.size();

        if (yOffset >= contentTop && yOffset + SECTION_HEIGHT <= contentBottom) {
            gg.drawString(mc.font, headerText, x + PADDING + 2, yOffset + 7, UIStyles.CHAN_CATEGORY);
        }
        renderedRows.add(new RowEntry(headerType, null, yOffset, SECTION_HEIGHT));
        yOffset += SECTION_HEIGHT;

        if (collapsed) return yOffset;

        for (ChatChannel channel : channels) {
            if (yOffset >= contentTop && yOffset + LINE_HEIGHT <= contentBottom) {
                boolean isSelected = channel.getChannelId().equals(activeChannelId);
                boolean isHovered = !isSelected
                        && mouseX >= x && mouseX < x + width
                        && mouseY >= yOffset && mouseY < yOffset + LINE_HEIGHT;

                // 选中/悬停背景
                if (isSelected) {
                    gg.fill(x + 4, yOffset, x + width - 4, yOffset + LINE_HEIGHT, UIStyles.CHAN_SELECTED);
                    // 左侧强调竖条
                    UIStyles.drawAccentBar(gg, x, yOffset, LINE_HEIGHT);
                } else if (isHovered) {
                    gg.fill(x + 4, yOffset, x + width - 4, yOffset + LINE_HEIGHT, UIStyles.CHAN_HOVER);
                    UIStyles.drawHoverBar(gg, x, yOffset, LINE_HEIGHT);
                }

                if (isNotification) {
                    // 通知频道：显示完整频道名（按像素宽度截断）
                    String displayName = channel.getDisplayName();
                    int maxNameW = width - PADDING * 2 - 12;
                    displayName = trimToFit(mc, displayName, maxNameW);
                    int nameColor = isSelected ? UIStyles.TEXT_WHITE : UIStyles.COLOR_WARN;
                    gg.drawString(mc.font, displayName, x + PADDING + 6, yOffset + 5, nameColor);
                } else {
                    // 普通频道：# 前缀 + 名称 + 右侧群号
                    String groupNo = "#" + channel.getGroupNumber();
                    int gnWidth = mc.font.width(groupNo);
                    int maxNameW = width - PADDING * 2 - 12 - gnWidth - 10;
                    String prefix = "# ";
                    String channelName = trimToFit(mc, prefix + channel.getDisplayName(), maxNameW);
                    int nameColor = isSelected ? UIStyles.TEXT_WHITE : (isHovered ? UIStyles.TEXT_PRIMARY : UIStyles.TEXT_SECONDARY);
                    gg.drawString(mc.font, channelName, x + PADDING + 6, yOffset + 5, nameColor);
                    gg.drawString(mc.font, groupNo, x + width - gnWidth - 8, yOffset + 5, UIStyles.TEXT_FAINT);
                }
            }
            renderedRows.add(new RowEntry(RowType.CHANNEL, channel, yOffset, LINE_HEIGHT));
            yOffset += LINE_HEIGHT;
        }
        return yOffset;
    }

    private void renderFooterButton(GuiGraphics gg, Minecraft mc, int btnY, String label, int color, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x + 4 && mouseX < x + width - 4
                && mouseY >= btnY && mouseY < btnY + 20;
        if (hovered) {
            gg.fill(x + 4, btnY, x + width - 4, btnY + 20, UIStyles.BG_HOVER);
        }
        gg.drawString(mc.font, label, x + PADDING + 4, btnY + 6, color);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver((int) mouseX, (int) mouseY)) return false;

        if (width <= 24 || parent.isChannelListCollapsed()) {
            if (mouseY >= y + height - 20) openJoinDialog();
            else if (mouseY >= y + height - 36) openCreateDialog();
            else parent.toggleChannelList();
            return true;
        }

        int localY = (int) mouseY - y;
        if (localY <= HEADER_HEIGHT && mouseX >= x + width - 24) { parent.toggleChannelList(); return true; }

        int footerY = height - FOOTER_HEIGHT;
        if (localY >= footerY && localY < footerY + 22) { openCreateDialog(); return true; }
        if (localY >= footerY + 22) { openJoinDialog(); return true; }

        for (RowEntry row : renderedRows) {
            if (mouseY >= row.y && mouseY < row.y + row.height) {
                switch (row.type) {
                    case GROUP_HEADER -> { groupCollapsed = !groupCollapsed; return true; }
                    case PRIVATE_HEADER -> { privateCollapsed = !privateCollapsed; return true; }
                    case NOTIFY_HEADER -> { notifyCollapsed = !notifyCollapsed; return true; }
                    case CHANNEL -> { if (row.channel != null) { parent.setCurrentChannelId(row.channel.getChannelId()); return true; } }
                }
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver((int) mouseX, (int) mouseY)) return false;
        if (width <= 24 || parent.isChannelListCollapsed()) return false;

        scrollOffset -= (int) (delta * 12);
        scrollOffset = Math.max(0, scrollOffset);
        int maxScroll = Math.max(0, computeTotalContentHeight() - (height - HEADER_HEIGHT - FOOTER_HEIGHT));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        return true;
    }

    private int computeTotalContentHeight() {
        List<ChatChannel> allChannels = getVisibleChannelsForCurrentPlayer();
        List<ChatChannel> groups = new ArrayList<>(), privates = new ArrayList<>(), notifications = new ArrayList<>();
        for (ChatChannel ch : allChannels) {
            switch (ch.getType()) {
                case NOTIFICATION -> notifications.add(ch);
                case PRIVATE -> privates.add(ch);
                default -> groups.add(ch);
            }
        }
        int total = SECTION_HEIGHT;
        if (!groupCollapsed) total += groups.size() * LINE_HEIGHT;
        total += SECTION_HEIGHT;
        if (!privateCollapsed) total += privates.size() * LINE_HEIGHT;
        if (SimukraftDetector.isAvailable() && !notifications.isEmpty()) {
            total += SECTION_HEIGHT;
            if (!notifyCollapsed) total += notifications.size() * LINE_HEIGHT;
        }
        return total + 8;
    }

    private List<ChatChannel> getVisibleChannelsForCurrentPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return parent.getChatManager().getChannelManager().getAllActiveChannels();
        return parent.getChatManager().getChannelManager().getVisibleChannelsForPlayer(player.getUUID());
    }

    private void openCreateDialog() { Minecraft.getInstance().setScreen(new CreateGroupScreen(parent, this)); }
    private void openJoinDialog() { Minecraft.getInstance().setScreen(new JoinGroupScreen(parent, this)); }

    public void createGroupForCurrentPlayer(String requestedName, ChatChannel.GroupAccess access, String password) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        String displayName = requestedName == null || requestedName.isBlank()
                ? "群组" + (parent.getChatManager().getChannelManager().getChannelCount() + 1)
                : requestedName.trim();
        ChatNetwork.requestCreateGroup(displayName, access, password);
    }

    public boolean joinGroupForCurrentPlayer(String query, String password) {
        if (Minecraft.getInstance().player == null) return false;
        if (query == null || query.isBlank()) return false;
        ChatNetwork.requestJoinGroup(query, password);
        return true;
    }

    public void selectChannel(int index) {
        List<ChatChannel> channels = getVisibleChannelsForCurrentPlayer();
        if (index < 0 || index >= channels.size()) return;
        selectedIndex = index;
        parent.setCurrentChannelId(channels.get(index).getChannelId());
    }

    public int getSelectedIndex() { return selectedIndex; }

    /** 按像素宽度截断文字，超出部分用 .. 替代 */
    private static String trimToFit(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) return text;
        String ellipsis = "..";
        int ellipsisW = mc.font.width(ellipsis);
        while (text.length() > 1 && mc.font.width(text) + ellipsisW > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private boolean isMouseOver(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private enum RowType { GROUP_HEADER, PRIVATE_HEADER, NOTIFY_HEADER, CHANNEL }
    private record RowEntry(RowType type, ChatChannel channel, int y, int height) {}
}
