package com.lokins.citychat.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表示聊天频道的数据模型
 */
public class ChatChannel {
    private final String channelId;
    private volatile int groupNumber;
    private volatile String displayName;
    private final String description;
    private final ChannelType type;
    private volatile GroupAccess access;
    private volatile String password;
    private volatile UUID ownerId;
    private final Set<UUID> members;
    private final Set<UUID> admins;
    private final List<ChatMessage> messageHistory;
    private final long createdTime;
    private volatile boolean isActive;

    public enum ChannelType {
        GROUP("群聊", "#4ecdc4"),
        PRIVATE("私聊", "#95e1d3"),
        NOTIFICATION("城市通知", "#f0a500");

        private final String displayName;
        private final String color;

        ChannelType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    public enum GroupAccess {
        PUBLIC("公开"),
        NORMAL("普通"),
        ENCRYPTED("加密");

        private final String displayName;

        GroupAccess(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public ChatChannel(String channelId, int groupNumber, String displayName, String description,
                       ChannelType type, GroupAccess access, String password, UUID ownerId) {
        this.channelId = channelId;
        this.groupNumber = groupNumber;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.access = access;
        this.password = password == null ? "" : password;
        this.ownerId = ownerId;
        this.members = ConcurrentHashMap.newKeySet();
        this.admins = ConcurrentHashMap.newKeySet();
        this.messageHistory = Collections.synchronizedList(new LinkedList<>());
        this.createdTime = System.currentTimeMillis();
        this.isActive = true;
        this.members.add(ownerId);
    }

    // Member management
    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        if (!playerId.equals(ownerId)) {
            members.remove(playerId);
            admins.remove(playerId);
        }
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }

    public boolean isAdmin(UUID playerId) {
        return admins.contains(playerId);
    }

    public boolean canManage(UUID playerId) {
        return isOwner(playerId) || isAdmin(playerId);
    }

    /**
     * 设为管理员：仅群主可操作，不能设群主自己。
     */
    public boolean addAdmin(UUID operatorId, UUID targetId) {
        if (!isOwner(operatorId) || targetId == null || !isMember(targetId) || isOwner(targetId)) {
            return false;
        }
        return admins.add(targetId);
    }

    /**
     * 取消管理员：仅群主可操作，不能对自己操作，目标必须是管理员。
     */
    public boolean removeAdmin(UUID operatorId, UUID targetId) {
        if (!isOwner(operatorId) || targetId == null || isOwner(targetId) || !isAdmin(targetId)) {
            return false;
        }
        return admins.remove(targetId);
    }

    /**
     * 转让群主：旧群主自动变管理员，新群主从管理员中移除。
     */
    public boolean transferOwnership(UUID operatorId, UUID newOwnerId) {
        if (!isOwner(operatorId) || newOwnerId == null || !isMember(newOwnerId) || operatorId.equals(newOwnerId)) {
            return false;
        }
        UUID oldOwnerId = this.ownerId;
        this.ownerId = newOwnerId;
        // 新群主不再是管理员（已经是群主了）
        admins.remove(newOwnerId);
        // 旧群主变为管理员
        admins.add(oldOwnerId);
        return true;
    }

    public boolean canJoinWithPassword(String candidatePassword) {
        if (access != GroupAccess.ENCRYPTED) {
            return true;
        }
        // 加密群：密码不能为空，且必须匹配
        String candidate = candidatePassword == null ? "" : candidatePassword;
        if (candidate.isEmpty() || password.isEmpty()) {
            return false;
        }
        return password.equals(candidate);
    }

    public boolean isNotificationChannel() {
        return type == ChannelType.NOTIFICATION;
    }

    public boolean isPublicVisible() {
        return access == GroupAccess.PUBLIC;
    }

    public boolean updateAccess(UUID operatorId, GroupAccess newAccess) {
        if (!canManage(operatorId) || newAccess == null) {
            return false;
        }
        this.access = newAccess;
        // 切到非加密模式时清空密码。
        if (newAccess != GroupAccess.ENCRYPTED) {
            this.password = "";
        }
        return true;
    }

    public boolean updatePassword(UUID operatorId, String newPassword) {
        if (!canManage(operatorId) || access != GroupAccess.ENCRYPTED) {
            return false;
        }
        if (newPassword == null || newPassword.trim().length() < 4) {
            return false;
        }
        this.password = newPassword.trim();
        return true;
    }

    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        synchronized (messageHistory) {
            for (ChatMessage existing : messageHistory) {
                if (existing.getMessageId().equals(message.getMessageId())) {
                    return;
                }
            }
            messageHistory.add(message);
            int limit;
            try {
                limit = com.lokins.citychat.Config.messageHistoryLimit;
            } catch (Exception e) {
                limit = 500;
            }
            if (limit <= 0) {
                limit = 500;
            }
            while (messageHistory.size() > limit) {
                messageHistory.remove(0);
            }
        }
    }

    public boolean removeMessage(UUID messageId) {
        if (messageId == null) {
            return false;
        }
        synchronized (messageHistory) {
            return messageHistory.removeIf(msg -> msg.getMessageId().equals(messageId));
        }
    }

    public List<ChatMessage> getMessageHistory() {
        synchronized (messageHistory) {
            return new ArrayList<>(messageHistory);
        }
    }

    public List<ChatMessage> getMessageHistory(int limit) {
        synchronized (messageHistory) {
            int start = Math.max(0, messageHistory.size() - limit);
            return new ArrayList<>(messageHistory.subList(start, messageHistory.size()));
        }
    }

    public ChatMessage getMessage(UUID messageId) {
        if (messageId == null) {
            return null;
        }
        synchronized (messageHistory) {
            for (ChatMessage msg : messageHistory) {
                if (msg.getMessageId().equals(messageId)) {
                    return msg;
                }
            }
            return null;
        }
    }

    // Getters
    public String getChannelId() { return channelId; }
    public int getGroupNumber() { return groupNumber; }
    public void setGroupNumber(int groupNumber) { this.groupNumber = groupNumber; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public ChannelType getType() { return type; }
    public GroupAccess getAccess() { return access; }
    public String getPasswordForPersistence() { return password; }
    public UUID getOwnerId() { return ownerId; }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public Set<UUID> getAdmins() { return new HashSet<>(admins); }
    public int getMemberCount() { return members.size(); }
    public long getCreatedTime() { return createdTime; }
    public boolean isActive() { return isActive; }

    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return String.format("#%d %s (%s) - %d members", groupNumber, displayName, access.getDisplayName(), members.size());
    }
}
