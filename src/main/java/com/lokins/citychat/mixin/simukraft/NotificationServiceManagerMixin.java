package com.lokins.citychat.mixin.simukraft;

import com.xiaoliang.simukraft.notification.IMessageNotificationService;
import com.xiaoliang.simukraft.notification.NotificationServiceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 SimuKraft 的 NotificationServiceManager，
 * 追踪服务注册/获取/重置的全生命周期。
 */
@Mixin(value = NotificationServiceManager.class, remap = false)
public abstract class NotificationServiceManagerMixin {

    @Inject(method = "registerService", at = @At("HEAD"))
    private static void cc$onRegisterService(IMessageNotificationService service, CallbackInfo ci) {
        com.mojang.logging.LogUtils.getLogger().info("[SK-Mixin] registerService 被调用, service={}",
                service != null ? service.getClass().getName() : "null");
    }

    @Inject(method = "getService", at = @At("RETURN"))
    private static void cc$onGetService(CallbackInfoReturnable<IMessageNotificationService> cir) {
        IMessageNotificationService svc = cir.getReturnValue();
        String svcClass = svc != null ? svc.getClass().getName() : "null";
        // 只在返回 NullNotificationService 时用 WARN 级别
        if (svcClass.contains("Null")) {
            com.mojang.logging.LogUtils.getLogger().warn("[SK-Mixin] getService() 返回了 NullNotificationService! CC 服务未注册或已被 reset");
        } else {
            com.mojang.logging.LogUtils.getLogger().debug("[SK-Mixin] getService() 返回 {}", svcClass);
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private static void cc$onReset(CallbackInfo ci) {
        // reset 会把服务替换回 NullNotificationService，如果意外调用会导致通知丢失
        com.mojang.logging.LogUtils.getLogger().warn("[SK-Mixin] reset() 被调用! 服务将被替换为 NullNotificationService",
                new Throwable("reset 调用栈"));
    }

    @Inject(method = "isCustomServiceRegistered", at = @At("RETURN"))
    private static void cc$onIsCustomRegistered(CallbackInfoReturnable<Boolean> cir) {
        com.mojang.logging.LogUtils.getLogger().info("[SK-Mixin] isCustomServiceRegistered() = {}", cir.getReturnValue());
    }
}
