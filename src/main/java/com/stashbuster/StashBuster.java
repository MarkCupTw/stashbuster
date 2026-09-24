package com.stashbuster;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Stash Buster —— 让普通苦力怕主动去找并炸开玩家放置的、物品最多的储物方块。
 *
 * <p>本 mod 是纯服务端逻辑：所有事件处理器都会在客户端侧提前返回，
 * 不注册任何渲染、UI、按键或网络包。
 */
@Mod(StashBuster.MODID)
public final class StashBuster {

    public static final String MODID = "stashbuster";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StashBuster(IEventBus modEventBus, ModContainer modContainer) {
        // 事件处理器通过 @EventBusSubscriber 自动注册到游戏事件总线（见 StorageEvents），
        // 这里不再手动 register，避免同一处理器被注册两次造成重复掉落。
        modContainer.registerConfig(ModConfig.Type.COMMON, StashBusterConfig.SPEC);
    }
}
