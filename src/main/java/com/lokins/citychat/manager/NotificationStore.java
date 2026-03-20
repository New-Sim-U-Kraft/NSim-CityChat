package com.lokins.citychat.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知存储 - 服务端单例，按玩家存储通知数据。
 * 持久化到世界存档 data/cc_notifications.json。
 */
public class NotificationStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int STORAGE_VERSION = 1;

    // playerId -> 该玩家的通知列表
    private final Map<UUID, List<StoredNotification>> notifications = new ConcurrentHashMap<>();

    public static class StoredNotification {
        public String id;
        public long timestamp;
        public String sender;
        public String senderType;
        public String title;
        public String content;
        public String recipientId;
        public boolean isRead;
        public String category;
        public String relatedEntityId;
        public String relatedEntityType;
        public Map<String, String> metadata;

        public StoredNotification() {
            this.metadata = new HashMap<>();
        }
    }

    public List<StoredNotification> getPlayerNotifications(UUID playerId) {
        return notifications.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void addNotification(UUID playerId, StoredNotification notification) {
        getPlayerNotifications(playerId).add(notification);
    }

    public StoredNotification findById(UUID notificationId) {
        String idStr = notificationId.toString();
        for (List<StoredNotification> list : notifications.values()) {
            synchronized (list) {
                for (StoredNotification n : list) {
                    if (idStr.equals(n.id)) {
                        return n;
                    }
                }
            }
        }
        return null;
    }

    public boolean removeById(UUID notificationId) {
        String idStr = notificationId.toString();
        for (List<StoredNotification> list : notifications.values()) {
            synchronized (list) {
                if (list.removeIf(n -> idStr.equals(n.id))) {
                    return true;
                }
            }
        }
        return false;
    }

    public int removeAllForPlayer(UUID playerId) {
        List<StoredNotification> list = notifications.remove(playerId);
        return list != null ? list.size() : 0;
    }

    public void clear() {
        notifications.clear();
    }

    // ========== 持久化 ==========

    private Path resolveStoragePath() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT)
                    .resolve("data")
                    .resolve("cc_notifications.json");
        }
        return null;
    }

    public void load() {
        Path path = resolveStoragePath();
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            PersistedState state = GSON.fromJson(reader, PersistedState.class);
            if (state == null || state.players == null) {
                return;
            }
            notifications.clear();
            for (PersistedPlayer pp : state.players) {
                UUID playerId = parseUuid(pp.playerId);
                if (playerId == null || pp.notifications == null) {
                    continue;
                }
                List<StoredNotification> list = Collections.synchronizedList(new ArrayList<>(pp.notifications));
                notifications.put(playerId, list);
            }
            LOGGER.info("加载了 {} 个玩家的通知数据", notifications.size());
        } catch (Exception e) {
            LOGGER.warn("加载通知数据失败: {}", path, e);
        }
    }

    public void saveAll() {
        Path path = resolveStoragePath();
        if (path == null) {
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            PersistedState state = new PersistedState();
            state.version = STORAGE_VERSION;
            state.players = new ArrayList<>();
            for (Map.Entry<UUID, List<StoredNotification>> entry : notifications.entrySet()) {
                PersistedPlayer pp = new PersistedPlayer();
                pp.playerId = entry.getKey().toString();
                synchronized (entry.getValue()) {
                    pp.notifications = new ArrayList<>(entry.getValue());
                }
                state.players.add(pp);
            }

            Path tempFile = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("保存通知数据失败: {}", path, e);
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class PersistedState {
        int version;
        List<PersistedPlayer> players;
    }

    private static class PersistedPlayer {
        String playerId;
        List<StoredNotification> notifications;
    }
}
