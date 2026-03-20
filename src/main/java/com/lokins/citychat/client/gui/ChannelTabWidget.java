package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/**
 * 频道标签栏组件 - 允许快速切换频道
 */
public class ChannelTabWidget extends AbstractChatWidget {
    private int selectedTabIndex = 0;
    private static final int TAB_WIDTH = 80;
    private static final int TAB_PADDING = 5;

    public ChannelTabWidget(int x, int y, int width, int height, ChatScreen parent) {
        super(x, y, width, height, parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        renderBackground(guiGraphics, 0xFF1a1a1a);
        renderBorder(guiGraphics, 0xFF4a4a4a);

        // 获取所有频道
        List<ChatChannel> channels = parent.getChatManager().getChannelManager().getAllActiveChannels();
        
        Minecraft mc = Minecraft.getInstance();
        int xOffset = x + 5;

        for (int i = 0; i < channels.size() && xOffset < x + width - 10; i++) {
            ChatChannel channel = channels.get(i);
            int tabX = xOffset;
            int tabWidth = Math.min(TAB_WIDTH, (x + width - 10) - xOffset);
            
            // 渲染标签背景
            if (i == selectedTabIndex) {
                guiGraphics.fill(tabX, y + 2, tabX + tabWidth, y + height - 2, 0xFF4a6fa5);
            } else {
                guiGraphics.fill(tabX, y + 2, tabX + tabWidth, y + height - 2, 0xFF3a3a3a);
            }
            
            // 渲染标签文本
            String tabName = channel.getDisplayName();
            if (tabName.length() > 8) {
                tabName = tabName.substring(0, 8) + "..";
            }
            guiGraphics.drawString(mc.font, tabName, tabX + 5, y + height / 2 - 4, 0xFFffffff);
            
            xOffset += tabWidth + TAB_PADDING;
        }
    }

    public void selectTab(int index) {
        selectedTabIndex = index;
        List<ChatChannel> channels = parent.getChatManager().getChannelManager().getAllActiveChannels();
        if (index >= 0 && index < channels.size()) {
            parent.setCurrentChannelId(channels.get(index).getChannelId());
        }
    }

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }
}

