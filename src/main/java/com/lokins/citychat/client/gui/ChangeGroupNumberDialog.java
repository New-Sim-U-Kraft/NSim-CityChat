package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 变更群号弹窗 —— 使用 LDLib 纹理渲染
 */
public class ChangeGroupNumberDialog extends Screen {
    private static final int DW = 260, DH = 130;

    private final GroupInfoScreen parentScreen;
    private final ChatChannel channel;
    private EditBox numberInput;
    private CCButton confirmBtn, cancelBtn;
    private String hint = "";

    public ChangeGroupNumberDialog(GroupInfoScreen parentScreen, ChatChannel channel) {
        super(Component.literal("变更群号"));
        this.parentScreen = parentScreen;
        this.channel = channel;
    }

    @Override
    protected void init() {
        int px = (width - DW) / 2, py = (height - DH) / 2;

        numberInput = new EditBox(this.font, px + 12, py + 46, DW - 24, 18, Component.literal("新群号"));
        numberInput.setMaxLength(8);
        numberInput.setValue(String.valueOf(channel.getGroupNumber()));
        this.addWidget(numberInput);
        this.setInitialFocus(numberInput);

        confirmBtn = new CCButton(px + 12, py + DH - 28, 110, 20, "确认", b -> confirm(), true);
        cancelBtn = new CCButton(px + DW - 122, py + DH - 28, 110, 20, "取消",
                b -> this.minecraft.setScreen(parentScreen));
    }

    private void confirm() {
        String value = numberInput.getValue() == null ? "" : numberInput.getValue().trim();
        if (!value.matches("\\d+")) { hint = "请输入有效数字"; return; }
        int newNumber = Integer.parseInt(value);
        if (newNumber <= 0) { hint = "群号必须大于 0"; return; }
        if (newNumber == channel.getGroupNumber()) { hint = "群号未变化"; return; }

        ChatNetwork.requestChangeGroupNumber(channel.getChannelId(), newNumber);
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

        gg.drawString(this.font, "变更群号", px + 12, py + 10, UIStyles.TEXT_WHITE);
        UIStyles.drawDivider(gg, px + 8, py + 22, DW - 16);
        gg.drawString(this.font, "当前: #" + channel.getGroupNumber(), px + 12, py + 24, UIStyles.TEXT_SECONDARY);
        gg.drawString(this.font, "新群号:", px + 12, py + 36, UIStyles.TEXT_SECONDARY);

        numberInput.render(gg, mouseX, mouseY, pt);

        if (!hint.isEmpty()) {
            gg.drawString(this.font, hint, px + 12, py + 70, UIStyles.COLOR_ERR);
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
        if (keyCode == 257 || keyCode == 335) { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }
}
