package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 创建群组弹层：支持公开/普通/加密群组。
 */
public class CreateGroupScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 176;

    private final ChatScreen parentScreen;
    private final ChannelListWidget channelListWidget;

    private EditBox nameInput;
    private EditBox passwordInput;
    private ModButton createButton;

    private ChatChannel.GroupAccess access = ChatChannel.GroupAccess.NORMAL;
    private String hintText = "支持中文/英文/数字/空格/_/-，长度 1-24";
    private int hintColor = 0xFFBDBDBD;

    public CreateGroupScreen(ChatScreen parentScreen, ChannelListWidget channelListWidget) {
        super(Component.literal("创建群组"));
        this.parentScreen = parentScreen;
        this.channelListWidget = channelListWidget;
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        this.nameInput = new EditBox(this.font, panelX + 12, panelY + 24, PANEL_WIDTH - 24, 20, Component.literal("群组名称"));
        this.nameInput.setMaxLength(24);
        this.nameInput.setResponder(value -> validateForm());
        this.addWidget(this.nameInput);

        this.passwordInput = new EditBox(this.font, panelX + 12, panelY + 84, PANEL_WIDTH - 24, 20, Component.literal("加密群密码"));
        this.passwordInput.setMaxLength(24);
        this.passwordInput.setResponder(value -> validateForm());
        this.addWidget(this.passwordInput);

        this.setInitialFocus(this.nameInput);

        this.addRenderableWidget(ModButton.modBuilder(Component.literal(modeLabel()), btn -> {
            switchAccessMode();
            btn.setMessage(Component.literal(modeLabel()));
            validateForm();
        }).bounds(panelX + 12, panelY + 52, 132, 20).build());

        this.createButton = this.addRenderableWidget(ModButton.modBuilder(Component.literal("创建"), btn -> tryCreate())
                .bounds(panelX + 12, panelY + 140, 112, 22)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("取消"), btn -> this.minecraft.setScreen(parentScreen))
                .bounds(panelX + PANEL_WIDTH - 124, panelY + 140, 112, 22)
                .build());

        validateForm();
    }

    private void switchAccessMode() {
        if (access == ChatChannel.GroupAccess.PUBLIC) {
            access = ChatChannel.GroupAccess.NORMAL;
        } else if (access == ChatChannel.GroupAccess.NORMAL) {
            access = ChatChannel.GroupAccess.ENCRYPTED;
        } else {
            access = ChatChannel.GroupAccess.PUBLIC;
        }
    }

    private String modeLabel() {
        return "模式: " + access.getDisplayName();
    }

    private void tryCreate() {
        if (!validateForm()) {
            return;
        }

        String password = access == ChatChannel.GroupAccess.ENCRYPTED ? passwordInput.getValue().trim() : "";
        channelListWidget.createGroupForCurrentPlayer(nameInput.getValue().trim(), access, password);
        this.minecraft.setScreen(parentScreen);
    }

    private boolean validateForm() {
        String name = nameInput.getValue() == null ? "" : nameInput.getValue().trim();

        if (name.isEmpty()) {
            hintText = "群组名不能为空";
            hintColor = 0xFFE07070;
            createButton.active = false;
            return false;
        }

        if (name.length() > 24) {
            hintText = "群组名长度不能超过 24";
            hintColor = 0xFFE07070;
            createButton.active = false;
            return false;
        }

        if (!name.matches("[\\u4e00-\\u9fa5A-Za-z0-9_\\- ]+")) {
            hintText = "仅允许中文/英文/数字/空格/_/-";
            hintColor = 0xFFE07070;
            createButton.active = false;
            return false;
        }

        boolean duplicate = parentScreen.getChatManager().getChannelManager().getAllActiveChannels().stream()
                .anyMatch(ch -> ch.getDisplayName().equalsIgnoreCase(name));
        if (duplicate) {
            hintText = "群组名已存在，请换一个";
            hintColor = 0xFFE0B15F;
            createButton.active = false;
            return false;
        }

        if (access == ChatChannel.GroupAccess.ENCRYPTED) {
            String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();
            if (password.length() < 4) {
                hintText = "加密群密码至少 4 位";
                hintColor = 0xFFE07070;
                createButton.active = false;
                return false;
            }
        }

        hintText = "信息有效，可创建";
        hintColor = 0xFF93D17C;
        createButton.active = true;
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE1f1f1f);
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFF4a4a4a);
        guiGraphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF4a4a4a);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, 0xFF4a4a4a);
        guiGraphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF4a4a4a);

        guiGraphics.drawString(this.font, "创建群组", panelX + 12, panelY + 8, 0xFFFFFFFF);

        nameInput.render(guiGraphics, mouseX, mouseY, partialTick);

        if (access == ChatChannel.GroupAccess.ENCRYPTED) {
            guiGraphics.drawString(this.font, "密码", panelX + 12, panelY + 74, 0xFFBDBDBD);
            passwordInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.drawString(this.font, hintText, panelX + 12, panelY + 118, hintColor);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            tryCreate();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
