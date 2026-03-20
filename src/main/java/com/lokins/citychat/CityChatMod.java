package com.lokins.citychat;

import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.network.ChatNetwork;
import com.lokins.citychat.network.ChannelSnapshotPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

@Mod(CityChatMod.MODID)
public class CityChatMod {
    public static final String MODID = "cc";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CityChatMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
        }

        MinecraftForge.EVENT_BUS.register(this);
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        ChatNetwork.register();
        ChatManager chatManager = ChatManager.getInstance();
        LOGGER.info("CityChat Mod initialized successfully!");
        LOGGER.info("Chat system loaded with {} channels", chatManager.getChannelManager().getChannelCount());
    }

    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        LOGGER.info("CityChat Client initialization complete");
    }

    /**
     * 玩家登录时注册用户并同步频道快照。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ChatManager chatManager = ChatManager.getInstance();
            chatManager.registerUser(serverPlayer.getUUID(), serverPlayer.getName().getString());

            // 发送该玩家可见的频道快照
            ChannelSnapshotPacket snapshot = ChannelSnapshotPacket.forPlayer(serverPlayer.getUUID());
            ChatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), snapshot);
            LOGGER.info("Sent channel snapshot to player {}", serverPlayer.getName().getString());
        }
    }

    /**
     * 玩家登出时清理用户数据。
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ChatManager.getInstance().unregisterUser(serverPlayer.getUUID());
            LOGGER.info("Cleaned up chat data for player {}", serverPlayer.getName().getString());
        }
    }

    /**
     * 服务器关闭时强制保存所有数据。
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ChatManager.getInstance().getChannelManager().saveAll();
        LOGGER.info("CityChat data saved on server shutdown");
    }
}