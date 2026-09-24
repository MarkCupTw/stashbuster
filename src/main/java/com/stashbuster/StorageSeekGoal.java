package com.stashbuster;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 让苦力怕去找并炸开"玩家放置的、物品最多的储物方块"。
 *
 * <p>关键设计：
 * <ul>
 *   <li>只在搜索半径内有玩家（旁观模式除外）时才激活。</li>
 *   <li>苦力怕一旦锁定玩家就让位——玩家优先，储物方块只是备选。</li>
 *   <li>不自己调用爆炸，只在抵达后点燃原版引信（{@code setSwellDir(1)}），
 *       之后爆炸强度、范围、掉落、自爆即死全部由原版 {@code Creeper#tick()} 接管。</li>
 *   <li>搜索有冷却、寻路有重算间隔、寻不动会放弃并临时拉黑该目标，避免原地死循环。</li>
 *   <li>附近有猫/豹猫时主动让位给原版躲猫行为。</li>
 * </ul>
 */
public class StorageSeekGoal extends Goal {

    /**
     * 必须<b>严格高于</b>原版 {@code SwellGoal} 的优先级 2。
     *
     * <p>这是踩过的坑，别再改回去（已核对 1.21.1 源码）：
     * <ol>
     *   <li>{@code SwellGoal.canUse()} 的第一项就是 {@code creeper.getSwellDir() > 0}——
     *       <b>只要引信被点燃，它就会启动</b>。</li>
     *   <li>它的 {@code tick()} 在没有锁定目标时执行 {@code setSwellDir(-1)}，把引信掐灭。</li>
     *   <li>它和本目标抢同一个 {@code Goal.Flag.MOVE}。</li>
     *   <li>{@code WrappedGoal.canBeReplacedBy} 用的是<b>严格小于</b>：
     *       {@code other.priority < this.priority}。</li>
     * </ol>
     * 所以只要本目标优先级大于等于 2，点燃引信的下一 tick 就会被 SwellGoal 抢走 flag 并掐灭，
     * 表现为"苦力怕站在箱子旁边就是不爆炸"。优先级取 1 即可压住它。
     *
     * <p>代价与补偿：优先级 1 也让本目标高于原版躲猫的 {@code AvoidEntityGoal}（优先级 3），
     * 所以本目标主动实现了让位逻辑（见 {@link #refreshContext}），把 MOVE 让给它。
     */
    public static final int PRIORITY = 1;

    /** 距离目标多近就点燃引信（2.5 格的平方）。 */
    private static final double DETONATE_DIST_SQR = 6.25D;
    /** 偏离目标过远就放弃这次行动（40 格的平方）。 */
    private static final double ABANDON_DIST_SQR = 1600.0D;
    /** 寻路重算间隔（tick）。 */
    private static final int REPATH_INTERVAL = 10;
    /** 寻路连续无进展多久就放弃目标（tick）。 */
    private static final int STUCK_GIVE_UP_TICKS = 60;
    /** 放弃后该目标被拉黑、且不再搜索的时长（tick）。 */
    private static final int FAIL_COOLDOWN_TICKS = 200;
    /** 移动速度，与原版苦力怕追击速度一致。 */
    private static final double MOVE_SPEED = 1.0D;

    /** 原版 AvoidEntityGoal 对猫/豹猫的搜索半径（水平）。 */
    private static final double CAT_AVOID_DISTANCE = 6.0D;
    /** 原版 AvoidEntityGoal 搜索盒的垂直外扩量。 */
    private static final double CAT_AVOID_VERTICAL = 3.0D;
    /** "附近情况"（怕猫 + 玩家激活条件）的缓存间隔（tick），避免每 tick 扫实体和玩家。 */
    private static final int CONTEXT_CHECK_INTERVAL = 10;

    private final Creeper creeper;
    private final LongSet excluded = new LongOpenHashSet();

    private BlockPos target;
    private int nextSearchTick;
    private int repathTicks;
    private int stuckTicks;
    private int exclusionExpiryTick;
    private boolean catNearby;
    private boolean playerGateUnsatisfied;
    private int nextContextCheckTick;

    public StorageSeekGoal(Creeper creeper) {
        this.creeper = creeper;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!StashBusterConfig.ENABLED.get()) {
            return false;
        }
        if (!this.creeper.isAlive() || this.creeper.isNoAi()) {
            return false;
        }
        // 玩家优先：已经盯上玩家时不做别的事。
        // 原版苦力怕不会锁定创造模式与旁观者（Player#canBeSeenAsEnemy 对 invulnerable 返回 false），
        // 因此这里天然就是"生存/冒险玩家优先"。
        if (this.creeper.getTarget() != null) {
            return false;
        }
        if (!(this.creeper.level() instanceof ServerLevel level)) {
            return false;
        }

        // 冷却闸门放在最前面：本方法在目标未运行时每 tick 都会被调用，这里必须极廉价
        int now = this.creeper.tickCount;
        if (now < this.nextSearchTick) {
            return false;
        }

        int interval = Math.max(1, StashBusterConfig.SCORE_UPDATE_INTERVAL_TICKS.get());
        this.refreshContext(level);

        // 附近有猫就先不去搜刮，把 MOVE 让给原版 AvoidEntityGoal。
        // 这里刻意不刷新搜索冷却，猫一走就能立刻恢复搜刮。
        if (this.catNearby) {
            return false;
        }
        // 半径内没有非旁观玩家就不激活。这里刷新冷却，省掉每 tick 的扫描。
        if (this.playerGateUnsatisfied) {
            this.nextSearchTick = now + interval;
            return false;
        }

        this.pruneExclusions(now);
        this.nextSearchTick = now + interval;

        StorageTargetScorer.Target found =
                StorageTargetScorer.findBest(level, this.creeper.blockPosition(), this.excluded);
        if (found == null) {
            this.target = null;
            return false;
        }
        this.target = found.pos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.creeper.isAlive()) {
            return false;
        }
        if (this.creeper.getTarget() != null) {
            return false;
        }
        // 引信已点燃就交给原版走完爆炸流程。
        // 也刻意放在怕猫/激活检查之前：原版 SwellGoal(2) 优先级高于 AvoidEntityGoal(3)，
        // 也就是原版引信点燃后不会被猫打断，这里保持一致，
        // 否则会出现"猫路过就把引信吹灭"这种原版没有的行为。
        if (this.creeper.getSwellDir() > 0) {
            return true;
        }
        if (!(this.creeper.level() instanceof ServerLevel level)) {
            return false;
        }
        // 持续检查（不只是启动时检查一次）：猫靠近就让位给原版躲猫；
        // 半径内没有非旁观玩家就放弃——保证"只有玩家 24 格内它才会主动来炸"
        // 这条规则在整段寻路过程中都成立，而不只是出发那一刻成立。
        this.refreshContext(level);
        if (this.catNearby || this.playerGateUnsatisfied) {
            return false;
        }
        if (!level.isLoaded(this.target)) {
            return false;
        }
        if (!StorageHelper.isContainerAt(level, this.target)) {
            return false;
        }
        return this.creeper.distanceToSqr(Vec3.atCenterOf(this.target)) <= ABANDON_DIST_SQR;
    }

    @Override
    public void start() {
        this.repathTicks = 0;
        this.stuckTicks = 0;
        this.pathToTarget();
    }

    @Override
    public void stop() {
        this.creeper.getNavigation().stop();
        // 只在我们自己点燃、且玩家没有介入的情况下去取消引信，
        // 否则会跟原版 SwellGoal 抢状态。
        if (this.target != null
                && this.creeper.getTarget() == null
                && this.creeper.getSwellDir() > 0
                && this.creeper.isAlive()) {
            this.creeper.setSwellDir(-1);
        }
        this.target = null;
        this.repathTicks = 0;
        this.stuckTicks = 0;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }

        double distSqr = this.creeper.distanceToSqr(Vec3.atCenterOf(this.target));
        if (distSqr <= DETONATE_DIST_SQR) {
            this.creeper.getNavigation().stop();
            if (this.creeper.getSwellDir() <= 0) {
                StashBuster.LOGGER.debug("[Stash Buster] {} 点燃引信 @ {}", this.creeper, this.target);
                this.creeper.setSwellDir(1);
            }
            return;
        }

        // 已经在膨胀了就别乱走，让原版把爆炸走完
        if (this.creeper.getSwellDir() > 0) {
            return;
        }

        if (--this.repathTicks <= 0) {
            this.repathTicks = REPATH_INTERVAL;
            this.pathToTarget();
        }

        if (this.creeper.getNavigation().isDone()) {
            // 路走完了却还没到目标：多半是不可达，累计到阈值就放弃
            if (++this.stuckTicks >= STUCK_GIVE_UP_TICKS) {
                this.giveUp();
            }
        } else {
            this.stuckTicks = 0;
        }
    }

    private void pathToTarget() {
        if (this.target == null) {
            return;
        }
        // 往方块中心寻路：容器本身不可站立，寻路会自动停在最接近的可达位置
        this.creeper.getNavigation().moveTo(
                this.target.getX() + 0.5D,
                this.target.getY(),
                this.target.getZ() + 0.5D,
                MOVE_SPEED);
    }

    /** 目标不可达：拉黑它并进入冷却，避免无限重试同一个够不着的目标。 */
    private void giveUp() {
        if (this.target != null) {
            this.excluded.add(this.target.asLong());
            this.exclusionExpiryTick = this.creeper.tickCount + FAIL_COOLDOWN_TICKS;
        }
        this.creeper.getNavigation().stop();
        this.target = null;
        this.stuckTicks = 0;
        this.repathTicks = 0;
        this.nextSearchTick = this.creeper.tickCount + FAIL_COOLDOWN_TICKS;
    }

    private void pruneExclusions(int now) {
        if (!this.excluded.isEmpty() && now > this.exclusionExpiryTick) {
            this.excluded.clear();
        }
    }

    /**
     * 半径内是否存在玩家（<b>只排除旁观模式</b>）。生存、创造、冒险模式都算。
     */
    private static boolean hasNonSpectatorPlayerNearby(ServerLevel level, BlockPos origin) {
        double radius = StashBusterConfig.SEARCH_RADIUS.get();
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.blockPosition().distSqr(origin) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 {@link #CONTEXT_CHECK_INTERVAL} 的间隔刷新两项"附近情况"，两者共用一次缓存：
     *
     * <ul>
     *   <li>{@code catNearby}：是否需要让位给原版躲猫行为。
     *       让位条件近似原版 {@code AvoidEntityGoal}（原版给苦力怕注册了猫和豹猫各一条，
     *       半径 6 格）：搜索盒取 {@code boundingBox.inflate(6.0, 3.0, 6.0)}，
     *       并要求苦力怕对它有视线（原版用的是 {@code TargetingConditions.forCombat()}，
     *       默认包含视线判定）。原版那套还额外要求"能找到逃跑点并成功寻路"，
     *       那部分交给原版自己负责——我们只需把 {@code Goal.Flag.MOVE} 让出来。</li>
     *   <li>{@code playerGateUnsatisfied}：半径内是否缺少非旁观玩家（仅当配置开启时才有意义）。</li>
     * </ul>
     *
     * <p>做成缓存是因为 {@code canContinueToUse()} 每 tick 都会被调用，
     * 而这两项检查都要扫实体/玩家。
     */
    private void refreshContext(ServerLevel level) {
        int now = this.creeper.tickCount;
        if (now < this.nextContextCheckTick) {
            return;
        }
        this.nextContextCheckTick = now + CONTEXT_CHECK_INTERVAL;
        this.catNearby = detectCatNearby(level);
        this.playerGateUnsatisfied = StashBusterConfig.REQUIRE_NEARBY_PLAYER.get()
                && !hasNonSpectatorPlayerNearby(level, this.creeper.blockPosition());
    }

    private boolean detectCatNearby(ServerLevel level) {
        AABB box = this.creeper.getBoundingBox()
                .inflate(CAT_AVOID_DISTANCE, CAT_AVOID_VERTICAL, CAT_AVOID_DISTANCE);
        for (Cat cat : level.getEntitiesOfClass(Cat.class, box)) {
            if (cat.isAlive() && this.creeper.getSensing().hasLineOfSight(cat)) {
                return true;
            }
        }
        for (Ocelot ocelot : level.getEntitiesOfClass(Ocelot.class, box)) {
            if (ocelot.isAlive() && this.creeper.getSensing().hasLineOfSight(ocelot)) {
                return true;
            }
        }
        return false;
    }
}
