package com.lokins.citychat.client;

import com.lokins.citychat.data.MessageAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知管理器 - 单例，维护消息通知队列。
 * 最多 5 条通知，5 秒自动消失（有 action 按钮时延长到 10 秒），最后 1 秒淡出。
 */
public class NotificationManager {
    private static final NotificationManager INSTANCE = new NotificationManager();
    private static final int MAX_NOTIFICATIONS = 5;
    private static final long NOTIFICATION_DURATION_MS = 5000;
    private static final long ACTION_NOTIFICATION_DURATION_MS = 10000;
    private static final long FADE_DURATION_MS = 1000;

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public static NotificationManager getInstance() {
        return INSTANCE;
    }

    public record Notification(String channelName, String senderName, String preview,
                                long createdTime, List<MessageAction> actions) {

        public Notification(String channelName, String senderName, String preview, long createdTime) {
            this(channelName, senderName, preview, createdTime, Collections.emptyList());
        }

        public boolean hasActions() {
            return actions != null && !actions.isEmpty();
        }

        private long duration() {
            return hasActions() ? ACTION_NOTIFICATION_DURATION_MS : NOTIFICATION_DURATION_MS;
        }

        public long age() {
            return System.currentTimeMillis() - createdTime;
        }

        public boolean isExpired() {
            return age() > duration();
        }

        public float getAlpha() {
            long age = age();
            long dur = duration();
            if (age > dur) return 0f;
            long fadeStart = dur - FADE_DURATION_MS;
            if (age > fadeStart) return 1f - (float) (age - fadeStart) / FADE_DURATION_MS;
            return 1f;
        }
    }

    public void addNotification(String channelName, String senderName, String content) {
        addNotification(channelName, senderName, content, Collections.emptyList());
    }

    public void addNotification(String channelName, String senderName, String content,
                                 List<MessageAction> actions) {
        notifications.removeIf(Notification::isExpired);

        String preview = content.length() > 20 ? content.substring(0, 20) + "..." : content;
        notifications.add(new Notification(channelName, senderName, preview, System.currentTimeMillis(),
                actions == null ? Collections.emptyList() : actions));

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
