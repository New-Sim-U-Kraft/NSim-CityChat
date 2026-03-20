package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/**
 * 用户列表组件 - 显示当前频道的成员
 */
public class UserListWidget extends AbstractChatWidget {

    public UserListWidget(int x, int y, int width, int height, ChatScreen parent) {
        super(x, y, width, height, parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        renderBackground(guiGraphics, 0xFF2a2a2a);
        renderBorder(guiGraphics, 0xFF4a4a4a);

        // 获取当前频道
        ChatChannel currentChannel = parent.getChatManager().getChannelManager()
                .getChannel(parent.getCurrentChannelId());
        
        if (currentChannel == null) return;

        Minecraft mc = Minecraft.getInstance();
        
        // 渲染标题
        guiGraphics.drawString(mc.font, "成员列表", x + 5, y + 5, 0xFFffffff);
        
        int yOffset = y + 20;
        var members = currentChannel.getMembers();
        
        for (var memberId : members) {
            if (yOffset >= y + height - 5) break;
            
            // 获取成员信息
            var user = parent.getChatManager().getUser(memberId);
            String memberName = (user != null) ? user.getDisplayName() : "Unknown";
            
            // 标记频道所有者
            if (memberId.equals(currentChannel.getOwnerId())) {
                memberName = "👑 " + memberName;
                guiGraphics.drawString(mc.font, memberName, x + 5, yOffset, 0xFFffd700);
            } else {
                guiGraphics.drawString(mc.font, memberName, x + 5, yOffset, 0xFFcccccc);
            }
            
            yOffset += 15;
        }
    }

    public void updateChannel(String channelId) {
        // 组件会在下一次 render 时自动更新
    }
}

