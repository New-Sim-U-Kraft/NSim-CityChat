package com.lokins.citychat.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 插件：仅当 SimuKraft 存在时才加载 simukraft 子包下的 Mixin。
 * 在 Mixin 阶段运行，不能用 Forge ModList（还未初始化），改用类加载探测。
 */
public class CityChaiMixinPlugin implements IMixinConfigPlugin {
    // 用 classpath 资源探测代替 Class.forName，避免触发类加载导致 MixinTargetAlreadyLoadedException
    private static final String SIMUKRAFT_PROBE_RESOURCE = "com/xiaoliang/simukraft/notification/NotificationServiceManager.class";
    private boolean simukraftPresent;

    @Override
    public void onLoad(String mixinPackage) {
        simukraftPresent = getClass().getClassLoader().getResource(SIMUKRAFT_PROBE_RESOURCE) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // simukraft 子包下的 mixin 仅在 SimuKraft 存在时才应用
        if (mixinClassName.contains(".simukraft.")) {
            return simukraftPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
