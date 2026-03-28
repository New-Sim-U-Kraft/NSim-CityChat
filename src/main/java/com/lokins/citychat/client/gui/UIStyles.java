package com.lokins.citychat.client.gui;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import net.minecraft.client.gui.GuiGraphics;

/**
 * CityChat 设计系统 —— Discord 风格暗色主题
 */
public final class UIStyles {
    private UIStyles() {}

    // ═══════════════════════ 背景层级 ═══════════════════════
    /** 最底层 / 侧边栏 */
    public static final int BG_BASE     = 0xFF1E1F22;
    /** 中间层 / 主内容区 */
    public static final int BG_PRIMARY  = 0xFF2B2D31;
    /** 浮起层 / 卡片、输入框 */
    public static final int BG_SURFACE  = 0xFF313338;
    /** 头部 / 工具栏 */
    public static final int BG_HEADER   = 0xFF232428;
    /** 悬停态 */
    public static final int BG_HOVER    = 0xFF35373C;
    /** 激活/按下 */
    public static final int BG_ACTIVE   = 0xFF3F4147;
    /** 弹出层 / 菜单 */
    public static final int BG_POPUP    = 0xFF111214;

    // ═══════════════════════ 强调色 ═══════════════════════
    /** 主强调 — 蓝紫 */
    public static final int ACCENT          = 0xFF5865F2;
    /** 主强调悬停 */
    public static final int ACCENT_HOVER    = 0xFF4752C4;
    /** 主强调按下 */
    public static final int ACCENT_ACTIVE   = 0xFF3C45A5;
    /** 选中态半透明 */
    public static final int ACCENT_MUTED    = 0x305865F2;
    /** 左侧选中指示条 */
    public static final int ACCENT_BAR      = 0xFFFFFFFF;

    // ═══════════════════════ 语义色 ═══════════════════════
    public static final int COLOR_OK    = 0xFF57F287;
    public static final int COLOR_ERR   = 0xFFED4245;
    public static final int COLOR_WARN  = 0xFFFEE75C;
    public static final int COLOR_INFO  = 0xFF5865F2;

    // ═══════════════════════ 文字色 ═══════════════════════
    /** 主要文字 */
    public static final int TEXT_PRIMARY    = 0xFFF2F3F5;
    /** 次要文字 */
    public static final int TEXT_SECONDARY  = 0xFFB5BAC1;
    /** 弱化文字 */
    public static final int TEXT_MUTED      = 0xFF949BA4;
    /** 超弱 / 时间戳 */
    public static final int TEXT_FAINT      = 0xFF6D6F78;
    /** 链接 / 玩家名 */
    public static final int TEXT_LINK       = 0xFF00A8FC;
    /** 白色（标题） */
    public static final int TEXT_WHITE      = 0xFFFFFFFF;

    // ═══════════════════════ 边框 / 分隔线 ═══════════════════════
    public static final int DIVIDER         = 0xFF3F4147;
    public static final int DIVIDER_SUBTLE  = 0xFF2E3035;
    public static final int BORDER_INPUT    = 0xFF1E1F22;

    // ═══════════════════════ 按钮 ═══════════════════════
    /** 次要按钮（灰色） */
    public static final int BTN_SEC_BG      = 0xFF4E5058;
    public static final int BTN_SEC_HOVER   = 0xFF6D6F78;
    public static final int BTN_SEC_TEXT    = 0xFFFFFFFF;
    /** 禁用 */
    public static final int BTN_DIS_BG      = 0xFF4E5058;
    public static final int BTN_DIS_TEXT    = 0xFF80848E;

    // ═══════════════════════ 滚动条 ═══════════════════════
    public static final int SCROLL_TRACK    = 0xFF2B2D31;
    public static final int SCROLL_THUMB    = 0xFF1A1B1E;
    public static final int SCROLL_THUMB_H  = 0xFF0F1012;

    // ═══════════════════════ 频道列表 ═══════════════════════
    public static final int CHAN_HOVER      = 0x14FFFFFF;
    public static final int CHAN_SELECTED   = 0x1FFFFFFF;
    public static final int CHAN_CATEGORY   = 0xFFB5BAC1;

    // ═══════════════════════ 兼容旧引用 ═══════════════════════
    public static final int TEXT_GRAY  = TEXT_SECONDARY;
    public static final int TEXT_DIM   = TEXT_MUTED;
    public static final int TEXT_BLUE  = TEXT_LINK;
    public static final int TEXT_BTN   = BTN_SEC_TEXT;
    public static final int TEXT_BTN_H = 0xFFFFFFFF;
    public static final int TEXT_BTN_D = BTN_DIS_TEXT;
    public static final int BG_ITEM    = BG_SURFACE;
    public static final int BG_SELECTED= ACCENT;

    // ═══════════════════════ 纹理工厂 ═══════════════════════

    /** 侧边栏背景 */
    public static IGuiTexture sidebarBg() {
        return new ColorRectTexture(BG_BASE);
    }

    /** 主内容区背景 */
    public static IGuiTexture primaryBg() {
        return new ColorRectTexture(BG_PRIMARY);
    }

    /** 面板 / 对话框背景 */
    public static IGuiTexture panelBg() {
        return new ColorRectTexture(BG_POPUP);
    }

    /** 深色面板 */
    public static IGuiTexture darkBg() {
        return new ColorRectTexture(BG_BASE);
    }

    /** 中等深度面板 */
    public static IGuiTexture mediumBg() {
        return new ColorRectTexture(BG_PRIMARY);
    }

    /** 浮起面板（弹窗等） */
    public static IGuiTexture surfaceBg() {
        return new ColorRectTexture(BG_SURFACE);
    }

    /** 标题头背景 */
    public static IGuiTexture headerBg() {
        return new ColorRectTexture(BG_HEADER);
    }

    /** 输入框背景 */
    public static IGuiTexture inputBg() {
        return new ColorRectTexture(BG_POPUP);
    }

    /** 主按钮（强调色） */
    public static IGuiTexture btnPrimary() {
        return new ColorRectTexture(ACCENT);
    }

    /** 主按钮悬停 */
    public static IGuiTexture btnPrimaryHover() {
        return new ColorRectTexture(ACCENT_HOVER);
    }

    /** 次要按钮 */
    public static IGuiTexture btnNormal() {
        return new ColorRectTexture(BTN_SEC_BG);
    }

    /** 次要按钮悬停 */
    public static IGuiTexture btnHover() {
        return new ColorRectTexture(BTN_SEC_HOVER);
    }

    /** 按钮禁用 */
    public static IGuiTexture btnDisabled() {
        return new GuiTextureGroup(
                new ColorRectTexture(BTN_DIS_BG),
                new ColorRectTexture(0x60000000) // 半透明遮罩
        );
    }

    /** 选中高亮 */
    public static IGuiTexture selectedHighlight() {
        return new ColorRectTexture(CHAN_SELECTED);
    }

    /** 悬停高亮 */
    public static IGuiTexture hoverHighlight() {
        return new ColorRectTexture(CHAN_HOVER);
    }

    /** 右键菜单背景 */
    public static IGuiTexture menuBg() {
        return new ColorRectTexture(BG_POPUP);
    }

    /** 分组标题栏背景 */
    public static IGuiTexture sectionHeaderBg() {
        return new ColorRectTexture(0x00000000); // 透明，靠文字颜色区分
    }

    /** 平面矩形 */
    public static ColorRectTexture flatRect(int color) {
        return new ColorRectTexture(color);
    }

    // ═══════════════════════ 渲染辅助 ═══════════════════════

    /** 用 LDLib 纹理在指定区域绘制 */
    public static void draw(IGuiTexture tex, GuiGraphics gg, int x, int y, int w, int h) {
        tex.draw(gg, 0, 0, (float) x, (float) y, w, h);
    }

    /** 绘制水平分隔线 */
    public static void drawDivider(GuiGraphics gg, int x, int y, int w) {
        gg.fill(x, y, x + w, y + 1, DIVIDER);
    }

    /** 绘制左侧强调竖条（频道选中指示器） */
    public static void drawAccentBar(GuiGraphics gg, int x, int y, int h) {
        gg.fill(x, y + 2, x + 3, y + h - 2, ACCENT_BAR);
    }

    /** 绘制悬停时左侧短竖条 */
    public static void drawHoverBar(GuiGraphics gg, int x, int y, int h) {
        int barH = Math.max(4, h / 3);
        int barY = y + (h - barH) / 2;
        gg.fill(x, barY, x + 2, barY + barH, 0x80FFFFFF);
    }

    /** 绘制滚动条 */
    public static void drawScrollbar(GuiGraphics gg, int trackX, int trackY, int trackW, int trackH,
                                     int thumbY, int thumbH, boolean hovered) {
        gg.fill(trackX, trackY, trackX + trackW, trackY + trackH, SCROLL_TRACK);
        gg.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH,
                hovered ? SCROLL_THUMB_H : SCROLL_THUMB);
    }

    /** 绘制菜单阴影 */
    public static void drawShadow(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x - 2, y + 2, x + w + 2, y + h + 4, 0x40000000);
        gg.fill(x - 1, y + 1, x + w + 1, y + h + 2, 0x30000000);
    }
}
