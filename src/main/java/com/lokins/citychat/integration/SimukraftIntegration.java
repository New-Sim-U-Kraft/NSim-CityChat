package com.lokins.citychat.integration;

import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.manager.ChannelVisibilityFilter;
import com.mojang.logging.LogUtils;
import com.xiaoliang.simukraft.event.CityDataChangedEvent;
import com.xiaoliang.simukraft.notification.NotificationServiceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.*;

/**
 * simukraft 集成激活/停用。
 * 此类直接引用 simukraft 的类，仅在 SimukraftDetector.isAvailable() 为 true 时加载。
 */
public class SimukraftIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static CityChatNotificationService service;
    private static ChannelVisibilityFilter visibilityFilter;
    private static CityDataEventHandler cityDataEventHandler;

    public static void activate() {
        try {
            service = new CityChatNotificationService();
            NotificationServiceManager.registerService(service);
            LOGGER.info("已注册通知服务: CityChatNotificationService");

            // 注册通知频道可见性过滤器
            visibilityFilter = new NotificationChannelVisibilityFilter();
            ChatManager.getInstance().getChannelManager().addVisibilityFilter(visibilityFilter);
            LOGGER.info("已注册通知频道可见性过滤器");

            // 注册城市数据变更事件监听
            cityDataEventHandler = new CityDataEventHandler();
            MinecraftForge.EVENT_BUS.register(cityDataEventHandler);
            LOGGER.info("已注册城市数据变更事件监听");
        } catch (Exception e) {
            LOGGER.error("激活 simukraft 集成失败", e);
            service = null;
            visibilityFilter = null;
            cityDataEventHandler = null;
        }
    }

    public static void deactivate() {
        try {
            // 注销事件监听
            if (cityDataEventHandler != null) {
                MinecraftForge.EVENT_BUS.unregister(cityDataEventHandler);
                cityDataEventHandler = null;
                LOGGER.info("已注销城市数据变更事件监听");
            }

            // 移除可见性过滤器
            if (visibilityFilter != null) {
                ChatManager.getInstance().getChannelManager().removeVisibilityFilter(visibilityFilter);
                visibilityFilter = null;
                LOGGER.info("已移除通知频道可见性过滤器");
            }

            // 清空城市角色缓存
            CityRoleBridge.getInstance().clear();

            if (service != null) {
                service.saveAll();
                NotificationServiceManager.reset();
                service = null;
                LOGGER.info("已停用 simukraft 通知服务集成");
            }
        } catch (Exception e) {
            LOGGER.error("停用 simukraft 集成失败", e);
        }
    }

    public static CityChatNotificationService getService() {
        return service;
    }

    /**
     * 监听 CityDataChangedEvent，刷新 CityRoleBridge 缓存。
     * 替代之前的 CityDataAccessMixin。
     */
    static class CityDataEventHandler {

        @SubscribeEvent
        public void onCityDataChanged(CityDataChangedEvent event) {
            Map<UUID, Set<String>> roles = new HashMap<>();
            Map<String, String> cityNames = new HashMap<>();

            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

            for (CityDataChangedEvent.CitySnapshot city : event.getCities()) {
                String cityId = city.getCityId().toString();

                // 城市名称
                if (city.getCityName() != null && !city.getCityName().isBlank()) {
                    cityNames.put(cityId, city.getCityName());
                }

                // 市长（有 UUID，直接加入）
                if (city.getMayorId() != null) {
                    roles.computeIfAbsent(city.getMayorId(), k -> new HashSet<>()).add(cityId);
                }

                // 官员（只有名字，需要通过服务器解析 UUID）
                if (server != null && city.getOfficialNames() != null) {
                    for (String officialName : city.getOfficialNames()) {
                        ServerPlayer player = server.getPlayerList().getPlayerByName(officialName);
                        if (player != null) {
                            roles.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(cityId);
                        }
                    }
                }
            }

            CityRoleBridge.getInstance().refresh(roles, cityNames);
        }
    }
}
