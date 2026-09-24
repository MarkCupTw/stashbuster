package com.stashbuster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 全部事件挂钩。
 *
 * <p>用 {@code @EventBusSubscriber} 注册到游戏事件总线；<b>不要再手动 register</b>，
 * 否则处理器会被调用两次。
 *
 * <p><b>这里故意没有"爆炸时把容器物品弹出来"的钩子。</b>
 * 因为原版本来就做这件事：爆炸通过
 * {@code IBlockExtension#onBlockExploded} → {@code level.setBlock(pos, AIR, 3)}
 * 移除方块，进而触发 {@code ChestBlock#onRemove} → {@code Containers.dropContentsOnDestroy}，
 * 内容物会被完整掉出。自己再写一遍不仅没有额外效果，还会引入物品重复与
 * "爆炸被别的 mod 取消但容器已被倒空"这类真实 bug。详见 README。
 */
@EventBusSubscriber(modid = StashBuster.MODID)
public final class StorageEvents {

    private StorageEvents() {
    }

    /**
     * 记录玩家新放置的方块。
     *
     * <p>放置事件发生在方块实体创建<b>之前</b>（NeoForge 是先发事件、可取消后再落方块），
     * 所以此刻无法判断它是不是容器。这里的策略是：凡是"带方块实体的方块"一律先记录下来，
     * 是不是储物方块留到搜索时用 {@code 方块实体 instanceof Container} 判定，
     * 不是容器的条目会被顺手清理。好处是能自动覆盖其它 mod 的容器，不需要维护白名单。
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!StashBusterConfig.ENABLED.get()) {
            return;
        }
        Entity placer = event.getEntity();
        if (!(placer instanceof Player)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (!state.hasBlockEntity()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlacedStorageRegistry.get(level).add(event.getPos());
    }

    /** 玩家自己拆掉方块时同步移除记录，避免残留无效目标。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getState().hasBlockEntity()) {
            return;
        }
        PlacedStorageRegistry registry = PlacedStorageRegistry.get(level);
        if (!registry.isEmpty() && registry.contains(event.getPos())) {
            registry.remove(event.getPos());
        }
    }

    /** 给每个新加入世界的苦力怕装上目标，带去重检查。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        // 区块卸载再加载会让实体重新 join，必须检查是否已经装过，否则目标会越加越多
        for (var wrapped : creeper.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof StorageSeekGoal) {
                return;
            }
        }
        creeper.goalSelector.addGoal(StorageSeekGoal.PRIORITY, new StorageSeekGoal(creeper));
    }
}
