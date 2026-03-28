package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.GroupQueryResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 加入群组弹层 —— 使用 LDLib 纹理渲染
 */
public class JoinGroupScreen extends Screen implements GroupQueryResultPacket.Listener {
    private static final int DW = 300, DH = 170;

    private final ChatScreen parentScreen;
    private final ChannelListWidget channelListWidget;

    private EditBox queryInput;
    private EditBox passwordInput;
    private CCButton joinBtn, verifyBtn, cancelBtn;
    private String hintText = "输入群名或#群号";
    private int hintColor = UIStyles.TEXT_SECONDARY;
    private String matchInfo = "";

    private boolean serverVerified = false;
    private String verifiedAccessName = "";

    public JoinGroupScreen(ChatScreen parentScreen, ChannelListWidget channelListWidget) {
        super(Component.literal("加入群组"));
        this.parentScreen = parentScreen;
        this.channelListWidget = channelListWidget;
    }

    @Override
    protected void init() {
        GroupQueryResultPacket.setListener(this);

        int px = (this.width - DW) / 2, py = (this.height - DH) / 2;

        queryInput = new EditBox(this.font, px + 12, py + 24, DW - 24, 20, Component.literal("群名或#群号"));
        queryInput.setMaxLength(32);
        queryInput.setResponder(v -> onQueryChanged());
        this.addWidget(queryInput);

        passwordInput = new EditBox(this.font, px + 12, py + 56, DW - 24, 20, Component.literal("密码(非加密群可留空)"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);

        this.setInitialFocus(queryInput);

        joinBtn = new CCButton(px + 12, py + 132, 82, 22, "加入", b -> tryJoin(), true);
        verifyBtn = new CCButton(px + 100, py + 132, 82, 22, "验证", b -> doVerify());
        cancelBtn = new CCButton(px + DW - 94, py + 132, 82, 22, "取消", b -> close());

        joinBtn.setActive(false);
        verifyBtn.setActive(false);
        onQueryChanged();
    }

    private void close() {
        GroupQueryResultPacket.setListener(null);
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public void removed() {
        GroupQueryResultPacket.setListener(null);
        super.removed();
    }

    private void onQueryChanged() {
        serverVerified = false;
        verifiedAccessName = "";
        String query = queryInput.getValue() == null ? "" : queryInput.getValue().trim();

        if (query.isEmpty()) {
            matchInfo = "";
            hintText = "输入群名或#群号"; hintColor = UIStyles.TEXT_SECONDARY;
            joinBtn.setActive(false); verifyBtn.setActive(false);
            return;
        }

        ChatChannel channel = parentScreen.getChatManager().getChannelManager().findChannelByNameOrNumber(query);
        if (channel != null) {
            matchInfo = "匹配: #" + channel.getGroupNumber() + " " + channel.getDisplayName() + " [" + channel.getAccess().getDisplayName() + "]";
            if (channel.getAccess() == ChatChannel.GroupAccess.ENCRYPTED) {
                hintText = "加密群，请在下方输入密码"; hintColor = UIStyles.COLOR_WARN;
            } else {
                hintText = "可直接加入"; hintColor = UIStyles.COLOR_OK;
            }
            joinBtn.setActive(true); verifyBtn.setActive(false);
        } else {
            matchInfo = "";
            hintText = "本地未匹配，请点击验证查询服务端"; hintColor = UIStyles.TEXT_SECONDARY;
            joinBtn.setActive(false); verifyBtn.setActive(true);
        }
    }

    private void doVerify() {
        String query = queryInput.getValue() == null ? "" : queryInput.getValue().trim();
        if (query.isEmpty()) return;
        hintText = "正在验证..."; hintColor = UIStyles.TEXT_SECONDARY;
        verifyBtn.setActive(false);
        ChatNetwork.requestGroupQuery(query);
    }

    @Override
    public void onQueryResult(boolean found, String query, String displayName, int groupNumber, String accessName) {
        if (!found) {
            matchInfo = "";
            hintText = "服务端未找到该群组"; hintColor = UIStyles.COLOR_ERR;
            joinBtn.setActive(false); verifyBtn.setActive(true);
            return;
        }

        String accessDisplay;
        try { accessDisplay = ChatChannel.GroupAccess.valueOf(accessName).getDisplayName(); }
        catch (Exception e) { accessDisplay = accessName; }

        matchInfo = "匹配: #" + groupNumber + " " + displayName + " [" + accessDisplay + "]";
        serverVerified = true;
        verifiedAccessName = accessName;

        if ("ENCRYPTED".equals(accessName)) {
            hintText = "加密群，请在下方输入密码后加入"; hintColor = UIStyles.COLOR_WARN;
        } else {
            hintText = "验证通过，可加入"; hintColor = UIStyles.COLOR_OK;
        }
        joinBtn.setActive(true); verifyBtn.setActive(false);
    }

    private void tryJoin() {
        String query = queryInput.getValue() == null ? "" : queryInput.getValue().trim();
        String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();
        if (query.isEmpty()) { hintText = "请输入群名或#群号"; hintColor = UIStyles.COLOR_ERR; return; }

        boolean isEncrypted = "ENCRYPTED".equals(verifiedAccessName);
        if (!isEncrypted) {
            ChatChannel local = parentScreen.getChatManager().getChannelManager().findChannelByNameOrNumber(query);
            if (local != null && local.getAccess() == ChatChannel.GroupAccess.ENCRYPTED) isEncrypted = true;
        }
        if (isEncrypted && password.isEmpty()) {
            hintText = "加密群必须输入密码"; hintColor = UIStyles.COLOR_ERR; return;
        }

        ChatNetwork.requestJoinGroup(query, password);
        close();
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
        gg.drawString(this.font, "加入群组", px + 12, py + 8, UIStyles.TEXT_WHITE);
        UIStyles.drawDivider(gg, px + 8, py + 22, DW - 16);

        queryInput.render(gg, mouseX, mouseY, pt);
        passwordInput.render(gg, mouseX, mouseY, pt);

        if (!matchInfo.isBlank())
            gg.drawString(this.font, matchInfo, px + 12, py + 88, UIStyles.COLOR_INFO);
        gg.drawString(this.font, hintText, px + 12, py + 102, hintColor);

        joinBtn.render(gg, mouseX, mouseY, pt);
        verifyBtn.render(gg, mouseX, mouseY, pt);
        cancelBtn.render(gg, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (joinBtn.mouseClicked(mx, my, btn)) return true;
        if (verifyBtn.mouseClicked(mx, my, btn)) return true;
        if (cancelBtn.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        if (keyCode == 257 || keyCode == 335) {
            if (joinBtn.isActive()) tryJoin();
            else if (verifyBtn.isActive()) doVerify();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }
}
