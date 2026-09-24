package com.stashbuster;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 配置。
 *
 * <p>注意：{@code ModConfigSpec} 的取值只能在配置加载完成后调用，
 * 因此所有 {@code .get()} 都发生在游戏内的事件/AI 逻辑中，绝不在静态初始化里取值。
 */
public final class StashBusterConfig {

    /** 物品数量的统计口径。 */
    public enum CountMode {
        /** 累加每个格子的堆叠数量（"东西最多"）。 */
        TOTAL_ITEMS,
        /** 只数非空格子数量（"占的格子最多"）。 */
        OCCUPIED_SLOTS
    }

    public static final ModConfigSpec SPEC;

    // [general]
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.BooleanValue REQUIRE_NEARBY_PLAYER;
    public static final ModConfigSpec.IntValue SCORE_UPDATE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MINIMUM_ITEM_COUNT;

    // [targets]
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_BLOCKS;

    // [scoring]
    public static final ModConfigSpec.DoubleValue DISTANCE_PENALTY;
    public static final ModConfigSpec.EnumValue<CountMode> COUNT_MODE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("总开关").push("general");
        ENABLED = b.comment("关闭后苦力怕恢复原版行为，已记录的数据仍会保留。")
                .define("enabled", true);
        SEARCH_RADIUS = b.comment("储物方块的搜索半径（格）。未加载区块内的方块不会被读取。")
                .defineInRange("searchRadius", 24, 1, 128);
        REQUIRE_NEARBY_PLAYER = b.comment("""
                是否要求搜索半径内存在玩家才激活此行为。默认 true。
                这个"玩家"只把**旁观模式**排除在外——生存、创造、冒险模式都算。
                （所以创造模式下建基地时，苦力怕照样会来炸箱子；纯旁观观察时不会。）
                关掉它的后果：苦力怕所在的区块只要被加载（例如出生点强加载、区块加载器），
                哪怕没有任何玩家在线，它也会去搜刮储物方块。""")
                .define("requireNearbyPlayer", true);
        SCORE_UPDATE_INTERVAL_TICKS = b.comment("重新挑选目标的最小间隔（tick），避免每 tick 重算。")
                .defineInRange("scoreUpdateIntervalTicks", 20, 1, 200);
        MINIMUM_ITEM_COUNT = b.comment("容器内物品少于该值时不作为目标（0 表示空容器也炸）。")
                .defineInRange("minimumItemCount", 1, 0, Integer.MAX_VALUE);
        b.pop();

        b.comment("哪些方块算目标").push("targets");
        EXCLUDED_BLOCKS = b.comment("""
                不参与搜刮的方块 ID 列表。
                判定"是不是储物方块"用的是"方块实体实现了 Container"，而不是白名单，
                所以木桶、潜影盒、漏斗、发射器、投掷器、熔炉系、酿造台、合成器、
                饰纹陶罐、雕纹书架，以及其它 mod 的标准容器都会被自动纳入。
                末影箱默认列在这里：它的内容属于玩家个人的末影储物、并不存在于世界里。
                （实际上 EnderChestBlockEntity 根本没实现 Container，本来就不会被选中，
                列出来是为了显式表达意图、并在将来原版改动时提供保护。）
                想排除更多方块（例如 minecraft:jukebox）往列表里追加 ID 即可。""")
                .defineListAllowEmpty("excludedBlocks", List.of("minecraft:ender_chest"), null, o -> o instanceof String);
        b.pop();

        b.comment("目标打分：score = 物品数 - 距离惩罚 * 距离").push("scoring");
        DISTANCE_PENALTY = b.comment("每 1 格距离扣多少分。默认 1.5，约等于满箱可以压过 10 格的距离差。")
                .defineInRange("distancePenalty", 1.5D, 0.0D, 1000.0D);
        COUNT_MODE = b.comment("物品数量的统计口径。TOTAL_ITEMS = 堆叠数量总和；OCCUPIED_SLOTS = 非空格子数。")
                .defineEnum("countMode", CountMode.TOTAL_ITEMS);
        b.pop();

        SPEC = b.build();
    }

    private StashBusterConfig() {
    }
}
