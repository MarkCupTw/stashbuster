package com.stashbuster;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * 储物方块的底层操作。
 *
 * <p><b>本类是所有"版本敏感 API"与"什么算储物方块"的唯一收敛点。</b>
 *
 * <p>判定标准是<b>方块实体是否实现 {@link Container}</b>，而不是写死一份方块白名单。
 * 这样自动覆盖木桶、潜影盒、漏斗、发射器、投掷器、熔炉/高炉/烟熏炉、酿造台、合成器、
 * 饰纹陶罐、雕纹书架，以及其它 mod 的标准容器；末影箱则天然被排除，
 * 因为 {@code EnderChestBlockEntity} 并没有实现 {@code Container}
 * （它的内容属于玩家个人的末影储物，不存在于世界里）。
 */
public final class StorageHelper {

    /** 配置里 excludedBlocks 的解析缓存，避免每次候选都去查注册表。 */
    private static List<? extends String> cachedRawIds = null;
    private static Set<Block> cachedExcluded = Set.of();

    private StorageHelper() {
    }

    /** 该位置的方块实体是不是一个容器（真正的"是不是储物方块"判定）。 */
    public static boolean isContainerAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container;
    }

    public static Container containerAt(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    /** 是否在配置的排除列表里。列表变动时会重新解析。 */
    public static boolean isExcluded(BlockState state) {
        List<? extends String> raw = StashBusterConfig.EXCLUDED_BLOCKS.get();
        if (!raw.equals(cachedRawIds)) {
            cachedRawIds = List.copyOf(raw);
            cachedExcluded = resolveExcluded(cachedRawIds);
        }
        return cachedExcluded.contains(state.getBlock());
    }

    private static Set<Block> resolveExcluded(List<? extends String> ids) {
        Set<Block> resolved = new HashSet<>();
        for (String id : ids) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) {
                StashBuster.LOGGER.warn("[Stash Buster] excludedBlocks 里有无效的方块 ID：{}", id);
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
            if (block == null) {
                StashBuster.LOGGER.warn("[Stash Buster] excludedBlocks 里的方块不存在：{}", id);
                continue;
            }
            resolved.add(block);
        }
        return resolved;
    }

    /**
     * 双重箱子的另一半位置；单箱子与非箱子容器一律返回空。
     * 只有 {@link ChestBlock}（含陷阱箱，它是 ChestBlock 的子类）才有"两半"的概念。
     */
    public static Optional<BlockPos> partnerPos(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return Optional.empty();
        }
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return Optional.empty();
        }
        Direction connected = ChestBlock.getConnectedDirection(state);
        BlockPos partner = pos.relative(connected);
        if (!(level.getBlockState(partner).getBlock() instanceof ChestBlock)) {
            return Optional.empty();
        }
        return Optional.of(partner);
    }

    /**
     * 双重箱子的归一化键：取两半中坐标较小者。
     * 用于去重，避免同一口大箱子被当成两个目标、打分翻倍。非箱子容器返回自身坐标。
     */
    public static long canonicalKey(BlockGetter level, BlockPos pos, BlockState state) {
        return partnerPos(level, pos, state)
                .map(partner -> Math.min(partner.asLong(), pos.asLong()))
                .orElse(pos.asLong());
    }

    /**
     * 统计一个容器（含双重箱子的两半）的物品数量。
     *
     * <p>已核对 1.21.1 源码：{@code ChestBlockEntity} 的 {@code getContainerSize()} 固定返回 27，
     * 两半各持有自己的 27 格，大箱子只是被 {@code CompoundContainer} 包装起来呈现为 54 格。
     * 所以"两半各自计数后相加"是正确的，不会重复也不会漏。
     */
    public static int countItems(BlockGetter level, BlockPos pos, BlockState state, StashBusterConfig.CountMode mode) {
        int total = 0;

        Container self = containerAt(level, pos);
        if (self != null) {
            total += countItems(self, mode);
        }

        Optional<BlockPos> partner = partnerPos(level, pos, state);
        if (partner.isPresent()) {
            Container other = containerAt(level, partner.get());
            // 只有两半确实是两个不同容器时才相加，防止"共享同一容器"的实现被重复计数
            if (other != null && other != self) {
                total += countItems(other, mode);
            }
        }
        return total;
    }

    public static int countItems(Container container, StashBusterConfig.CountMode mode) {
        int total = 0;
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (mode == StashBusterConfig.CountMode.TOTAL_ITEMS) {
                total += stack.getCount();
            } else {
                total++;
            }
        }
        return total;
    }
}
