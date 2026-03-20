package com.lokins.citychat.mixin.simukraft;

import com.xiaoliang.simukraft.notification.MessageNotification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 SimuKraft 的 NullNotificationService（默认空实现）。
 * 如果这里打出日志，说明通知被吞了——CC 的服务没注册上或已被 reset。
 */
@Mixin(targets = "com.xiaoliang.simukraft.notification.NotificationServiceManager$NullNotificationService", remap = false)
public abstract class NullNotificationServiceMixin {

    @Inject(method = "sendNotification", at = @At("HEAD"))
    private void cc$onNullSendNotification(MessageNotification notification, CallbackInfoReturnable<Boolean> cir) {
        if (notification != null) {
            String cat = notification.getCategory() != null ? notification.getCategory().getKey() : "null";
            com.mojang.logging.LogUtils.getLogger().error(
                    "[SK-Mixin] !!!通知被 NullNotificationService 吞掉!!! " +
                    "分类={}, 标题={}, 内容={}, 接收者={}",
                    cat, notification.getTitle(), notification.getContent(), notification.getRecipientId());
        } else {
            com.mojang.logging.LogUtils.getLogger().error("[SK-Mixin] NullNotificationService.sendNotification 收到 null");
        }
    }
}
