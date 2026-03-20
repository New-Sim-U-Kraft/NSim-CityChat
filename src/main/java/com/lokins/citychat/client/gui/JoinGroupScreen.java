package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.GroupQueryResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 加入群组弹层：
 * - 本地匹配到（PUBLIC/已加入）：直接显示信息并可加入
 * - 本地未匹配：显示"验证"按钮，发服务端查询，验证通过后启用"加入"
 */
public class JoinGroupScreen extends Screen implements GroupQueryResultPacket.Listener {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 170;

    private final ChatScreen parentScreen;
    private final ChannelListWidget channelListWidget;

    private EditBox queryInput;
    private EditBox passwordInput;
    private ModButton joinButton;
    private ModButton verifyButton;
    private String hintText = "输入群名或#群号";
    private int hintColor = 0xFFBDBDBD;
    private String matchInfo = "";

    /** 服务端验证通过的信息 */
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

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        queryInput = new EditBox(this.font, panelX + 12, panelY + 24, PANEL_WIDTH - 24, 20, Component.literal("群名或#群号"));
        queryInput.setMaxLength(32);
        queryInput.setResponder(v -> onQueryChanged());
        this.addWidget(queryInput);

        passwordInput = new EditBox(this.font, panelX + 12, panelY + 56, PANEL_WIDTH - 24, 20, Component.literal("密码(非加密群可留空)"));
        passwordInput.setMaxLength(24);
        this.addWidget(passwordInput);

        this.setInitialFocus(queryInput);

        joinButton = this.addRenderableWidget(ModButton.modBuilder(Component.literal("加入"), btn -> tryJoin())
                .bounds(panelX + 12, panelY + 132, 82, 22)
                .build());

        verifyButton = this.addRenderableWidget(ModButton.modBuilder(Component.literal("验证"), btn -> doVerify())
                .bounds(panelX + 100, panelY + 132, 82, 22)
                .build());

        this.addRenderableWidget(ModButton.modBuilder(Component.literal("取消"), btn -> close())
                .bounds(panelX + PANEL_WIDTH - 94, panelY + 132, 82, 22)
                .build());

        joinButton.active = false;
        verifyButton.active = false;
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
            hintText = "输入群名或#群号";
            hintColor = 0xFFBDBDBD;
            joinButton.active = false;
            verifyButton.active = false;
            return;
        }

        // 尝试本地匹配
        ChatChannel channel = parentScreen.getChatManager().getChannelManager().findChannelByNameOrNumber(query);
        if (channel != null) {
            matchInfo = "匹配: #" + channel.getGroupNumber() + " " + channel.getDisplayName() + " [" + channel.getAccess().getDisplayName() + "]";
            if (channel.getAccess() == ChatChannel.GroupAccess.ENCRYPTED) {
                hintText = "加密群，请在下方输入密码";
                hintColor = 0xFFE0B15F;
            } else {
                hintText = "可直接加入";
                hintColor = 0xFF93D17C;
            }
            joinButton.active = true;
            verifyButton.active = false;
        } else {
            matchInfo = "";
            hintText = "本地未匹配，请点击验证查询服务端";
            hintColor = 0xFFBDBDBD;
            joinButton.active = false;
            verifyButton.active = true;
        }
    }

    private void doVerify() {
        String query = queryInput.getValue() == null ? "" : queryInput.getValue().trim();
        if (query.isEmpty()) return;
        hintText = "正在验证...";
        hintColor = 0xFFBDBDBD;
        verifyButton.active = false;
        ChatNetwork.requestGroupQuery(query);
    }

    /** 服务端查询结果回调 */
    @Override
    public void onQueryResult(boolean found, String query, String displayName, int groupNumber, String accessName) {
        if (!found) {
            matchInfo = "";
            hintText = "服务端未找到该群组";
            hintColor = 0xFFE07070;
            joinButton.active = false;
            verifyButton.active = true;
            return;
        }

        String accessDisplay;
        try {
            accessDisplay = ChatChannel.GroupAccess.valueOf(accessName).getDisplayName();
        } catch (Exception e) {
            accessDisplay = accessName;
        }

        matchInfo = "匹配: #" + groupNumber + " " + displayName + " [" + accessDisplay + "]";
        serverVerified = true;
        verifiedAccessName = accessName;

        if ("ENCRYPTED".equals(accessName)) {
            hintText = "加密群，请在下方输入密码后加入";
            hintColor = 0xFFE0B15F;
        } else {
            hintText = "验证通过，可加入";
            hintColor = 0xFF93D17C;
        }
        joinButton.active = true;
        verifyButton.active = false;
    }

    private void tryJoin() {
        String query = queryInput.getValue() == null ? "" : queryInput.getValue().trim();
        String password = passwordInput.getValue() == null ? "" : passwordInput.getValue().trim();

        if (query.isEmpty()) {
            hintText = "请输入群名或#群号";
            hintColor = 0xFFE07070;
            return;
        }

        // 客户端前置检查：加密群必须输入密码
        boolean isEncrypted = "ENCRYPTED".equals(verifiedAccessName);
        if (!isEncrypted) {
            // 本地匹配的也检查
            ChatChannel local = parentScreen.getChatManager().getChannelManager().findChannelByNameOrNumber(query);
            if (local != null && local.getAccess() == ChatChannel.GroupAccess.ENCRYPTED) {
                isEncrypted = true;
            }
        }
        if (isEncrypted && password.isEmpty()) {
            hintText = "加密群必须输入密码";
            hintColor = 0xFFE07070;
            return;
        }

        ChatNetwork.requestJoinGroup(query, password);
        close();
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

        guiGraphics.drawString(this.font, "加入群组", panelX + 12, panelY + 8, 0xFFFFFFFF);
        if (!matchInfo.isBlank()) {
            guiGraphics.drawString(this.font, matchInfo, panelX + 12, panelY + 88, 0xFF8CC0FF);
        }
        guiGraphics.drawString(this.font, hintText, panelX + 12, panelY + 102, hintColor);

        queryInput.render(guiGraphics, mouseX, mouseY, partialTick);
        passwordInput.render(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            close();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (joinButton.active) {
                tryJoin();
            } else if (verifyButton.active) {
                doVerify();
            }
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
