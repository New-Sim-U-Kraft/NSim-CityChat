package com.lokins.citychat.manager;

import com.lokins.citychat.Config;
import com.lokins.citychat.data.ChatChannel;
import com.lokins.citychat.data.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频道管理器 - 管理所有聊天频道
 */
public class ChannelManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int STORAGE_VERSION = 1;

    private final Map<String, ChatChannel> channels = new ConcurrentHashMap<>();
    private final Map<Integer, String> groupNoToChannelId = new ConcurrentHashMap<>();
    private final AtomicInteger nextGroupNo = new AtomicInteger(1);
    private final ChatEventDispatcher eventDispatcher;
    private Path storagePath;

    public ChannelManager(ChatEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.storagePath = resolveServerStoragePath();
        if (isServerSide()) {
            loadPersistedChannels();
        }
    }

    /**
     * 是否运行在服务端（含局域网房主）。
     * 纯客户端连接到远程服务器时返回 false，此时不做任何本地文件 I/O。
     */
    private boolean isServerSide() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    private Path resolveServerStoragePath() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT)
                    .resolve("data")
                    .resolve("cc_groups.json");
        }
        // 纯客户端：不会用到此路径（不做文件 I/O），仅作占位
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("cc_groups_client_unused.json");
    }

    private void ensureStorageContext() {
        if (!isServerSide()) {
            // 纯客户端不做存储上下文切换，数据全靠 replaceFromSnapshot
            return;
        }
        Path resolved = resolveServerStoragePath();
        if (Objects.equals(resolved, storagePath)) {
            return;
        }

        storagePath = resolved;
        channels.clear();
        groupNoToChannelId.clear();
        nextGroupNo.set(1);
        loadPersistedChannels();
    }

    public ChatChannel createChannel(String channelId, String displayName,
                                     ChatChannel.ChannelType type, UUID ownerId) {
        ensureStorageContext();
        return createChannel(channelId, displayName, "", type, ChatChannel.GroupAccess.NORMAL, "", ownerId);
    }

    public ChatChannel createChannel(String channelId, String displayName, String description,
                                     ChatChannel.ChannelType type, UUID ownerId) {
        ensureStorageContext();
        return createChannel(channelId, displayName, description, type, ChatChannel.GroupAccess.NORMAL, "", ownerId);
    }

    public synchronized ChatChannel createChannel(String channelId, String displayName, String description,
                                     ChatChannel.ChannelType type, ChatChannel.GroupAccess access,
                                     String password, UUID ownerId) {
        ensureStorageContext();
        if (channelId == null || channelId.isBlank() || channels.containsKey(channelId)) {
            LOGGER.warn("Channel {} already exists or invalid", channelId);
            return null;
        }

        boolean duplicateName = channels.values().stream()
                .anyMatch(ch -> ch.getDisplayName().equalsIgnoreCase(displayName));
        if (duplicateName) {
            LOGGER.warn("Channel displayName {} already exists", displayName);
            return null;
        }

        int groupNo = nextGroupNo.getAndIncrement();
        ChatChannel channel = new ChatChannel(channelId, groupNo, displayName, description, type, access, password, ownerId);
        channels.put(channelId, channel);
        groupNoToChannelId.put(groupNo, channelId);
        LOGGER.info("Created new channel: {} ({}) #{} [{}]", displayName, channelId, groupNo, access);
        persistChannels();
        return channel;
    }

    public ChatChannel getChannel(String channelId) {
        ensureStorageContext();
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        return channels.get(channelId);
    }

    public ChatChannel findChannelByNameOrNumber(String query) {
        ensureStorageContext();
        if (query == null || query.isBlank()) {
            return null;
        }

        String trimmed = query.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.matches("\\d+")) {
            int number = Integer.parseInt(trimmed);
            String channelId = groupNoToChannelId.get(number);
            return channelId == null ? null : channels.get(channelId);
        }

        for (ChatChannel channel : channels.values()) {
            if (channel.getDisplayName().equalsIgnoreCase(query.trim())) {
                return channel;
            }
        }
        return null;
    }

    public boolean joinChannel(String channelId, UUID playerId, String playerName) {
        return joinChannel(channelId, "", playerId, playerName);
    }

    public boolean joinChannel(String channelId, String password, UUID playerId, String playerName) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            LOGGER.warn("Channel {} not found", channelId);
            return false;
        }

        // 密码校验必须在成员检查之前，防止被踢后绕过密码重新加入
        if (!channel.canJoinWithPassword(password)) {
            LOGGER.info("Player {} failed password check for channel {}", playerName, channelId);
            return false;
        }

        if (channel.isMember(playerId)) {
            return true; // 已是成员且密码正确
        }

        channel.addMember(playerId);
        eventDispatcher.dispatchChannelJoined(channelId, playerId, playerName);
        LOGGER.info("Player {} joined channel {}", playerName, channelId);
        persistChannels();
        return true;
    }

    public boolean joinByNameOrNumber(String query, String password, UUID playerId, String playerName) {
        ChatChannel channel = findChannelByNameOrNumber(query);
        if (channel == null) {
            return false;
        }
        return joinChannel(channel.getChannelId(), password, playerId, playerName);
    }

    public boolean leaveChannel(String channelId, UUID playerId, String playerName) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            LOGGER.warn("Channel {} not found", channelId);
            return false;
        }

        if (!channel.isMember(playerId)) {
            return false;
        }

        channel.removeMember(playerId);
        eventDispatcher.dispatchChannelLeft(channelId, playerId, playerName);
        LOGGER.info("Player {} left channel {}", playerName, channelId);
        persistChannels();
        return true;
    }

    /** 未持久化的消息计数器，每 MESSAGE_PERSIST_THRESHOLD 条消息自动保存一次 */
    private static final int MESSAGE_PERSIST_THRESHOLD = 50;
    private final AtomicInteger unsavedMessageCount = new AtomicInteger(0);

    public void addMessage(String channelId, ChatMessage message) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel != null) {
            channel.addMessage(message);
            if (unsavedMessageCount.incrementAndGet() >= MESSAGE_PERSIST_THRESHOLD) {
                unsavedMessageCount.set(0);
                persistChannels();
            }
        }
    }

    public List<ChatChannel> getAllActiveChannels() {
        ensureStorageContext();
        List<ChatChannel> activeChannels = new ArrayList<>();
        for (ChatChannel channel : channels.values()) {
            if (channel.isActive()) {
                activeChannels.add(channel);
            }
        }
        activeChannels.sort(Comparator.comparingInt(ChatChannel::getGroupNumber));
        return activeChannels;
    }

    /**
     * 用服务端快照覆盖客户端频道列表（不写入磁盘）。
     */
    public synchronized void replaceFromSnapshot(List<ChatChannel> snapshotChannels) {
        channels.clear();
        groupNoToChannelId.clear();
        nextGroupNo.set(1);

        if (snapshotChannels == null) {
            return;
        }

        for (ChatChannel channel : snapshotChannels) {
            if (channel == null || channel.getChannelId() == null || channel.getChannelId().isBlank()) {
                continue;
            }
            channels.put(channel.getChannelId(), channel);
            groupNoToChannelId.put(channel.getGroupNumber(), channel.getChannelId());
            nextGroupNo.updateAndGet(v -> Math.max(v, channel.getGroupNumber() + 1));
        }
    }

    /**
     * 获取玩家可见的频道列表（用于频道列表 UI 和快照下发）。
     * PUBLIC 对所有人可见；NORMAL / ENCRYPTED 仅对已是成员的人可见。
     * 非成员需要通过"加入群组"输入群号/群名搜索后加入。
     */
    public List<ChatChannel> getVisibleChannelsForPlayer(UUID playerId) {
        ensureStorageContext();
        List<ChatChannel> visible = new ArrayList<>();
        for (ChatChannel channel : channels.values()) {
            if (!channel.isActive()) {
                continue;
            }
            if (channel.isPublicVisible() || channel.isMember(playerId)) {
                visible.add(channel);
            }
        }
        visible.sort(Comparator.comparingInt(ChatChannel::getGroupNumber));
        return visible;
    }

    public List<ChatChannel> getPlayerChannels(UUID playerId) {
        ensureStorageContext();
        List<ChatChannel> playerChannels = new ArrayList<>();
        for (ChatChannel channel : channels.values()) {
            if (channel.isMember(playerId)) {
                playerChannels.add(channel);
            }
        }
        playerChannels.sort(Comparator.comparingInt(ChatChannel::getGroupNumber));
        return playerChannels;
    }

    public boolean deleteChannel(String channelId) {
        ensureStorageContext();
        ChatChannel removed = channels.remove(channelId);
        if (removed != null) {
            groupNoToChannelId.remove(removed.getGroupNumber());
            LOGGER.info("Deleted channel: {}", channelId);
            persistChannels();
            return true;
        }
        return false;
    }

    public boolean setAdmin(String channelId, UUID operatorId, UUID targetId, boolean admin) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            return false;
        }
        boolean ok = admin ? channel.addAdmin(operatorId, targetId) : channel.removeAdmin(operatorId, targetId);
        if (ok) {
            persistChannels();
        }
        return ok;
    }

    public boolean changeGroupAccess(String channelId, UUID operatorId, ChatChannel.GroupAccess newAccess) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            return false;
        }
        boolean ok = channel.updateAccess(operatorId, newAccess);
        if (ok) {
            persistChannels();
        }
        return ok;
    }

    public boolean changeGroupPassword(String channelId, UUID operatorId, String password) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            return false;
        }
        boolean ok = channel.updatePassword(operatorId, password);
        if (ok) {
            persistChannels();
        }
        return ok;
    }

    /**
     * 踢人：管理员不能踢管理员（除非是群主操作）。
     */
    public boolean kickMember(String channelId, UUID operatorId, UUID targetId) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null || targetId == null) {
            return false;
        }
        // 不能踢自己
        if (operatorId.equals(targetId)) {
            return false;
        }
        // 不能踢群主
        if (channel.isOwner(targetId)) {
            return false;
        }
        // 操作者必须是管理员或群主
        if (!channel.canManage(operatorId)) {
            return false;
        }
        // 管理员不能踢管理员，除非操作者是群主
        if (channel.isAdmin(targetId) && !channel.isOwner(operatorId)) {
            return false;
        }
        if (!channel.isMember(targetId)) {
            return false;
        }
        channel.removeMember(targetId);
        persistChannels();
        return true;
    }

    /**
     * 转让群主。
     */
    public boolean transferOwnership(String channelId, UUID operatorId, UUID newOwnerId) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null) {
            return false;
        }
        boolean ok = channel.transferOwnership(operatorId, newOwnerId);
        if (ok) {
            persistChannels();
        }
        return ok;
    }

    /**
     * 变更群号。原子级：synchronized 保证同一时间只有一个变更操作，防止重复群号。
     * 仅群主可操作。
     */
    public synchronized boolean changeGroupNumber(String channelId, UUID operatorId, int newNumber) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        if (channel == null || !channel.isOwner(operatorId)) {
            return false;
        }
        if (newNumber <= 0) {
            return false;
        }
        // 同号无需变更
        if (channel.getGroupNumber() == newNumber) {
            return true;
        }
        // 原子检查：新群号是否已被占用
        String existing = groupNoToChannelId.get(newNumber);
        if (existing != null) {
            LOGGER.warn("Group number {} already in use by {}", newNumber, existing);
            return false;
        }
        // 移除旧映射，写入新映射
        int oldNumber = channel.getGroupNumber();
        groupNoToChannelId.remove(oldNumber);
        channel.setGroupNumber(newNumber);
        groupNoToChannelId.put(newNumber, channelId);
        nextGroupNo.updateAndGet(v -> Math.max(v, newNumber + 1));
        LOGGER.info("Channel {} group number changed from {} to {}", channelId, oldNumber, newNumber);
        persistChannels();
        return true;
    }

    public boolean dissolveGroup(String channelId, UUID operatorId) {
        ensureStorageContext();
        ChatChannel channel = getChannel(channelId);
        // 只有群主可以解散群组
        if (channel == null || !channel.isOwner(operatorId)) {
            return false;
        }
        return deleteChannel(channelId);
    }

    /**
     * 强制保存所有数据到磁盘（用于服务器关闭时）。
     */
    public void saveAll() {
        unsavedMessageCount.set(0);
        persistChannels();
        LOGGER.info("All channel data saved to disk");
    }

    public int getChannelCount() {
        ensureStorageContext();
        return channels.size();
    }

    public void clear() {
        channels.clear();
        groupNoToChannelId.clear();
        nextGroupNo.set(1);
        if (isServerSide()) {
            try {
                Files.deleteIfExists(storagePath);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete persisted channels file: {}", storagePath, e);
            }
        }
        LOGGER.info("Cleared all channels");
    }

    private void loadPersistedChannels() {
        if (!Files.exists(storagePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(storagePath)) {
            PersistedState state = GSON.fromJson(reader, PersistedState.class);
            if (state == null || state.channels == null || state.channels.isEmpty()) {
                return;
            }

            for (PersistedChannel persisted : state.channels) {
                restoreChannel(persisted);
            }
            LOGGER.info("Loaded {} persisted channels", channels.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load persisted channels from {}", storagePath, e);
        }
    }

    private void restoreChannel(PersistedChannel persisted) {
        if (persisted == null || persisted.channelId == null || persisted.channelId.isBlank() || channels.containsKey(persisted.channelId)) {
            return;
        }
        if (persisted.displayName == null || persisted.displayName.isBlank() || persisted.ownerId == null) {
            return;
        }

        int groupNo = persisted.groupNumber > 0 ? persisted.groupNumber : nextGroupNo.getAndIncrement();
        nextGroupNo.updateAndGet(v -> Math.max(v, groupNo + 1));

        ChatChannel.ChannelType type = parseChannelType(persisted.type);
        ChatChannel.GroupAccess access = parseGroupAccess(persisted.access);
        String password = persisted.password == null ? "" : persisted.password;
        UUID ownerId = parseUuid(persisted.ownerId);
        if (ownerId == null) {
            return;
        }

        ChatChannel channel = new ChatChannel(
                persisted.channelId,
                groupNo,
                persisted.displayName,
                persisted.description == null ? "" : persisted.description,
                type,
                access,
                password,
                ownerId
        );

        if (persisted.members != null) {
            for (String member : persisted.members) {
                UUID memberId = parseUuid(member);
                if (memberId != null) {
                    channel.addMember(memberId);
                }
            }
        }

        if (persisted.admins != null) {
            for (String admin : persisted.admins) {
                UUID adminId = parseUuid(admin);
                if (adminId != null) {
                    channel.addAdmin(ownerId, adminId);
                }
            }
        }

        // 恢复消息历史
        if (persisted.messages != null) {
            for (PersistedMessage pmsg : persisted.messages) {
                UUID senderId = parseUuid(pmsg.senderId);
                if (senderId != null && pmsg.senderName != null && pmsg.content != null) {
                    UUID messageId = parseUuid(pmsg.messageId);
                    ChatMessage msg = new ChatMessage(
                            senderId,
                            pmsg.senderName,
                            pmsg.content,
                            persisted.channelId,
                            false,
                            pmsg.timestamp,
                            messageId
                    );
                    channel.addMessage(msg);
                }
            }
        }

        channels.put(channel.getChannelId(), channel);
        groupNoToChannelId.put(channel.getGroupNumber(), channel.getChannelId());
    }

    private void persistChannels() {
        if (!isServerSide()) {
            // 纯客户端不持久化，数据由服务端管理
            return;
        }
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            PersistedState state = new PersistedState();
            state.version = STORAGE_VERSION;
            state.channels = new ArrayList<>();
            for (ChatChannel channel : getAllActiveChannels()) {
                PersistedChannel persisted = new PersistedChannel();
                persisted.channelId = channel.getChannelId();
                persisted.groupNumber = channel.getGroupNumber();
                persisted.displayName = channel.getDisplayName();
                persisted.description = channel.getDescription();
                persisted.type = channel.getType().name();
                persisted.access = channel.getAccess().name();
                persisted.ownerId = channel.getOwnerId().toString();
                persisted.members = channel.getMembers().stream().map(UUID::toString).toList();
                persisted.admins = channel.getAdmins().stream().map(UUID::toString).toList();
                // 仅加密群持久化密码，普通/公开群保持空密码。
                persisted.password = channel.getAccess() == ChatChannel.GroupAccess.ENCRYPTED ? channel.getPasswordForPersistence() : "";

                // 持久化最后 N 条消息（根据配置）
                int limit = Config.messageHistoryLimit > 0 ? Config.messageHistoryLimit : 500;
                List<ChatMessage> messages = channel.getMessageHistory(limit);
                persisted.messages = new ArrayList<>();
                for (ChatMessage msg : messages) {
                    PersistedMessage pmsg = new PersistedMessage();
                    pmsg.senderId = msg.getSenderId().toString();
                    pmsg.senderName = msg.getSenderName();
                    pmsg.content = msg.getContent();
                    pmsg.timestamp = msg.getTimestamp();
                    pmsg.messageId = msg.getMessageId().toString();
                    persisted.messages.add(pmsg);
                }
                
                state.channels.add(persisted);
            }

            // 先写到临时文件，成功后再替换，防止写入中断导致数据丢失
            Path tempFile = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tempFile, storagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, storagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to persist channels to {}", storagePath, e);
        }
    }


    private ChatChannel.ChannelType parseChannelType(String rawType) {
        try {
            return ChatChannel.ChannelType.valueOf(rawType);
        } catch (Exception ignored) {
            return ChatChannel.ChannelType.GROUP;
        }
    }

    private ChatChannel.GroupAccess parseGroupAccess(String rawAccess) {
        try {
            return ChatChannel.GroupAccess.valueOf(rawAccess);
        } catch (Exception ignored) {
            return ChatChannel.GroupAccess.NORMAL;
        }
    }

    private UUID parseUuid(String rawUuid) {
        try {
            return rawUuid == null ? null : UUID.fromString(rawUuid);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class PersistedState {
        int version;
        List<PersistedChannel> channels;
    }

    private static class PersistedChannel {
        String channelId;
        int groupNumber;
        String displayName;
        String description;
        String type;
        String access;
        String password;
        String ownerId;
        List<String> members;
        List<String> admins;
        List<PersistedMessage> messages;
    }

    private static class PersistedMessage {
        String senderId;
        String senderName;
        String content;
        long timestamp;
        String messageId;
    }
}
