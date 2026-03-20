package com.lokins.citychat.mixin.simukraft;

import com.lokins.citychat.integration.CityRoleBridge;
import com.xiaoliang.simukraft.data.CityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注入 SimuKraft 的 CityData，在城市数据标记脏（setDirty）时
 * 刷新 CityRoleBridge 中的市长/官员缓存。
 * <p>
 * 目标方法 {@code setDirty()} 是 Minecraft SavedData 的标准脏标记方法，
 * 城市增删改都会触发，因此可以覆盖所有角色变更场景。
 * <p>
 * 如果 SimuKraft 的 CityData 类结构与存根不一致，
 * CityChaiMixinPlugin 会跳过本 Mixin，不会导致崩溃。
 */
@Mixin(value = CityData.class, remap = false)
public abstract class CityDataAccessMixin {

    /**
     * 在 CityData.setDirty() 末尾注入，全量扫描城市角色并刷新桥接缓存。
     * setDirty() 由 Minecraft SavedData 定义，每次城市数据修改后必被调用。
     */
    @Inject(method = "setDirty()V", at = @At("TAIL"))
    private void cc$onCityDataDirty(CallbackInfo ci) {
        try {
            CityData self = (CityData) (Object) this;
            Collection<CityData.City> cities = self.getAllCities();
            if (cities == null) {
                return;
            }

            Map<UUID, Set<String>> roles = new HashMap<>();
            Map<String, String> cityNames = new HashMap<>();
            for (CityData.City city : cities) {
                if (city == null) continue;

                String cityId = city.getCityId();
                if (cityId == null) continue;

                // 城市名称
                String cityName = city.getCityName();
                if (cityName != null && !cityName.isBlank()) {
                    cityNames.put(cityId, cityName);
                }

                // 市长
                UUID mayorId = city.getMayorId();
                if (mayorId != null) {
                    roles.computeIfAbsent(mayorId, k -> new HashSet<>()).add(cityId);
                }

                // 官员
                Collection<UUID> officials = city.getOfficials();
                if (officials != null) {
                    for (UUID officialId : officials) {
                        if (officialId != null) {
                            roles.computeIfAbsent(officialId, k -> new HashSet<>()).add(cityId);
                        }
                    }
                }
            }

            CityRoleBridge.getInstance().refresh(roles, cityNames);
        } catch (Exception ignored) {
            // 结构不匹配时静默失败，不影响 SimuKraft 正常运行
        }
    }
}
