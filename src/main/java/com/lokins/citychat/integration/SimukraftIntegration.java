package com.lokins.citychat.integration;

import com.lokins.citychat.manager.ChatManager;
import com.lokins.citychat.manager.ChannelVisibilityFilter;
import com.mojang.logging.LogUtils;
import com.xiaoliang.simukraft.notification.NotificationServiceManager;
import org.slf4j.Logger;

/**
 * simukraft 集成激活/停用。
 * 此类直接引用 simukraft 的类，仅在 SimukraftDetector.isAvailable() 为 true 时加载。
 */
public class SimukraftIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static CityChatNotificationService service;
    private static ChannelVisibilityFilter visibilityFilter;

    public static void activate() {
        try {
            service = new CityChatNotificationService();
            NotificationServiceManager.registerService(service);
            LOGGER.info("已注册通知服务: CityChatNotificationService");

            // 注册通知频道可见性过滤器
            visibilityFilter = new NotificationChannelVisibilityFilter();
            ChatManager.getInstance().getChannelManager().addVisibilityFilter(visibilityFilter);
            LOGGER.info("已注册通知频道可见性过滤器");
        } catch (Exception e) {
            LOGGER.error("激活 simukraft 集成失败", e);
            service = null;
            visibilityFilter = null;
        }
    }

    public static void deactivate() {
        try {
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
}
