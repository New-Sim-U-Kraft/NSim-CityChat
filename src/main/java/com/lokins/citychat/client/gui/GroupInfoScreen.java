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
 * 群组信息页面：左侧成员列表，右侧可滚动功能区。
 */
public class GroupInfoScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 200;
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;
    private static final int OUTER_MARGIN = 10;
    private static final int GUTTER = 8;
    private static final int SCROLLBAR_WIDTH = 3;

    private final ChatScreen parentScreen;
    private final String channelId;

    private UUID selectedMember;
    private String hint = "";

    /** 右侧按钮区滚动偏移（像素） */
    private int buttonScrollOffset = 0;
    /** 右侧按钮区域可用矩形 */
    private int panelX, panelY, panelW, panelH;
    /** 按钮布局总高度 */
    private int totalButtonsHeight;

    /** 维护按钮引用以便手动渲染和 scissor */
    private final List<ModButton> actionButtons = new ArrayList<>();

    public GroupInfoScreen(ChatScreen parentScreen, ChatChannel channel) {
        super(Component.literal("群组信息"));
        this.parentScreen = parentScreen;
        this.channelId = channel.getChannelId();
    }

    public ChatManager getChatManager() {
        return parentScreen.getChatManager();
    }

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

        if (!isNotification) {
            // 非通知频道：正常显示所有管理按钮
            addBtn("分享群组",     b -> shareGroup());

            if (isManager) {
                addBtn("切换群组类型", b -> switchAccess());
                addBtn("修改密码",     b -> changePassword());
                addBtn("踢出群组",     b -> kickMember());
            }

            if (isOwner) {
                addBtn("设为管理员",   b -> setAdmin(true));
                addBtn("取消管理员",   b -> setAdmin(false));
                addBtn("变更群号",     b -> changeGroupNumber());
                addBtn("转让群主",     b -> transferOwnership());
                addBtn("解散群组",     b -> dissolve());
            }

            if (ch != null && me != null && !isOwner) {
                addBtn("退出群组",     b -> leaveGroup());
            }
        }

        addBtn("返回",        b -> this.minecraft.setScreen(parentScreen));

        totalButtonsHeight = actionButtons.isEmpty() ? 0 : actionButtons.size() * (BUTTON_HEIGHT + GUTTER) - GUTTER;
        clampScroll();
    }

    private void addBtn(String label, ModButton.OnPress onPress) {
        ModButton btn = ModButton.modBuilder(Component.literal(label), onPress)
                .bounds(panelX + 4, 0, Math.min(BUTTON_WIDTH, panelW - SCROLLBAR_WIDTH - 12), BUTTON_HEIGHT)
                .build();
        actionButtons.add(btn);
        this.addRenderableOnly(btn);   // 不加入 children，由我们手动转发事件
    }

    /* ============ 渲染 ============ */

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg);

        ChatChannel ch = channel();
        if (ch == null) {
            gg.drawString(this.font, "群组不存在", OUTER_MARGIN, OUTER_MARGIN, 0xFFE07070);
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        renderHeaderBar(gg, ch);
        renderMemberList(gg, ch);
        renderButtonPanel(gg, mouseX, mouseY, partialTick);
        renderHint(gg, ch);

        // super.render 只绘制 renderableOnly 以外的 children（无），跳过即可
    }

    private void renderHeaderBar(GuiGraphics gg, ChatChannel ch) {
        int hx = OUTER_MARGIN;
        int hy = OUTER_MARGIN;
        int hw = this.width - OUTER_MARGIN * 2;
        int hh = 40;

        gg.fill(hx, hy, hx + hw, hy + hh, 0xFF2a2a2a);
        gg.drawString(this.font, ch.getDisplayName() + " #" + ch.getGroupNumber(), hx + 8, hy + 6, 0xFFFFFFFF);
        gg.drawString(this.font, "类型: " + ch.getAccess().getDisplayName() + "  人数: (" + ch.getMemberCount() + ")", hx + 8, hy + 20, 0xFFBDBDBD);
    }

    private void renderMemberList(GuiGraphics gg, ChatChannel ch) {
        int sx = OUTER_MARGIN;
        int sy = panelY;
        int sw = SIDEBAR_WIDTH;
        int sh = panelH;

        gg.fill(sx, sy, sx + sw, sy + sh, 0xFF1f1f1f);
        // 边框
        gg.fill(sx, sy, sx + sw, sy + 1, 0xFF4a4a4a);
        gg.fill(sx, sy + sh - 1, sx + sw, sy + sh, 0xFF4a4a4a);
        gg.fill(sx, sy, sx + 1, sy + sh, 0xFF4a4a4a);
        gg.fill(sx + sw - 1, sy, sx + sw, sy + sh, 0xFF4a4a4a);

        gg.drawString(this.font, "成员列表", sx + 8, sy + 8, 0xFFFFFFFF);

        List<UUID> list = getOrderedMembers(ch);
        int rowY = sy + 20;
        for (UUID id : list) {
            if (rowY > sy + sh - 10) break;

            String name = resolveMemberName(id);
            String role = ch.isOwner(id) ? "[群主]" : (ch.isAdmin(id) ? "[管理员]" : "[成员]");
            if (id.equals(selectedMember)) {
                gg.fill(sx + 2, rowY - 1, sx + sw - 2, rowY + 11, 0xFF3A4658);
            }
            gg.drawString(this.font, role + name, sx + 8, rowY, 0xFFDDDDDD);
            rowY += 14;
        }
    }

    private void renderButtonPanel(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        // 面板背景
        gg.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);
        // 边框
        gg.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF4a4a4a);
        gg.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF4a4a4a);
        gg.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF4a4a4a);
        gg.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF4a4a4a);

        // scissor 剪裁按钮到面板内
        gg.enableScissor(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1);

        int btnY = panelY + 6 - buttonScrollOffset;
        for (ModButton btn : actionButtons) {
            btn.setY(btnY);
            btn.setX(panelX + 4);
            // 只有可见时才渲染
            if (btnY + BUTTON_HEIGHT > panelY && btnY < panelY + panelH) {
                btn.render(gg, mouseX, mouseY, partialTick);
            }
            btnY += BUTTON_HEIGHT + GUTTER;
        }

        gg.disableScissor();

        // 滚动条
        renderScrollbar(gg);
    }

    private void renderScrollbar(GuiGraphics gg) {
        if (totalButtonsHeight + 12 <= panelH) return;

        int trackX = panelX + panelW - SCROLLBAR_WIDTH - 2;
        int trackY = panelY + 2;
        int trackH = panelH - 4;

        gg.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0xFF333333);

        int contentH = totalButtonsHeight + 12;
        float ratio = (float) panelH / contentH;
        int thumbH = Math.max(10, (int) (trackH * ratio));
        int maxScroll = contentH - panelH;
        float scrollRatio = maxScroll > 0 ? (float) buttonScrollOffset / maxScroll : 0;
        int thumbY = trackY + (int) ((trackH - thumbH) * scrollRatio);

        gg.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF888888);
    }

    private void renderHint(GuiGraphics gg, ChatChannel ch) {
        int hintX = panelX + 4;
        int hintY = panelY + panelH + 2;
        // 如果提示超出屏幕就放到面板底部内
        if (hintY + 10 > this.height) {
            hintY = this.height - 12;
        }
        gg.drawString(this.font, hint, hintX, hintY, canManage() ? 0xFF93D17C : 0xFFE07070);
    }

    /* ============ 输入 ============ */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 按钮点击（仅在面板区域内才响应）
        if (button == 0 && mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            for (ModButton btn : actionButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onPress();
                    return true;
                }
            }
        }

        // 成员列表选择
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
        // 右侧面板滚动
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

    /* ============ 操作 ============ */

    private ChatChannel channel() {
        return parentScreen.getChatManager().getChannelManager().getChannel(channelId);
    }

    private UUID currentPlayerId() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    private boolean canManage() {
        ChatChannel ch = channel();
        UUID me = currentPlayerId();
        return ch != null && me != null && ch.canManage(me);
    }

    private void switchAccess() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !canManage()) { hint = "你没有管理权限"; return; }
        this.minecraft.setScreen(new ChangeGroupTypeDialog(this, ch));
    }

    private void changePassword() {
        ChatChannel ch = channel(); UUID me = currentPlayerId();
        if (ch == null || me == null || !canManage()) { hint = "你没有管理权限"; return; }
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
        hint = "已踢出该成员";
        selectedMember = null;
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

    /* ============ 辅助 ============ */

    private List<UUID> getOrderedMembers(ChatChannel ch) {
        List<UUID> list = new ArrayList<>(ch.getMembers());
        list.sort((a, b) -> {
            int roleCompare = Integer.compare(roleRank(ch, a), roleRank(ch, b));
            return roleCompare != 0 ? roleCompare : resolveMemberName(a).compareToIgnoreCase(resolveMemberName(b));
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
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile() != null && info.getProfile().getName() != null) {
                return info.getProfile().getName();
            }
        }
        return id.toString().substring(0, 8);
    }
}
