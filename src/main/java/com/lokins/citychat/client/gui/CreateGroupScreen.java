package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 创建群组弹层 —— 使用 LDLib 纹理渲染
 */
public class CreateGroupScreen extends Screen {
    private static final int DW = 300, DH = 176;

    private final ChatScreen parentScreen;
    private final ChannelListWidget channelListWidget;

    private EditBox nameInput;
    private EditBox passwordInput;
    private CCButton modeBtn, createBtn, cancelBtn;

    private ChatChannel.GroupAccess access = ChatChannel.GroupAccess.NORMAL;
    private String hintText = "支持中文/英文/数字/空格/_/-，长度 1-24";
    private int hintColor = UIStyles.TEXT_SECONDARY;

    public CreateGroupScreen(ChatScreen parentScreen, ChannelListWidget channelListWidget) {
        super(Component.literal("创建群组"));
        this.parentScreen = parentScreen;
        this.channelListWidget = channelListWidget;
    }

    @Override
    protected void init() {
        int px = (this.width - DW) / 2, py = (this.height - DH) / 2;

        nameInput = new EditBox(this.font, px + 12, py + 24, DW - 24, 20, Component.literal("群组名称"));
        nameInput.setMaxLength(24);
        nameInput.setResponder(v -> validateForm());
        this.addWidget(nameInput);

        passwordInput = new EditBox(this.font, px + 12, py + 84, DW - 24, 20, Component.literal("加密群密码"));
        passwordInput.setMaxLength(24);
        passwordInput.setResponder(v -> validateForm());
        this.addWidget(passwordInput);

        this.setInitialFocus(nameInput);

        modeBtn = new CCButton(px + 12, py + 52, 132, 20, modeLabel(), b -> {
            switchAccessMode();
            b.setText(modeLabel());
            validateForm();
        });

        createBtn = new CCButton(px + 12, py + 140, 112, 22, "创建", b -> tryCreate(), true);
        cancelBtn = new CCButton(px + DW - 124, py + 140, 112, 22, "取消",
                b -> this.minecraft.setScreen(parentScreen));

        validateForm();
    }

    private void switchAccessMode() {
        if (access == ChatChannel.GroupAccess.PUBLIC) access = ChatChannel.GroupAccess.NORMAL;
        else if (access == ChatChannel.GroupAccess.NORMAL) access = ChatChannel.GroupAccess.ENCRYPTED;
        else access = ChatChannel.GroupAccess.PUBLIC;
    }

    private String modeLabel() { return "模式: " + access.getDisplayName(); }

    private void tryCreate() {
        if (!validateForm()) return;
        String password = access == ChatChannel.GroupAccess.ENCRYPTED ? passwordInput.getValue().trim() : "";
        channelListWidget.createGroupForCurrentPlayer(nameInput.getValue().trim(), access, password);
        this.minecraft.setScreen(parentScreen);
    }

    private boolean validateForm() {
        String name = nameInput.getValue() == null ? "" : nameInput.getValue().trim();

        if (name.isEmpty()) {
            hintText = "群组名不能为空"; hintColor = UIStyles.COLOR_ERR;
            createBtn.setActive(false); return false;
        }
        if (name.length() > 24) {
            hintText = "群组名长度不能超过 24"; hintColor = UIStyles.COLOR_ERR;
            createBtn.setActive(false); return false;
        }
        if (!name.matches("[\\u4e00-\\u9fa5A-Za-z0-9_\\- ]+")) {
            hintText = "仅允许中文/英文/数字/空格/_/-"; hintColor = UIStyles.COLOR_ERR;
            createBtn.setActive(false); return false;
        }

        boolean duplicate = parentScreen.getChatManager().getChannelManager().getAllActiveChannels().stream()
                .anyMatch(ch -> ch.getDisplayName().equalsIgnoreCase(name));
        if (duplicate) {
            hintText = "群组名已存在，请换一个"; hintColor = UIStyles.COLOR_WARN;
            createBtn.setActive(false); return false;
        }

        if (access == ChatChannel.GroupAccess.ENCRYPTED) {
            String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();
            if (password.length() < 4) {
                hintText = "加密群密码至少 4 位"; hintColor = UIStyles.COLOR_ERR;
                createBtn.setActive(false); return false;
            }
        }

        hintText = "信息有效，可创建"; hintColor = UIStyles.COLOR_OK;
        createBtn.setActive(true); return true;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg);

        int px = (this.width - DW) / 2, py = (this.height - DH) / 2;

        UIStyles.drawShadow(gg, px, py, DW, DH);
        gg.fill(px, py, px + DW, py + DH, UIStyles.BG_POPUP);
        gg.fill(px, py, px + DW, py + 1, UIStyles.DIVIDER);
        gg.fill(px, py + DH - 1, px + DW, py + DH, UIStyles.DIVIDER);
        gg.fill(px, py, px + 1, py + DH, UIStyles.DIVIDER);
        gg.fill(px + DW - 1, py, px + DW, py + DH, UIStyles.DIVIDER);
        gg.drawString(this.font, "创建群组", px + 12, py + 8, UIStyles.TEXT_WHITE);
        UIStyles.drawDivider(gg, px + 8, py + 22, DW - 16);

        nameInput.render(gg, mouseX, mouseY, pt);
        modeBtn.render(gg, mouseX, mouseY, pt);

        if (access == ChatChannel.GroupAccess.ENCRYPTED) {
            gg.drawString(this.font, "密码", px + 12, py + 74, UIStyles.TEXT_SECONDARY);
            passwordInput.render(gg, mouseX, mouseY, pt);
        }

        gg.drawString(this.font, hintText, px + 12, py + 118, hintColor);

        createBtn.render(gg, mouseX, mouseY, pt);
        cancelBtn.render(gg, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (modeBtn.mouseClicked(mx, my, btn)) return true;
        if (createBtn.mouseClicked(mx, my, btn)) return true;
        if (cancelBtn.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.minecraft.setScreen(parentScreen); return true; }
        if (keyCode == 257 || keyCode == 335) { tryCreate(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

}
