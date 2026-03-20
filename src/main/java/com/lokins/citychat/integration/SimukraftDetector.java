package com.lokins.citychat.integration;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * 运行时检测 simukraft 是否存在。
 * 不直接 import 任何 simukraft 类，避免 ClassNotFoundException。
 */
public class SimukraftDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SIMUKRAFT_MOD_ID = "simukraft";
    private static Boolean available;

    public static boolean isAvailable() {
        if (available == null) {
            available = ModList.get() != null && ModList.get().isLoaded(SIMUKRAFT_MOD_ID);
            if (available) {
                LOGGER.info("检测到 simukraft 模组，将启用通知服务集成");
            } else {
                LOGGER.info("未检测到 simukraft 模组，CityChat 将作为独立聊天室运行");
            }
        }
        return available;
    }
}
