package com.stashbuster;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 记录"玩家放置的方块"的坐标，按维度分别持久化到存档。
 *
 * <p>放置时凡是<b>带方块实体的方块</b>都会记录（因为放置事件发生在方块实体创建之前，
 * 那时无法判断它是不是容器）；到底算不算储物方块，在搜索时用
 * {@code 方块实体 instanceof Container} 判定，不是容器的条目会被顺手清掉。
 *
 * <p>用 {@code long}（{@link BlockPos#asLong()}）做键，避免为每个坐标分配 {@link BlockPos} 对象；
 * 整套逻辑的热路径是"遍历这个集合 + 距离判断"，与世界中方块总量无关。
 */
public final class PlacedStorageRegistry extends SavedData {

    private static final String DATA_NAME = "stashbuster_placed_storage";
    private static final String TAG_POSITIONS = "Positions";

    private static final SavedData.Factory<PlacedStorageRegistry> FACTORY =
            new SavedData.Factory<>(PlacedStorageRegistry::new, PlacedStorageRegistry::load, null);

    private final LongSet positions = new LongOpenHashSet();

    private PlacedStorageRegistry() {
    }

    /** 取（必要时创建）该维度的记录表。 */
    public static PlacedStorageRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(BlockPos pos) {
        if (this.positions.add(pos.asLong())) {
            this.setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (this.positions.remove(pos.asLong())) {
            this.setDirty();
        }
    }

    public boolean contains(BlockPos pos) {
        return this.positions.contains(pos.asLong());
    }

    public boolean isEmpty() {
        return this.positions.isEmpty();
    }

    /** 只读视图，调用方不得修改。 */
    public LongSet positions() {
        return this.positions;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(TAG_POSITIONS, this.positions.toLongArray());
        return tag;
    }

    private static PlacedStorageRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedStorageRegistry data = new PlacedStorageRegistry();
        for (long key : tag.getLongArray(TAG_POSITIONS)) {
            data.positions.add(key);
        }
        return data;
    }
}
