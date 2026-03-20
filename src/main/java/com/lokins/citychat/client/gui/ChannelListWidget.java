package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 频道列表组件
 */
public class ChannelListWidget extends AbstractChatWidget {
    private static final int LINE_HEIGHT = 16;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 42;

    private int selectedIndex = -1;

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

        List<ChatChannel> channels = getVisibleChannelsForCurrentPlayer();
        int yOffset = y + HEADER_HEIGHT;

        String activeChannelId = parent.getCurrentChannelId();
        for (int i = 0; i < channels.size() && yOffset < y + height - FOOTER_HEIGHT; i++) {
            ChatChannel channel = channels.get(i);

            if (channel.getChannelId().equals(activeChannelId)) {
                guiGraphics.fill(x + 2, yOffset - 1, x + width - 2, yOffset + 13, 0xFF4a6fa5);
            }

            String marker = switch (channel.getAccess()) {
                case PUBLIC -> "[P]";
                case NORMAL -> "[N]";
                case ENCRYPTED -> "[E]";
            };

            String channelName = channel.getDisplayName();
            if (channelName.length() > 6) {
                channelName = channelName.substring(0, 6) + "..";
            }
            guiGraphics.drawString(mc.font, marker + channelName, x + 6, yOffset, 0xFFffffff);

            String groupNo = "#" + channel.getGroupNumber();
            guiGraphics.drawString(mc.font, groupNo, x + width - 30, yOffset, 0xFFaaaaaa);
            yOffset += LINE_HEIGHT;
        }

        int footerY = y + height - FOOTER_HEIGHT;
        guiGraphics.fill(x + 2, footerY + 2, x + width - 2, footerY + 20, 0xFF3a3a3a);
        guiGraphics.drawString(mc.font, "+ 新建群组", x + 6, footerY + 8, 0xFF93d17c);

        guiGraphics.fill(x + 2, footerY + 22, x + width - 2, footerY + FOOTER_HEIGHT - 2, 0xFF353535);
        guiGraphics.drawString(mc.font, "J 加入群组", x + 6, footerY + 28, 0xFF8CC0FF);
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

        int index = (localY - HEADER_HEIGHT) / LINE_HEIGHT;
        selectChannel(index);
        return true;
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
        // 不需要手动 requestChannelSync —— 服务端操作成功后会自动广播快照
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
        // 不需要手动 requestChannelSync —— 服务端操作成功后会自动广播快照
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
}
