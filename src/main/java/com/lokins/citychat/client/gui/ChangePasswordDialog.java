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
 * 修改密码弹窗
 * 仅加密群可修改
 */
public class ChangePasswordDialog extends Screen {
    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 140;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox passwordInput;
    private String hint = "";

    public ChangePasswordDialog(GroupInfoScreen parentScreen, ChatChannel channel) {
        super(Component.literal("修改密码"));
        this.parentScreen = parentScreen;
        this.channel = channel;
    }

    @Override
    protected void init() {
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2;

        passwordInput = new EditBox(this.font, x + 12, y + 50, DIALOG_WIDTH - 24, 18, Component.literal("新密码"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);
        this.setInitialFocus(passwordInput);

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("确认"), b -> confirm())
                .bounds(x + 12, y + DIALOG_HEIGHT - 28, 130, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("取消"), b -> this.minecraft.setScreen(parentScreen))
                .bounds(x + DIALOG_WIDTH - 142, y + DIALOG_HEIGHT - 28, 130, 20)
                .build());
    }

    private void confirm() {
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        if (me == null) {
            hint = "出错了";
            return;
        }

        String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();
        if (password.length() < 4) {
            hint = "密码至少4位";
            return;
        }

        ChatNetwork.requestChangePassword(channel.getChannelId(), password);
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg);

        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2;

        gg.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xEE1f1f1f);
        gg.fill(x, y, x + DIALOG_WIDTH, y + 1, 0xFF4a4a4a);
        gg.fill(x, y + DIALOG_HEIGHT - 1, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF4a4a4a);
        gg.fill(x, y, x + 1, y + DIALOG_HEIGHT, 0xFF4a4a4a);
        gg.fill(x + DIALOG_WIDTH - 1, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF4a4a4a);

        gg.drawString(this.font, "修改密码", x + 12, y + 10, 0xFFFFFFFF);
        gg.drawString(this.font, "新密码:", x + 12, y + 36, 0xFFBDBDBD);

        passwordInput.render(gg, mouseX, mouseY, partialTick);

        gg.drawString(this.font, hint, x + 12, y + DIALOG_HEIGHT - 10, hint.contains("失败") || hint.contains("至少") ? 0xFFE07070 : 0xFF93D17C);

        super.render(gg, mouseX, mouseY, partialTick);
    }
}
