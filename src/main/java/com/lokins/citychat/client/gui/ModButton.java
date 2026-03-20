package com.lokins.citychat.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * CityChat 统一按钮样式 —— 扁平暗色，圆角模拟，hover 高亮，按下反馈。
 * <p>
 * 配色与模组 UI 面板/边框一致：
 * <ul>
 *   <li>常态背景 #2A2F38，边框 #3E4A5A</li>
 *   <li>悬停背景 #3A4658，边框 #5A7A9A</li>
 *   <li>按下背景 #222833</li>
 *   <li>禁用背景 #1E1E1E，文字变暗</li>
 * </ul>
 */
public class ModButton extends Button {

    // 常态
    private static final int BG_NORMAL    = 0xFF2A2F38;
    private static final int BD_NORMAL    = 0xFF3E4A5A;
    // 悬停
    private static final int BG_HOVER     = 0xFF3A4658;
    private static final int BD_HOVER     = 0xFF5A7A9A;
    // 按下
    private static final int BG_PRESSED   = 0xFF222833;
    // 禁用
    private static final int BG_DISABLED  = 0xFF1E1E1E;
    private static final int BD_DISABLED  = 0xFF2A2A2A;
    // 文字
    private static final int TEXT_NORMAL  = 0xFFE0E0E0;
    private static final int TEXT_HOVER   = 0xFFFFFFFF;
    private static final int TEXT_DISABLED= 0xFF666666;

    private ModButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    /* ---------- 工厂方法 ---------- */

    public static Builder modBuilder(Component label, OnPress onPress) {
        return new Builder(label, onPress);
    }

    public static class Builder {
        private final Component label;
        private final OnPress onPress;
        private int x, y, w = 140, h = 20;

        public Builder(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public ModButton build() {
            return new ModButton(x, y, w, h, label, onPress);
        }
    }

    /* ---------- 渲染 ---------- */

    @Override
    protected void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hovered = isHoveredOrFocused();

        int bg, bd, textColor;
        if (!this.active) {
            bg = BG_DISABLED;
            bd = BD_DISABLED;
            textColor = TEXT_DISABLED;
        } else if (isPressed(mouseX, mouseY)) {
            bg = BG_PRESSED;
            bd = BD_HOVER;
            textColor = TEXT_HOVER;
        } else if (hovered) {
            bg = BG_HOVER;
            bd = BD_HOVER;
            textColor = TEXT_HOVER;
        } else {
            bg = BG_NORMAL;
            bd = BD_NORMAL;
            textColor = TEXT_NORMAL;
        }

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();

        // 背景填充
        gg.fill(x0, y0, x1, y1, bg);

        // 1px 边框
        gg.fill(x0, y0, x1, y0 + 1, bd);       // top
        gg.fill(x0, y1 - 1, x1, y1, bd);       // bottom
        gg.fill(x0, y0 + 1, x0 + 1, y1 - 1, bd); // left
        gg.fill(x1 - 1, y0 + 1, x1, y1 - 1, bd); // right

        // 悬停时顶部加一条亮线（模拟微光）
        if (hovered && this.active) {
            gg.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x30FFFFFF);
        }

        // 居中文字
        String text = getMessage().getString();
        int textWidth = font.width(text);
        int maxTextWidth = getWidth() - 8;
        if (textWidth > maxTextWidth) {
            // 截断 + 省略号
            while (textWidth > maxTextWidth && text.length() > 1) {
                text = text.substring(0, text.length() - 1);
                textWidth = font.width(text + "..");
            }
            text = text + "..";
            textWidth = font.width(text);
        }
        int tx = x0 + (getWidth() - textWidth) / 2;
        int ty = y0 + (getHeight() - 8) / 2;
        gg.drawString(font, text, tx, ty, textColor);
    }

    private boolean isPressed(int mouseX, int mouseY) {
        return this.active && this.isHovered
                && mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight()
                && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed();
    }
}
