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
 * 切换群组类型弹窗 —— 使用 LDLib 纹理渲染
 */
public class ChangeGroupTypeDialog extends Screen {
    private static final int DW = 300, DH = 180;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox passwordInput;
    private CCButton publicBtn, normalBtn, encryptedBtn;
    private CCButton confirmBtn, cancelBtn;
    private String hint = "";
    private int hintColor = UIStyles.COLOR_OK;
    private final ChatChannel.GroupAccess currentAccess;
    private ChatChannel.GroupAccess selectedAccess;

    public ChangeGroupTypeDialog(GroupInfoScreen parentScreen, ChatChannel channel) {
        super(Component.literal("切换群组类型"));
        this.parentScreen = parentScreen;
        this.channel = channel;
        this.currentAccess = channel.getAccess();
        this.selectedAccess = this.currentAccess;
    }

    @Override
    protected void init() {
        int px = (width - DW) / 2, py = (height - DH) / 2;

        passwordInput = new EditBox(this.font, px + 12, py + 92, DW - 24, 18, Component.literal("密码"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);
        passwordInput.setVisible(requiresPasswordInput());
        if (requiresPasswordInput()) this.setInitialFocus(passwordInput);

        publicBtn = new CCButton(px + 12, py + 52, 88, 20, "公开",
                b -> { selectedAccess = ChatChannel.GroupAccess.PUBLIC; passwordInput.setVisible(requiresPasswordInput()); });
        normalBtn = new CCButton(px + 106, py + 52, 88, 20, "普通",
                b -> { selectedAccess = ChatChannel.GroupAccess.NORMAL; passwordInput.setVisible(requiresPasswordInput()); });
        encryptedBtn = new CCButton(px + 200, py + 52, 88, 20, "加密",
                b -> { selectedAccess = ChatChannel.GroupAccess.ENCRYPTED; passwordInput.setVisible(requiresPasswordInput()); passwordInput.setFocused(true); });

        confirmBtn = new CCButton(px + 12, py + DH - 28, 130, 20, "确认", b -> confirm(), true);
        cancelBtn = new CCButton(px + DW - 142, py + DH - 28, 130, 20, "取消",
                b -> this.minecraft.setScreen(parentScreen));
    }

    private boolean requiresPasswordInput() {
        return selectedAccess == ChatChannel.GroupAccess.ENCRYPTED
                && currentAccess != ChatChannel.GroupAccess.ENCRYPTED;
    }

    private void confirm() {
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        if (me == null) { hint = "出错了"; hintColor = UIStyles.COLOR_ERR; return; }
        if (selectedAccess == currentAccess) { hint = "类型未变化"; hintColor = UIStyles.COLOR_WARN; return; }

        String password = "";
        if (requiresPasswordInput()) {
            String value = passwordInput.getValue();
            if (value == null || value.trim().length() < 4) {
                hint = "密码至少4位"; hintColor = UIStyles.COLOR_ERR; return;
            }
            password = value.trim();
        }

        ChatNetwork.requestChangeAccess(channel.getChannelId(), selectedAccess, password);
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

        gg.drawString(this.font, "切换群组类型", px + 12, py + 10, UIStyles.TEXT_WHITE);
        UIStyles.drawDivider(gg, px + 8, py + 22, DW - 16);
        gg.drawString(this.font, "当前: " + currentAccess.getDisplayName(), px + 12, py + 24, UIStyles.TEXT_SECONDARY);
        gg.drawString(this.font, "切换至: " + selectedAccess.getDisplayName(), px + 12, py + 38, UIStyles.TEXT_SECONDARY);

        publicBtn.render(gg, mouseX, mouseY, pt);
        normalBtn.render(gg, mouseX, mouseY, pt);
        encryptedBtn.render(gg, mouseX, mouseY, pt);

        passwordInput.setVisible(requiresPasswordInput());
        if (requiresPasswordInput()) {
            gg.drawString(this.font, "请输入密码:", px + 12, py + 78, UIStyles.TEXT_PRIMARY);
            passwordInput.render(gg, mouseX, mouseY, pt);
        }

        if (!hint.isEmpty()) {
            gg.drawString(this.font, hint, px + 12, py + DH - 40, hintColor);
        }

        confirmBtn.render(gg, mouseX, mouseY, pt);
        cancelBtn.render(gg, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (publicBtn.mouseClicked(mx, my, btn)) return true;
        if (normalBtn.mouseClicked(mx, my, btn)) return true;
        if (encryptedBtn.mouseClicked(mx, my, btn)) return true;
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
