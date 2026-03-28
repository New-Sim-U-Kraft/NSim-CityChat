package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 修改密码弹窗 —— 使用 LDLib 纹理渲染
 */
public class ChangePasswordDialog extends Screen {
    private static final int DW = 300, DH = 140;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox passwordInput;
    private CCButton confirmBtn, cancelBtn;
    private String hint = "";
    private int hintColor = UIStyles.COLOR_OK;

    public ChangePasswordDialog(GroupInfoScreen parentScreen, ChatChannel channel) {
        super(Component.literal("修改密码"));
        this.parentScreen = parentScreen;
        this.channel = channel;
    }

    @Override
    protected void init() {
        int px = (width - DW) / 2, py = (height - DH) / 2;

        passwordInput = new EditBox(this.font, px + 12, py + 50, DW - 24, 18, Component.literal("新密码"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);
        this.setInitialFocus(passwordInput);

        confirmBtn = new CCButton(px + 12, py + DH - 28, 130, 20, "确认", b -> confirm(), true);
        cancelBtn = new CCButton(px + DW - 142, py + DH - 28, 130, 20, "取消",
                b -> this.minecraft.setScreen(parentScreen));
    }

    private void confirm() {
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        if (me == null) { hint = "出错了"; hintColor = UIStyles.COLOR_ERR; return; }

        String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();
        if (password.length() < 4) { hint = "密码至少4位"; hintColor = UIStyles.COLOR_ERR; return; }

        ChatNetwork.requestChangePassword(channel.getChannelId(), password);
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg);

        int px = (width - DW) / 2, py = (height - DH) / 2;

        UIStyles.drawShadow(gg, px, py, DW, DH);
        gg.fill(px, py, px + DW, py + DH, UIStyles.BG_POPUP);
        gg.fill(px, py, px + DW, py + 1, UIStyles.DIVIDER);
        gg.fill(px, py + DH - 1, px + DW, py + DH, UIStyles.DIVIDER);
        gg.fill(px, py, px + 1, py + DH, UIStyles.DIVIDER);
        gg.fill(px + DW - 1, py, px + DW, py + DH, UIStyles.DIVIDER);

        gg.drawString(this.font, "修改密码", px + 12, py + 10, UIStyles.TEXT_WHITE);
        UIStyles.drawDivider(gg, px + 8, py + 22, DW - 16);
        gg.drawString(this.font, "新密码:", px + 12, py + 36, UIStyles.TEXT_SECONDARY);
        passwordInput.render(gg, mouseX, mouseY, pt);

        if (!hint.isEmpty()) {
            gg.drawString(this.font, hint, px + 12, py + DH - 10, hintColor);
        }

        confirmBtn.render(gg, mouseX, mouseY, pt);
        cancelBtn.render(gg, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (confirmBtn.mouseClicked(mx, my, btn)) return true;
        if (cancelBtn.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }
}
