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
 * 切换群组类型弹窗
 * 如果要改为加密，需要输入密码
 */
public class ChangeGroupTypeDialog extends Screen {
    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 180;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox passwordInput;
    private String hint = "";
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
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2;

        passwordInput = new EditBox(this.font, x + 12, y + 92, DIALOG_WIDTH - 24, 18, Component.literal("密码"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);

        passwordInput.setVisible(requiresPasswordInput());
        if (requiresPasswordInput()) {
            this.setInitialFocus(passwordInput);
        }

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("公开"), b -> selectedAccess = ChatChannel.GroupAccess.PUBLIC)
                .bounds(x + 12, y + 52, 88, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("普通"), b -> selectedAccess = ChatChannel.GroupAccess.NORMAL)
                .bounds(x + 106, y + 52, 88, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("加密"), b -> {
                    selectedAccess = ChatChannel.GroupAccess.ENCRYPTED;
                    passwordInput.setFocused(true);
                })
                .bounds(x + 200, y + 52, 88, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("确认"), b -> confirm())
                .bounds(x + 12, y + DIALOG_HEIGHT - 28, 130, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("取消"), b -> this.minecraft.setScreen(parentScreen))
                .bounds(x + DIALOG_WIDTH - 142, y + DIALOG_HEIGHT - 28, 130, 20)
                .build());
    }

    private boolean requiresPasswordInput() {
        return selectedAccess == ChatChannel.GroupAccess.ENCRYPTED
                && currentAccess != ChatChannel.GroupAccess.ENCRYPTED;
    }

    private void confirm() {
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        if (me == null) {
            hint = "出错了";
            return;
        }

        if (selectedAccess == currentAccess) {
            hint = "类型未变化";
            return;
        }

        String password = "";
        if (requiresPasswordInput()) {
            String value = passwordInput.getValue();
            if (value == null || value.trim().length() < 4) {
                hint = "密码至少4位";
                return;
            }
            password = value.trim();
        }

        ChatNetwork.requestChangeAccess(channel.getChannelId(), selectedAccess, password);

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

        // 绘制背景框
        gg.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xEE1f1f1f);
        gg.fill(x, y, x + DIALOG_WIDTH, y + 1, 0xFF4a4a4a);
        gg.fill(x, y + DIALOG_HEIGHT - 1, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF4a4a4a);
        gg.fill(x, y, x + 1, y + DIALOG_HEIGHT, 0xFF4a4a4a);
        gg.fill(x + DIALOG_WIDTH - 1, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF4a4a4a);

        gg.drawString(this.font, "切换群组类型", x + 12, y + 10, 0xFFFFFFFF);
        gg.drawString(this.font, "当前: " + currentAccess.getDisplayName(), x + 12, y + 24, 0xFFBDBDBD);
        gg.drawString(this.font, "切换至: " + selectedAccess.getDisplayName(), x + 12, y + 38, 0xFFBDBDBD);

        passwordInput.setVisible(requiresPasswordInput());
        if (requiresPasswordInput()) {
            gg.drawString(this.font, "请输入密码:", x + 12, y + 78, 0xFFFFFFFF);
            passwordInput.render(gg, mouseX, mouseY, partialTick);
        }

        gg.drawString(this.font, hint, x + 12, y + DIALOG_HEIGHT - 40, hint.contains("失败") || hint.contains("至少") ? 0xFFE07070 : 0xFF93D17C);

        super.render(gg, mouseX, mouseY, partialTick);
    }
}
