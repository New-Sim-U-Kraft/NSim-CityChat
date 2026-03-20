package com.lokins.citychat.manager;

import com.lokins.citychat.data.ChatChannel;

import java.util.UUID;

/**
 * 频道可见性过滤器：决定某个频道对某个玩家是否可见。
 */
@FunctionalInterface
public interface ChannelVisibilityFilter {
    boolean isVisible(ChatChannel channel, UUID playerId);
}
