package com.lokins.citychat.client;

import com.lokins.citychat.CityChatMod;
import com.lokins.citychat.data.MessageAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD overlay: 右上角渲染消息通知弹窗，支持 action 按钮点击。
 */
@Mod.EventBusSubscriber(modid = CityChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NotificationOverlay {

    private static final int POPUP_WIDTH = 200;
    private static final int POPUP_HEIGHT_NORMAL = 30;
    private static final int POPUP_HEIGHT_WITH_ACTIONS = 44;
    private static final int PADDING = 4;
    private static final int GAP = 2;
    private static final int BUTTON_HEIGHT = 12;
    private static final int BUTTON_PADDING = 2;

    /** 当前帧渲染的按钮点击区域 */
    private static final List<ActionButtonRect> buttonRects = new ArrayList<>();

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cc_notifications", NotificationOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        List<NotificationManager.Notification> active = NotificationManager.getInstance().getActiveNotifications();
        buttonRects.clear();

        if (active.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // 如果有 Screen 打开（如聊天界面、暂停菜单），不渲染 overlay 按钮区域可能被遮挡
        // 但通知本身还是要显示的

        int x = screenWidth - POPUP_WIDTH - 5;
        int y = 5;

        for (NotificationManager.Notification notification : active) {
            float alpha = notification.getAlpha();
            if (alpha <= 0) continue;

            boolean hasActions = notification.hasActions();
            int popupHeight = hasActions ? POPUP_HEIGHT_WITH_ACTIONS : POPUP_HEIGHT_NORMAL;

            int alphaByte = (int) (alpha * 255) & 0xFF;
            int bgColor = (alphaByte << 24) | 0x222222;
            int borderColor = (alphaByte << 24) | 0x4A4A4A;

            // 背景
            guiGraphics.fill(x, y, x + POPUP_WIDTH, y + popupHeight, bgColor);
            // 边框
            guiGraphics.fill(x, y, x + POPUP_WIDTH, y + 1, borderColor);
            guiGraphics.fill(x, y + popupHeight - 1, x + POPUP_WIDTH, y + popupHeight, borderColor);
            guiGraphics.fill(x, y, x + 1, y + popupHeight, borderColor);
            guiGraphics.fill(x + POPUP_WIDTH - 1, y, x + POPUP_WIDTH, y + popupHeight, borderColor);

            // 文本
            int textColor = (alphaByte << 24) | 0xFFFFFF;
            int subColor = (alphaByte << 24) | 0xBDBDBD;
            String header = "[" + notification.channelName() + "]";
            String body = notification.senderName() + ": " + notification.preview();

            guiGraphics.drawString(mc.font, header, x + PADDING, y + PADDING, textColor);
            guiGraphics.drawString(mc.font, body, x + PADDING, y + PADDING + 12, subColor);

            // Action 按钮
            if (hasActions) {
                int btnX = x + PADDING;
                int btnY = y + PADDING + 24;

                for (MessageAction action : notification.actions()) {
                    int btnTextWidth = mc.font.width(action.label());
                    int btnWidth = btnTextWidth + BUTTON_PADDING * 2 + 4;
                    int btnColor = (alphaByte << 24) | (action.color() & 0xFFFFFF);
                    int btnBgColor = (alphaByte << 24) | 0x3A3A3A;

                    // 按钮背景
                    guiGraphics.fill(btnX, btnY, btnX + btnWidth, btnY + BUTTON_HEIGHT, btnBgColor);
                    // 按钮文字
                    guiGraphics.drawString(mc.font, action.label(), btnX + BUTTON_PADDING + 2, btnY + 2, btnColor);

                    // 记录按钮区域供点击检测
                    buttonRects.add(new ActionButtonRect(btnX, btnY, btnX + btnWidth, btnY + BUTTON_HEIGHT, action.command()));

                    btnX += btnWidth + 4;
                }
            }

            y += popupHeight + GAP;
        }
    }

    /**
     * 鼠标点击处理 — 注册在 FORGE 事件总线上。
     * 需要单独注册因为 MOD 总线和 FORGE 总线不同。
     */
    @Mod.EventBusSubscriber(modid = CityChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClickHandler {

        @SubscribeEvent
        public static void onMouseClick(InputEvent.MouseButton.Pre event) {
            if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            if (buttonRects.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            // Screen 打开时仍允许点击右上角弹窗按钮（弹窗渲染在 Screen 之上）

            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

            for (ActionButtonRect rect : buttonRects) {
                if (mouseX >= rect.x1 && mouseX <= rect.x2 && mouseY >= rect.y1 && mouseY <= rect.y2) {
                    // 执行命令
                    String command = rect.command;
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    mc.player.connection.sendCommand(command);

                    com.mojang.logging.LogUtils.getLogger().info("[CC客户端] 执行 action 命令: /{}", command);
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    private record ActionButtonRect(int x1, int y1, int x2, int y2, String command) {}
}
