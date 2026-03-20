package com.lokins.citychat.client;

import com.lokins.citychat.CityChatMod;
import com.lokins.citychat.client.gui.ChatScreen;
import com.lokins.citychat.manager.ChatManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端事件处理 - 处理聊天屏幕的打开和快捷键
 */
public class ClientChatEvents {

    public static final KeyMapping OPEN_CHAT_KEY = new KeyMapping(
            "key.cc.open_chat",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            "key.categories.cc"
    );

    @Mod.EventBusSubscriber(modid = CityChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_CHAT_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = CityChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            while (OPEN_CHAT_KEY.consumeClick()) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen == null) {
                    var player = mc.player;
                    if (player != null) {
                        ChatManager chatManager = ChatManager.getInstance();
                        if (chatManager.getUser(player.getUUID()) == null) {
                            chatManager.registerUser(player.getUUID(), player.getName().getString());
                        }
                    }
                    mc.setScreen(new ChatScreen());
                }
            }
        }
    }
}
