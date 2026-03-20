package com.lokins.citychat.client;

import com.lokins.citychat.CityChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * HUD overlay: 右上角渲染消息通知弹窗。
 */
@Mod.EventBusSubscriber(modid = CityChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NotificationOverlay {

    private static final int POPUP_WIDTH = 200;
    private static final int POPUP_HEIGHT = 30;
    private static final int PADDING = 4;
    private static final int GAP = 2;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cc_notifications", NotificationOverlay::renderOverlay);
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        List<NotificationManager.Notification> active = NotificationManager.getInstance().getActiveNotifications();
        if (active.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int x = screenWidth - POPUP_WIDTH - 5;
        int y = 5;

        for (NotificationManager.Notification notification : active) {
            float alpha = notification.getAlpha();
            if (alpha <= 0) {
                continue;
            }

            int alphaByte = (int) (alpha * 255) & 0xFF;
            int bgColor = (alphaByte << 24) | 0x222222;
            int borderColor = (alphaByte << 24) | 0x4A4A4A;

            // 背景
            guiGraphics.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, bgColor);
            // 边框
            guiGraphics.fill(x, y, x + POPUP_WIDTH, y + 1, borderColor);
            guiGraphics.fill(x, y + POPUP_HEIGHT - 1, x + POPUP_WIDTH, y + POPUP_HEIGHT, borderColor);
            guiGraphics.fill(x, y, x + 1, y + POPUP_HEIGHT, borderColor);
            guiGraphics.fill(x + POPUP_WIDTH - 1, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, borderColor);

            // 文本
            int textColor = (alphaByte << 24) | 0xFFFFFF;
            int subColor = (alphaByte << 24) | 0xBDBDBD;
            String header = "[" + notification.channelName() + "]";
            String body = notification.senderName() + ": " + notification.preview();

            guiGraphics.drawString(mc.font, header, x + PADDING, y + PADDING, textColor);
            guiGraphics.drawString(mc.font, body, x + PADDING, y + PADDING + 12, subColor);

            y += POPUP_HEIGHT + GAP;
        }
    }
}
