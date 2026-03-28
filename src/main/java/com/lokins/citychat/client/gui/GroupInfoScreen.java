package com.lokins.citychat.client.gui;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.network.ChatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 群组信息页面 —— 使用 LDLib 纹理渲染
 */
public class GroupInfoScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int OUTER_MARGIN = 10;
    private static final int GUTTER = 8;
    private static final int SCROLLBAR_WIDTH = 3;

    private final ChatScreen parentScreen;
    private final String channelId;

    private UUID selectedMember;
    private String hint = "";

    private int buttonScrollOffset = 0;
    private int panelX, panelY, panelW, panelH;
    private int totalButtonsHeight;

    private final List<CCButton> actionButtons = new ArrayList<>();

    public GroupInfoScreen(ChatScreen parentScreen, ChatChannel channel) {
        super(Component.literal("群组信息"));
        this.parentScreen = parentScreen;
        this.channelId = channel.getChannelId();
    }

    public ChatManager getChatManager() { return parentScreen.getChatManager(); }

    @Override
    protected void init() {
        actionButtons.clear();
        int headerHeight = 40;
        panelX = OUTER_MARGIN + SIDEBAR_WIDTH + GUTTER;
        panelY = OUTER_MARGIN + headerHeight;
        panelW = this.width - panelX - OUTER_MARGIN;
        panelH = this.height - panelY - OUTER_MARGIN;

        ChatChannel ch = channel();
        UUID me = currentPlayerId();
        boolean isOwner = ch != null && me != null && ch.isOwner(me);
        boolean isManager = ch != null && me != null && ch.canManage(me);
        boolean isNotification = ch != null && ch.isNotificationChannel();

        int btnW = Math.min(140, panelW - SCROLLBAR_WIDTH - 12);

        if (!isNotification) {
            addBtn("分享群组", btnW, b -> shareGroup());
            if (isManager) {
                addBtn("切换群组类型", btnW, b -> switchAccess());
                addBtn("修改密码", btnW, b -> changePassword());
                addBtn("踢出群组", btnW, b -> kickMember());
            }
            if (isOwner) {
                addBtn("设为管理员", btnW, b -> setAdmin(true));
                addBtn("取消管理员", btnW, b -> setAdmin(false));
                addBtn("变更群号", btnW, b -> changeGroupNumber());
                addBtn("转让群主", btnW, b -> transferOwnership());
                addBtn("解散群组", btnW, b -> dissolve());
            }
            if (ch != null && me != null && !isOwner) {
                addBtn("退出群组", btnW, b -> leaveGroup());
            }
        }
        addBtn("返回", btnW, b -> this.minecraft.setScreen(parentScreen));

        totalButtonsHeight = actionButtons.isEmpty() ? 0 : actionButtons.size() * (BUTTON_HEIGHT + GUTTER) - GUTTER;
        clampScroll();
    }

    private void addBtn(String label, int btnW, java.util.function.Consumer<CCButton> onPress) {
        actionButtons.add(new CCButton(panelX + 4, 0, btnW, BUTTON_HEIGHT, label, onPress));
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg);

        ChatChannel ch = channel();
        if (ch == null) {
            gg.drawString(this.font, "群组不存在", OUTER_MARGIN, OUTER_MARGIN, UIStyles.COLOR_ERR);
            return;
        }

        renderHeaderBar(gg, ch);
        renderMemberList(gg, ch);
        renderButtonPanel(gg, mouseX, mouseY, pt);
        renderHint(gg);
    }

    private void renderHeaderBar(GuiGraphics gg, ChatChannel ch) {
        int hx = OUTER_MARGIN, hy = OUTER_MARGIN;
        int hw = this.width - OUTER_MARGIN * 2, hh = 40;

        gg.fill(hx, hy, hx + hw, hy + hh, UIStyles.BG_HEADER);
        gg.drawString(this.font, ch.getDisplayName() + " #" + ch.getGroupNumber(), hx + 8, hy + 6, UIStyles.TEXT_WHITE);
        gg.drawString(this.font, "类型: " + ch.getAccess().getDisplayName() + "  人数: (" + ch.getMemberCount() + ")",
                hx + 8, hy + 20, UIStyles.TEXT_SECONDARY);
        UIStyles.drawDivider(gg, hx, hy + hh, hw);
    }

    private void renderMemberList(GuiGraphics gg, ChatChannel ch) {
        int sx = OUTER_MARGIN, sy = panelY, sw = SIDEBAR_WIDTH, sh = panelH;

        gg.fill(sx, sy, sx + sw, sy + sh, UIStyles.BG_BASE);
        gg.drawString(this.font, "成员列表", sx + 8, sy + 8, UIStyles.TEXT_WHITE);

        List<UUID> list = getOrderedMembers(ch);
        int rowY = sy + 20;
        for (UUID id : list) {
            if (rowY > sy + sh - 10) break;
            String name = resolveMemberName(id);
            String role = ch.isOwner(id) ? "[群主]" : (ch.isAdmin(id) ? "[管理员]" : "[成员]");
            if (id.equals(selectedMember)) {
                gg.fill(sx + 3, rowY - 1, sx + sw - 3, rowY + 11, UIStyles.ACCENT_MUTED);
            }
            gg.drawString(this.font, role + name, sx + 8, rowY, 0xFFDDDDDD);
            rowY += 14;
        }
    }

    private void renderButtonPanel(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        gg.fill(panelX, panelY, panelX + panelW, panelY + panelH, UIStyles.BG_PRIMARY);

        gg.enableScissor(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1);

        int btnY = panelY + 6 - buttonScrollOffset;
        for (CCButton btn : actionButtons) {
            btn.setY(btnY);
            btn.setX(panelX + 4);
            if (btnY + BUTTON_HEIGHT > panelY && btnY < panelY + panelH) {
                btn.render(gg, mouseX, mouseY, pt);
            }
            btnY += BUTTON_HEIGHT + GUTTER;
        }

        gg.disableScissor();

        // 圆角滚动条
        if (totalButtonsHeight + 12 > panelH) {
            int trackX = panelX + panelW - SCROLLBAR_WIDTH - 2;
            int trackY = panelY + 2, trackH = panelH - 4;

            int contentH = totalButtonsHeight + 12;
            float ratio = (float) panelH / contentH;
            int thumbH = Math.max(12, (int) (trackH * ratio));
            int maxScroll = contentH - panelH;
            float scrollRatio = maxScroll > 0 ? (float) buttonScrollOffset / maxScroll : 0;
            int thumbY = trackY + (int) ((trackH - thumbH) * scrollRatio);
            UIStyles.drawScrollbar(gg, trackX, trackY, SCROLLBAR_WIDTH, trackH, thumbY, thumbH, false);
        }
    }

    private void renderHint(GuiGraphics gg) {
        int hintX = panelX + 4;
        int hintY = panelY + panelH + 2;
        if (hintY + 10 > this.height) hintY = this.height - 12;
        gg.drawString(this.font, hint, hintX, hintY, canManage() ? UIStyles.COLOR_OK : UIStyles.COLOR_ERR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            for (CCButton btn : actionButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        ChatChannel ch = channel();
        if (ch != null) {
            List<UUID> list = getOrderedMembers(ch);
            int rowY = panelY + 20;
            for (UUID id : list) {
                if (mouseX >= OUTER_MARGIN && mouseX <= OUTER_MARGIN + SIDEBAR_WIDTH
                        && mouseY >= rowY && mouseY <= rowY + 12) {
                    selectedMember = id;
                    return true;
                }
                rowY += 14;
                if (rowY > panelY + panelH) break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            buttonScrollOffset -= (int) (delta * 10);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, totalButtonsHeight + 12 - panelH);
        buttonScrollOffset = Math.max(0, Math.min(buttonScrollOffset, maxScroll));
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

    // ── 操作方法 ──

    private ChatChannel channel() { return parentScreen.getChatManager().getChannelManager().getChannel(channelId); }
    private UUID currentPlayerId() { var p = Minecraft.getInstance().player; return p == null ? null : p.getUUID(); }
    private boolean canManage() { ChatChannel ch = channel(); UUID me = currentPlayerId(); return ch != null && me != null && ch.canManage(me); }

    private void switchAccess() {
        ChatChannel ch = channel(); if (ch == null || !canManage()) { hint = "你没有管理权限"; return; }
        this.minecraft.setScreen(new ChangeGroupTypeDialog(this, ch));
    }
    private void changePassword() {
        ChatChannel ch = channel(); if (ch == null || !canManage()) { hint = "你没有管理权限"; return; }
        this.minecraft.setScreen(new ChangePasswordDialog(this, ch));
    }
    private void setAdmin(boolean admin) {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !ch.isOwner(me)) { hint = "仅群主可操作"; return; }
        if (selectedMember == null) { hint = "请先在左侧选择一名成员"; return; }
        if (ch.isOwner(selectedMember)) { hint = "不能对群主执行此操作"; return; }
        if (admin && ch.isAdmin(selectedMember)) { hint = "该成员已经是管理员"; return; }
        if (!admin && !ch.isAdmin(selectedMember)) { hint = "该成员不是管理员"; return; }
        ChatNetwork.requestSetAdmin(ch.getChannelId(), selectedMember, admin);
        hint = admin ? "已设为管理员" : "已取消管理员";
    }
    private void changeGroupNumber() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !ch.isOwner(me)) { hint = "仅群主可变更群号"; return; }
        this.minecraft.setScreen(new ChangeGroupNumberDialog(this, ch));
    }
    private void kickMember() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !canManage()) { hint = "你没有管理权限"; return; }
        if (selectedMember == null) { hint = "请先在左侧选择一名成员"; return; }
        if (ch.isOwner(selectedMember)) { hint = "不能踢出群主"; return; }
        if (selectedMember.equals(me)) { hint = "不能踢出自己"; return; }
        if (ch.isAdmin(selectedMember) && !ch.isOwner(me)) { hint = "管理员不能踢出其他管理员"; return; }
        ChatNetwork.requestKickMember(ch.getChannelId(), selectedMember);
        hint = "已踢出该成员"; selectedMember = null;
    }
    private void transferOwnership() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !ch.isOwner(me)) { hint = "仅群主可转让"; return; }
        if (selectedMember == null) { hint = "请先在左侧选择一名成员"; return; }
        if (selectedMember.equals(me)) { hint = "不能转让给自己"; return; }
        ChatNetwork.requestTransferOwnership(ch.getChannelId(), selectedMember);
        hint = "已转让群主";
    }
    private void shareGroup() {
        ChatChannel ch = channel();
        if (ch == null) { hint = "群组不存在"; return; }
        String shareText = "#" + ch.getGroupNumber() + " " + ch.getDisplayName();
        Minecraft.getInstance().keyboardHandler.setClipboard(shareText);
        hint = "已复制: " + shareText;
    }
    private void leaveGroup() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null) { hint = "操作无效"; return; }
        ChatNetwork.requestLeaveGroup(ch.getChannelId());
        parentScreen.setCurrentChannelId(null);
        this.minecraft.setScreen(parentScreen);
    }
    private void dissolve() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !ch.isOwner(me)) { hint = "仅群主可解散"; return; }
        ChatNetwork.requestDissolveGroup(ch.getChannelId());
        parentScreen.setCurrentChannelId(null);
        this.minecraft.setScreen(parentScreen);
    }

    // ── 辅助 ──

    private List<UUID> getOrderedMembers(ChatChannel ch) {
        List<UUID> list = new ArrayList<>(ch.getMembers());
        list.sort((a, b) -> {
            int rc = Integer.compare(roleRank(ch, a), roleRank(ch, b));
            return rc != 0 ? rc : resolveMemberName(a).compareToIgnoreCase(resolveMemberName(b));
        });
        return list;
    }
    private int roleRank(ChatChannel ch, UUID id) {
        if (ch.isOwner(id)) return 0;
        if (ch.isAdmin(id)) return 1;
        return 2;
    }
    private String resolveMemberName(UUID id) {
        var user = parentScreen.getChatManager().getUser(id);
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile() != null && info.getProfile().getName() != null) return info.getProfile().getName();
        }
        return id.toString().substring(0, 8);
    }
}
