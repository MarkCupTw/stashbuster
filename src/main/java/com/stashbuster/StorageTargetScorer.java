package com.stashbuster;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 挑选目标储物方块。
 *
 * <p>性能设计：只遍历"玩家放置过的带方块实体的方块"这个集合，不做任何世界范围扫描。
 * 先做纯算术的距离判断，只有距离合格的候选才去碰世界（读方块状态、读方块实体、读容器内容）。
 * 因此单次搜索的开销与"玩家放过的方块数量"成正比，且绝大多数候选在第一步就被淘汰。
 */
public final class StorageTargetScorer {

    /** 一次搜索的结果。 */
    public record Target(BlockPos pos, int itemCount, double score) {
    }

    private StorageTargetScorer() {
    }

    /**
     * @param exclude 本次不参与挑选的坐标（寻路失败后临时拉黑），可为 null
     * @return 得分最高的储物方块；没有合格目标时返回 null
     */
    public static Target findBest(ServerLevel level, BlockPos origin, LongSet exclude) {
        PlacedStorageRegistry registry = PlacedStorageRegistry.get(level);
        if (registry.isEmpty()) {
            return null;
        }

        // 配置值只读一次，避免在循环里反复取
        int radius = StashBusterConfig.SEARCH_RADIUS.get();
        double distancePenalty = StashBusterConfig.DISTANCE_PENALTY.get();
        int minimumItems = StashBusterConfig.MINIMUM_ITEM_COUNT.get();
        StashBusterConfig.CountMode mode = StashBusterConfig.COUNT_MODE.get();

        double radiusSqr = (double) radius * (double) radius;
        LongSet seenPairs = new LongOpenHashSet();
        LongSet stale = null;
        Target best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        LongIterator it = registry.positions().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();

            // 1) 纯算术淘汰：距离不合格的直接跳过，不碰世界
            BlockPos pos = BlockPos.of(key);
            double distSqr = origin.distSqr(pos);
            if (distSqr > radiusSqr) {
                continue;
            }
            if (exclude != null && exclude.contains(key)) {
                continue;
            }
            // 2) 未加载的区块读不到容器内容，跳过（条目保留，区块加载后即可用）
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.hasBlockEntity()) {
                // 该方块不该有方块实体（例如已被换成石头）：记录已失效
                if (stale == null) {
                    stale = new LongOpenHashSet();
                }
                stale.add(key);
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                // 区块刚加载、方块实体尚未就绪：跳过但保留记录，下次再说
                continue;
            }
            if (!(blockEntity instanceof Container)) {
                // 玩家放的告示牌、床、旗帜之类不是储物方块，清掉记录
                if (stale == null) {
                    stale = new LongOpenHashSet();
                }
                stale.add(key);
                continue;
            }
            // 排除名单（默认为末影箱）：不作为目标，但保留记录，这样改配置后能立刻生效
            if (StorageHelper.isExcluded(state)) {
                continue;
            }

            // 3) 双重箱子去重：同一口大箱子只算一次
            if (!seenPairs.add(StorageHelper.canonicalKey(level, pos, state))) {
                continue;
            }

            int itemCount = StorageHelper.countItems(level, pos, state, mode);
            if (itemCount < minimumItems) {
                continue;
            }

            double score = itemCount - distancePenalty * Math.sqrt(distSqr);
            if (score > bestScore) {
                bestScore = score;
                best = new Target(pos, itemCount, score);
            }
        }

        if (stale != null) {
            for (long key : stale) {
                registry.remove(BlockPos.of(key));
            }
        }
        return best;
    }
}
