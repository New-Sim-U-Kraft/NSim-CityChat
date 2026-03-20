package com.lokins.citychat.data;

import java.util.UUID;

/**
 * 表示聊天系统中的玩家身份
 */
public class ChatUser {
    private final UUID playerId;
    private final String playerName;
    private String displayName;
    private final PermissionLevel permissionLevel;
    private boolean isMuted;

    public enum PermissionLevel {
        ADMIN(4, "管理员"),
        MODERATOR(3, "版主"),
        MEMBER(2, "成员"),
        GUEST(1, "访客");

        private final int level;
        private final String displayName;

        PermissionLevel(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
    }

    public ChatUser(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.displayName = playerName;
        this.permissionLevel = PermissionLevel.MEMBER;
        this.isMuted = false;
    }

    public ChatUser(UUID playerId, String playerName, PermissionLevel permissionLevel) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.displayName = playerName;
        this.permissionLevel = permissionLevel;
        this.isMuted = false;
    }

    // Getters and Setters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { isMuted = muted; }

    public boolean hasPermission(PermissionLevel requiredLevel) {
        return this.permissionLevel.getLevel() >= requiredLevel.getLevel();
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", displayName, permissionLevel.getDisplayName());
    }
}

