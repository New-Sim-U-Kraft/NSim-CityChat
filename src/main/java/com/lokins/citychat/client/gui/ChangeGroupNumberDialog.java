package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 变更群号弹窗：输入新群号，服务端进行原子级重复验证。
 */
public class ChangeGroupNumberDialog extends Screen {
    private static final int DIALOG_WIDTH = 260;
    private static final int DIALOG_HEIGHT = 130;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox numberInput;
    private String hint = "";

    public ChangeGroupNumberDialog(GroupInfoScreen parentScreen, ChatChannel channel) {
        super(Component.literal("变更群号"));
        this.parentScreen = parentScreen;
        this.channel = channel;
    }

    @Override
    protected void init() {
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2;

        numberInput = new EditBox(this.font, x + 12, y + 46, DIALOG_WIDTH - 24, 18, Component.literal("新群号"));
        numberInput.setMaxLength(8);
        numberInput.setValue(String.valueOf(channel.getGroupNumber()));
        this.addWidget(numberInput);
        this.setInitialFocus(numberInput);

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("确认"), b -> confirm())
                .bounds(x + 12, y + DIALOG_HEIGHT - 28, 110, 20)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("取消"), b -> this.minecraft.setScreen(parentScreen))
                .bounds(x + DIALOG_WIDTH - 122, y + DIALOG_HEIGHT - 28, 110, 20)
                .build());
    }

    private void confirm() {
        String value = numberInput.getValue() == null ? "" : numberInput.getValue().trim();
        if (!value.matches("\\d+")) {
            hint = "请输入有效数字";
            return;
        }
        int newNumber = Integer.parseInt(value);
        if (newNumber <= 0) {
            hint = "群号必须大于 0";
            return;
        }
        if (newNumber == channel.getGroupNumber()) {
            hint = "群号未变化";
            return;
        }

        ChatNetwork.requestChangeGroupNumber(channel.getChannelId(), newNumber);
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
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

        gg.drawString(this.font, "变更群号", x + 12, y + 10, 0xFFFFFFFF);
        gg.drawString(this.font, "当前: #" + channel.getGroupNumber(), x + 12, y + 24, 0xFFBDBDBD);
        gg.drawString(this.font, "新群号:", x + 12, y + 36, 0xFFBDBDBD);

        numberInput.render(gg, mouseX, mouseY, partialTick);

        if (!hint.isEmpty()) {
            gg.drawString(this.font, hint, x + 12, y + 70, 0xFFE07070);
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }
}
