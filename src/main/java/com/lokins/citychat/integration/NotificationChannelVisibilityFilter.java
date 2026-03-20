package com.lokins.citychat.integration;

import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.manager.ChannelVisibilityFilter;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 通知频道可见性过滤器。
 * <p>
 * 频道 ID 格式：{@code sk_notify_<cityId>_<categoryKey>}
 * <p>
 * 可见条件（满足任一即可）：
 * 1. 玩家是频道成员（通知接收者）
 * 2. 玩家是该城市的市长/官员（Mixin 注入的角色数据）
 */
public class NotificationChannelVisibilityFilter implements ChannelVisibilityFilter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHANNEL_PREFIX = "sk_notify_";

    @Override
    public boolean isVisible(ChatChannel channel, UUID playerId) {
        if (!channel.isNotificationChannel()) {
            return true;
        }

        // 条件 1：频道成员始终可见
        if (channel.isMember(playerId)) {
            return true;
        }

        // 条件 2：该城市的市长/官员可见
        CityRoleBridge bridge = CityRoleBridge.getInstance();
        if (bridge.isPopulated()) {
            String cityId = extractCityId(channel.getChannelId());
            if (cityId != null) {
                return bridge.isCityOfficialOf(playerId, cityId);
            }
            // 无法解析城市 ID，退化为检查任意城市官员
            return bridge.isCityOfficial(playerId);
        }

        // Mixin 未生效且不是成员 → 不可见
        return false;
    }

    /**
     * 从 channelId 提取城市 ID。
     * 格式：sk_notify_<cityUUID>_<categoryKey>
     * 其中 cityUUID 为 36 字符的标准 UUID 格式。
     */
    private static String extractCityId(String channelId) {
        if (channelId == null || !channelId.startsWith(CHANNEL_PREFIX)) {
            return null;
        }
        String rest = channelId.substring(CHANNEL_PREFIX.length());
        // cityUUID 为 36 字符（8-4-4-4-12），后面跟 _categoryKey
        if (rest.length() >= 37 && rest.charAt(36) == '_') {
            return rest.substring(0, 36);
        }
        // 兼容旧格式 sk_notify_city（无城市 ID）
        return null;
    }
}
