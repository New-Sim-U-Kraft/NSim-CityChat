package com.lokins.citychat.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知管理器 - 单例，维护消息通知队列。
 * 最多 5 条通知，5 秒自动消失，最后 1 秒淡出。
 */
public class NotificationManager {
    private static final NotificationManager INSTANCE = new NotificationManager();
    private static final int MAX_NOTIFICATIONS = 5;
    private static final long NOTIFICATION_DURATION_MS = 5000;
    private static final long FADE_DURATION_MS = 1000;

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public static NotificationManager getInstance() {
        return INSTANCE;
    }

    public record Notification(String channelName, String senderName, String preview, long createdTime) {
        public long age() {
            return System.currentTimeMillis() - createdTime;
        }

        public boolean isExpired() {
            return age() > NOTIFICATION_DURATION_MS;
        }

        /**
         * 返回透明度 0.0-1.0（最后 1 秒淡出）。
         */
        public float getAlpha() {
            long age = age();
            if (age > NOTIFICATION_DURATION_MS) {
                return 0f;
            }
            long fadeStart = NOTIFICATION_DURATION_MS - FADE_DURATION_MS;
            if (age > fadeStart) {
                return 1f - (float) (age - fadeStart) / FADE_DURATION_MS;
            }
            return 1f;
        }
    }

    public void addNotification(String channelName, String senderName, String content) {
        // 添加前先清理过期通知
        notifications.removeIf(Notification::isExpired);

        String preview = content.length() > 20 ? content.substring(0, 20) + "..." : content;
        notifications.add(new Notification(channelName, senderName, preview, System.currentTimeMillis()));

        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }
    }

    public List<Notification> getActiveNotifications() {
        notifications.removeIf(Notification::isExpired);
        return new ArrayList<>(notifications);
    }

    public void clear() {
        notifications.clear();
    }
}
