package com.lokins.citychat.event;

import java.util.UUID;

/**
 * 离开频道事件
 */
public class ChannelLeftEvent extends ChatEvent {
    private final String channelId;
    private final UUID playerId;
    private final String playerName;

    public ChannelLeftEvent(String channelId, UUID playerId, String playerName) {
        this.channelId = channelId;
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public String getChannelId() { return channelId; }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }

    @Override
    public String getEventId() {
        return "channel_left:" + channelId + ":" + playerId;
    }
}

