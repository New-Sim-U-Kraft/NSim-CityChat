package com.lokins.citychat.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * GUI 组件的基类
 */
public abstract class AbstractChatWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected ChatScreen parent;

    public AbstractChatWidget(int x, int y, int width, int height, ChatScreen parent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parent = parent;
    }

    /**
     * 渲染组件
     */
    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    /**
     * 处理鼠标点击
     */
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        return false;
    }

    /**
     * 检查鼠标是否在组件内
     */
    protected boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * 渲染容器边框
     */
    protected void renderBorder(GuiGraphics guiGraphics, int color) {
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y, color); // 上边框
        guiGraphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // 下边框
        guiGraphics.fill(x - 1, y, x, y + height, color); // 左边框
        guiGraphics.fill(x + width, y, x + width + 1, y + height, color); // 右边框
    }

    /**
     * 渲染背景
     */
    protected void renderBackground(GuiGraphics guiGraphics, int color) {
        guiGraphics.fill(x, y, x + width, y + height, color);
    }
}

