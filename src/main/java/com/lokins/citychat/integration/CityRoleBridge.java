package com.lokins.citychat.integration;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 城市角色桥接服务：缓存「玩家 → 拥有管理权的城市集合」映射 + 城市名称。
 * <p>
 * 数据来源：监听 SimuKraft 的 {@code CityDataChangedEvent}，在城市数据变更时全量刷新。
 */
public class CityRoleBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CityRoleBridge INSTANCE = new CityRoleBridge();

    /** playerId → 拥有管理权的城市 ID 集合 */
    private final Map<UUID, Set<String>> playerCityRoles = new ConcurrentHashMap<>();

    /** cityId → 城市名称 */
    private final Map<String, String> cityNames = new ConcurrentHashMap<>();

    /** 事件监听是否成功注入过数据 */
    private volatile boolean populated = false;

    public static CityRoleBridge getInstance() {
        return INSTANCE;
    }

    /** 玩家是否在任何城市中拥有管理权（市长或官员） */
    public boolean isCityOfficial(UUID playerId) {
        Set<String> roles = playerCityRoles.get(playerId);
        return roles != null && !roles.isEmpty();
    }

    /** 玩家是否为指定城市的市长或官员 */
    public boolean isCityOfficialOf(UUID playerId, String cityId) {
        if (cityId == null) return false;
        Set<String> roles = playerCityRoles.get(playerId);
        return roles != null && roles.contains(cityId);
    }

    /** 获取城市名称，未知时返回 null */
    public String getCityName(String cityId) {
        return cityNames.get(cityId);
    }

    public boolean isPopulated() {
        return populated;
    }

    public void addRole(UUID playerId, String cityId) {
        playerCityRoles.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(cityId);
        populated = true;
    }

    public void removeRole(UUID playerId, String cityId) {
        Set<String> roles = playerCityRoles.get(playerId);
        if (roles != null) {
            roles.remove(cityId);
            if (roles.isEmpty()) {
                playerCityRoles.remove(playerId);
            }
        }
    }

    /** 全量刷新角色 + 城市名（由 CityDataChangedEvent 监听器调用） */
    public void refresh(Map<UUID, Set<String>> newRoles, Map<String, String> newCityNames) {
        playerCityRoles.clear();
        for (var entry : newRoles.entrySet()) {
            playerCityRoles.put(entry.getKey(), ConcurrentHashMap.newKeySet());
            playerCityRoles.get(entry.getKey()).addAll(entry.getValue());
        }
        cityNames.clear();
        cityNames.putAll(newCityNames);
        populated = true;
        LOGGER.debug("CityRoleBridge 已刷新，{} 名管理者，{} 个城市", playerCityRoles.size(), cityNames.size());
    }

    /** 兼容旧版 refresh（无城市名） */
    public void refresh(Map<UUID, Set<String>> newRoles) {
        refresh(newRoles, Map.of());
    }

    public void clear() {
        playerCityRoles.clear();
        cityNames.clear();
        populated = false;
    }
}
