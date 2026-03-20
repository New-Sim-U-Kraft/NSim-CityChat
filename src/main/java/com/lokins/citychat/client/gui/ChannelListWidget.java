package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.integration.SimukraftDetector;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道列表组件 — 三分组：群组、私聊、城市通知（仅 simukraft 存在时显示）
 */
public class ChannelListWidget extends AbstractChatWidget {
    private static final int LINE_HEIGHT = 16;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 42;
    private static final int GROUP_HEADER_HEIGHT = 16;

    private int selectedIndex = -1;
    private boolean groupCollapsed = false;
    private boolean privateCollapsed = false;
    private boolean notifyCollapsed = false;
    private int scrollOffset = 0;

    /** 当前帧渲染的行列表，用于点击命中测试 */
    private final List<RowEntry> renderedRows = new ArrayList<>();

    public ChannelListWidget(int x, int y, int width, int height, ChatScreen parent) {
        super(x, y, width, height, parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, 0xFF2a2a2a);
        renderBorder(guiGraphics, 0xFF4a4a4a);

        Minecraft mc = Minecraft.getInstance();

        // 折叠态：显示展开、当前群号缩略、创建和加入。
        if (width <= 24 || parent.isChannelListCollapsed()) {
            guiGraphics.drawCenteredString(mc.font, ">", x + width / 2, y + 6, 0xFFffffff);

            String groupNoMini = "#-";
            String currentId = parent.getCurrentChannelId();
            if (currentId != null) {
                ChatChannel current = parent.getChatManager().getChannelManager().getChannel(currentId);
                if (current != null) {
                    groupNoMini = "#" + current.getGroupNumber();
                }
            }
            guiGraphics.fill(x + 3, y + 18, x + width - 3, y + 31, 0xFF3A4658);
            guiGraphics.drawCenteredString(mc.font, groupNoMini, x + width / 2, y + 20, 0xFFaaaaaa);

            guiGraphics.drawCenteredString(mc.font, "+", x + width / 2, y + height - 28, 0xFF93d17c);
            guiGraphics.drawCenteredString(mc.font, "J", x + width / 2, y + height - 14, 0xFF8CC0FF);
            return;
        }

        guiGraphics.drawString(mc.font, "群组", x + 6, y + 8, 0xFFffffff);
        guiGraphics.drawString(mc.font, "<", x + width - 12, y + 8, 0xFFbbbbbb);

        // 分组频道
        List<ChatChannel> allChannels = getVisibleChannelsForCurrentPlayer();
        List<ChatChannel> groups = new ArrayList<>();
        List<ChatChannel> privates = new ArrayList<>();
        List<ChatChannel> notifications = new ArrayList<>();

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
        int yOffset = contentAreaTop - scrollOffset;
        String activeChannelId = parent.getCurrentChannelId();

        // === 群组分组 ===
        yOffset = renderGroupSection(guiGraphics, mc, "群组", groups, groupCollapsed,
                0xFF4ecdc4, yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.GROUP_HEADER, false);

        // === 私聊分组 ===
        yOffset = renderGroupSection(guiGraphics, mc, "私聊", privates, privateCollapsed,
                0xFF95e1d3, yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.PRIVATE_HEADER, false);

        // === 城市通知分组（仅 simukraft 存在且有通知频道时显示）===
        if (SimukraftDetector.isAvailable() && !notifications.isEmpty()) {
            renderGroupSection(guiGraphics, mc, "城市通知", notifications, notifyCollapsed,
                    0xFFf0a500, yOffset, contentAreaTop, contentAreaBottom, activeChannelId, RowType.NOTIFY_HEADER, true);
        }

        // Footer
        int footerY = y + height - FOOTER_HEIGHT;
        guiGraphics.fill(x + 2, footerY + 2, x + width - 2, footerY + 20, 0xFF3a3a3a);
        guiGraphics.drawString(mc.font, "+ 新建群组", x + 6, footerY + 8, 0xFF93d17c);

        guiGraphics.fill(x + 2, footerY + 22, x + width - 2, footerY + FOOTER_HEIGHT - 2, 0xFF353535);
        guiGraphics.drawString(mc.font, "J 加入群组", x + 6, footerY + 28, 0xFF8CC0FF);
    }

    private int renderGroupSection(GuiGraphics gg, Minecraft mc, String title,
                                   List<ChatChannel> channels, boolean collapsed,
                                   int titleColor, int yOffset,
                                   int contentTop, int contentBottom,
                                   String activeChannelId, RowType headerType,
                                   boolean isNotification) {
        // 分组头
        String arrow = collapsed ? "\u25B6" : "\u25BC";
        String headerText = arrow + " " + title + " (" + channels.size() + ")";

        if (yOffset >= contentTop && yOffset + GROUP_HEADER_HEIGHT <= contentBottom) {
            gg.fill(x + 2, yOffset, x + width - 2, yOffset + GROUP_HEADER_HEIGHT, 0xFF333333);
            gg.drawString(mc.font, headerText, x + 6, yOffset + 4, titleColor);
        }
        renderedRows.add(new RowEntry(headerType, null, yOffset, GROUP_HEADER_HEIGHT));
        yOffset += GROUP_HEADER_HEIGHT;

        if (collapsed) {
            return yOffset;
        }

        // 频道条目
        for (ChatChannel channel : channels) {
            if (yOffset >= contentTop && yOffset + LINE_HEIGHT <= contentBottom) {
                // 高亮当前选中频道
                if (channel.getChannelId().equals(activeChannelId)) {
                    gg.fill(x + 2, yOffset - 1, x + width - 2, yOffset + 13, 0xFF4a6fa5);
                }

                if (isNotification) {
                    // 通知频道：仅显示分类名，使用金色调
                    String displayName = channel.getDisplayName();
                    if (displayName.length() > 8) {
                        displayName = displayName.substring(0, 8) + "..";
                    }
                    gg.drawString(mc.font, displayName, x + 10, yOffset, 0xFFf0a500);
                } else {
                    // 普通频道：显示 [P]/[N]/[E] 标记 + 频道名 + 群号
                    String marker = switch (channel.getAccess()) {
                        case PUBLIC -> "[P]";
                        case NORMAL -> "[N]";
                        case ENCRYPTED -> "[E]";
                    };

                    String channelName = channel.getDisplayName();
                    if (channelName.length() > 6) {
                        channelName = channelName.substring(0, 6) + "..";
                    }
                    gg.drawString(mc.font, marker + channelName, x + 10, yOffset, 0xFFffffff);

                    String groupNo = "#" + channel.getGroupNumber();
                    gg.drawString(mc.font, groupNo, x + width - 30, yOffset, 0xFFaaaaaa);
                }
            }
            renderedRows.add(new RowEntry(RowType.CHANNEL, channel, yOffset, LINE_HEIGHT));
            yOffset += LINE_HEIGHT;
        }

        return yOffset;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver((int) mouseX, (int) mouseY)) {
            return false;
        }

        if (width <= 24 || parent.isChannelListCollapsed()) {
            if (mouseY >= y + height - 20) {
                openJoinDialog();
            } else if (mouseY >= y + height - 36) {
                openCreateDialog();
            } else {
                parent.toggleChannelList();
            }
            return true;
        }

        int localY = (int) mouseY - y;
        if (localY <= HEADER_HEIGHT && mouseX >= x + width - 24) {
            parent.toggleChannelList();
            return true;
        }

        if (localY >= height - FOOTER_HEIGHT && localY < height - 20) {
            openCreateDialog();
            return true;
        }

        if (localY >= height - 20) {
            openJoinDialog();
            return true;
        }

        // 命中测试 renderedRows
        for (RowEntry row : renderedRows) {
            if (mouseY >= row.y && mouseY < row.y + row.height) {
                switch (row.type) {
                    case GROUP_HEADER -> { groupCollapsed = !groupCollapsed; return true; }
                    case PRIVATE_HEADER -> { privateCollapsed = !privateCollapsed; return true; }
                    case NOTIFY_HEADER -> { notifyCollapsed = !notifyCollapsed; return true; }
                    case CHANNEL -> {
                        if (row.channel != null) {
                            parent.setCurrentChannelId(row.channel.getChannelId());
                            return true;
                        }
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (width <= 24 || parent.isChannelListCollapsed()) {
            return false;
        }
        scrollOffset -= (int) (delta * 10);
        scrollOffset = Math.max(0, scrollOffset);

        // 计算最大可滚动量
        int totalContentHeight = computeTotalContentHeight();
        int viewportHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - viewportHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        return true;
    }

    private int computeTotalContentHeight() {
        List<ChatChannel> allChannels = getVisibleChannelsForCurrentPlayer();
        List<ChatChannel> groups = new ArrayList<>();
        List<ChatChannel> privates = new ArrayList<>();
        List<ChatChannel> notifications = new ArrayList<>();

        for (ChatChannel ch : allChannels) {
            switch (ch.getType()) {
                case NOTIFICATION -> notifications.add(ch);
                case PRIVATE -> privates.add(ch);
                default -> groups.add(ch);
            }
        }

        int total = 0;
        // 群组
        total += GROUP_HEADER_HEIGHT;
        if (!groupCollapsed) total += groups.size() * LINE_HEIGHT;
        // 私聊
        total += GROUP_HEADER_HEIGHT;
        if (!privateCollapsed) total += privates.size() * LINE_HEIGHT;
        // 通知
        if (SimukraftDetector.isAvailable() && !notifications.isEmpty()) {
            total += GROUP_HEADER_HEIGHT;
            if (!notifyCollapsed) total += notifications.size() * LINE_HEIGHT;
        }
        return total;
    }

    private List<ChatChannel> getVisibleChannelsForCurrentPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return parent.getChatManager().getChannelManager().getAllActiveChannels();
        }
        return parent.getChatManager().getChannelManager().getVisibleChannelsForPlayer(player.getUUID());
    }

    private void openCreateDialog() {
        Minecraft.getInstance().setScreen(new CreateGroupScreen(parent, this));
    }

    private void openJoinDialog() {
        Minecraft.getInstance().setScreen(new JoinGroupScreen(parent, this));
    }

    public void createGroupForCurrentPlayer(String requestedName, ChatChannel.GroupAccess access, String password) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        String displayName = requestedName == null || requestedName.isBlank()
                ? "群组" + (parent.getChatManager().getChannelManager().getChannelCount() + 1)
                : requestedName.trim();

        ChatNetwork.requestCreateGroup(displayName, access, password);
    }

    public boolean joinGroupForCurrentPlayer(String query, String password) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        if (query == null || query.isBlank()) {
            return false;
        }

        ChatNetwork.requestJoinGroup(query, password);
        return true;
    }

    public void selectChannel(int index) {
        List<ChatChannel> channels = getVisibleChannelsForCurrentPlayer();
        if (index < 0 || index >= channels.size()) {
            return;
        }

        selectedIndex = index;
        parent.setCurrentChannelId(channels.get(index).getChannelId());
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    // === 行条目类型 ===

    private enum RowType {
        GROUP_HEADER,
        PRIVATE_HEADER,
        NOTIFY_HEADER,
        CHANNEL
    }

    private record RowEntry(RowType type, ChatChannel channel, int y, int height) {}
}
