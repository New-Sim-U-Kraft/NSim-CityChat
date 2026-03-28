package com.lokins.citychat.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * CityChat 按钮 —— 支持主操作（强调色）和次要操作两种风格。
 */
public class CCButton {
    private int x, y, width, height;
    private String text;
    private Consumer<CCButton> onClick;
    private boolean active = true;
    private final boolean primary; // true = 强调色主按钮

    /** 创建次要按钮（灰色） */
    public CCButton(int x, int y, int width, int height, String text, Consumer<CCButton> onClick) {
        this(x, y, width, height, text, onClick, false);
    }

    /** 创建按钮，primary=true 为强调色主按钮 */
    public CCButton(int x, int y, int width, int height, String text, Consumer<CCButton> onClick, boolean primary) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.onClick = onClick;
        this.primary = primary;
    }

    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        boolean hovered = active && isMouseOver(mouseX, mouseY);
        boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();

        int bgColor;
        int textColor;

        if (!active) {
            bgColor = UIStyles.BTN_DIS_BG;
            textColor = UIStyles.BTN_DIS_TEXT;
        } else if (primary) {
            bgColor = pressed ? UIStyles.ACCENT_ACTIVE : (hovered ? UIStyles.ACCENT_HOVER : UIStyles.ACCENT);
            textColor = UIStyles.TEXT_WHITE;
        } else {
            bgColor = pressed ? UIStyles.BG_ACTIVE : (hovered ? UIStyles.BTN_SEC_HOVER : UIStyles.BTN_SEC_BG);
            textColor = UIStyles.BTN_SEC_TEXT;
        }

        // 背景
        gg.fill(x, y, x + width, y + height, bgColor);

        // 禁用遮罩
        if (!active) {
            gg.fill(x, y, x + width, y + height, 0x40000000);
        }

        // 居中文字
        Font font = Minecraft.getInstance().font;
        String display = text;
        int tw = font.width(display);
        int maxW = width - 10;
        if (tw > maxW) {
            while (tw > maxW && display.length() > 1) {
                display = display.substring(0, display.length() - 1);
                tw = font.width(display + "..");
            }
            display = display + "..";
            tw = font.width(display);
        }
        int tx = x + (width - tw) / 2;
        int ty = y + (height - 8) / 2;
        gg.drawString(font, display, tx, ty, textColor);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && active && isMouseOver((int) mouseX, (int) mouseY)) {
            if (onClick != null) onClick.accept(this);
            return true;
        }
        return false;
    }

    public boolean isMouseOver(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public void setActive(boolean active) { this.active = active; }
    public boolean isActive() { return active; }
    public void setText(String text) { this.text = text; }
    public String getText() { return text; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
