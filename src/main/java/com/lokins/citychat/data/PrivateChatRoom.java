package com.lokins.citychat.data;

import java.util.UUID;

/**
 * 表示私聊房间
 */
public class PrivateChatRoom {
    private final String roomId;
    private final UUID participant1;
    private final UUID participant2;
    private final String participant1Name;
    private final String participant2Name;
    private final long createdTime;

    public PrivateChatRoom(UUID participant1, UUID participant2, String p1Name, String p2Name) {
        this.roomId = generateRoomId(participant1, participant2);
        this.participant1 = participant1;
        this.participant2 = participant2;
        this.participant1Name = p1Name;
        this.participant2Name = p2Name;
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * 生成标准化的房间ID，确保相同的两个玩家总是生成相同的房间ID
     */
    public static String generateRoomId(UUID p1, UUID p2) {
        String combined = (p1.compareTo(p2) < 0) ?
                p1.toString() + ":" + p2.toString() :
                p2.toString() + ":" + p1.toString();
        return combined;
    }

    public boolean isParticipant(UUID playerId) {
        return participant1.equals(playerId) || participant2.equals(playerId);
    }

    public UUID getOtherParticipant(UUID playerId) {
        if (participant1.equals(playerId)) {
            return participant2;
        } else if (participant2.equals(playerId)) {
            return participant1;
        }
        return null;
    }

    public String getOtherParticipantName(UUID playerId) {
        if (participant1.equals(playerId)) {
            return participant2Name;
        } else if (participant2.equals(playerId)) {
            return participant1Name;
        }
        return null;
    }

    // Getters
    public String getRoomId() { return roomId; }
    public UUID getParticipant1() { return participant1; }
    public UUID getParticipant2() { return participant2; }
    public String getParticipant1Name() { return participant1Name; }
    public String getParticipant2Name() { return participant2Name; }
    public long getCreatedTime() { return createdTime; }

    @Override
    public String toString() {
        return String.format("PM Room: %s ↔ %s", participant1Name, participant2Name);
    }
}


