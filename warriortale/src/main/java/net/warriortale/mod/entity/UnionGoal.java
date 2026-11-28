package net.warriortale.mod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;

/**
 * The bed valve. A promised folk walks to a bed at night, together with
 * the partner. Both lie down (vanilla sleep pose - the blanket does the
 * censoring), creaks play on entry and once at the settle, hearts rise,
 * and after the timer a child appears. No home with a bed = no child.
 */
public class UnionGoal extends MoveToBlockGoal {
    private final FolkEntity folk;
    private int ceremony = -1;      // -1 idle; >=0 counting down
    private static final int CEREMONY_TICKS = 160;

    public UnionGoal(FolkEntity folk, double speed) {
        super(folk, speed, 24, 4);
        this.folk = folk;
    }

    private boolean isNightTime() {
        // 26.3 moved the day-time accessor; resolve it reflectively and
        // fall back to "always allowed" if it moved again. A cleanup
        // commit will pin the direct call once the probe names it.
        try {
            Object lvl = folk.level();
            java.lang.reflect.Method m;
            Object target = lvl;
            try {
                m = lvl.getClass().getMethod("getDayTime");
            } catch (NoSuchMethodException e) {
                target = lvl.getClass().getMethod("getLevelData").invoke(lvl);
                m = target.getClass().getMethod("getDayTime");
            }
            long t = ((Number) m.invoke(target)).longValue() % 24000L;
            return t >= 12542 && t <= 23459;   // vanilla bed-usable window
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public boolean canUse() {
        return folk.promisedPartner() != null
            && folk.unionCooldown == 0
            && !folk.isBaby()
            && isNightTime()
            && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return folk.promisedPartner() != null && (ceremony >= 0 || super.canContinueToUse());
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BedBlock
            && level.getBlockState(pos.above()).isAir();
    }

    @Override
    public void start() {
        super.start();
        ceremony = -1;
    }

    @Override
    public void tick() {
        FolkEntity partner = folk.promisedPartner();
        if (partner == null) return;

        if (ceremony < 0) {
            super.tick();
            // partner walks along with us
            if (folk.distanceTo(partner) > 3.0 && partner.getNavigation().isDone()) {
                partner.getNavigation().moveTo(folk, 0.6);
            }
            if (this.isReachedTarget() && folk.distanceTo(partner) < 3.0) {
                BlockPos bed = this.getMoveToTarget().below();
                // climb-in creaks, weight-scaled; the Gharr's timber groan is the joke
                folk.playCreak(bed, folk.getBbHeight());
                partner.playCreak(bed, partner.getBbHeight());
                folk.startSleeping(bed);
                partner.startSleeping(bed);
                ceremony = CEREMONY_TICKS;
            }
        } else {
            ceremony--;
            if (ceremony == CEREMONY_TICKS - 30) {
                // the settle: one slow strained creak, then quiet
                folk.playCreak(folk.blockPosition(), folk.getBbHeight() + partner.getBbHeight());
            }
            if (ceremony % 30 == 0 && folk.level() instanceof ServerLevel sl) {
                FolkEntity.spawnHearts(sl, folk, 2);
            }
            if (ceremony == 0 && folk.level() instanceof ServerLevel sl) {
                folk.stopSleeping();
                partner.stopSleeping();
                FolkEntity child = folk.makeChild(sl, partner);
                if (child != null) {
                    child.snapTo(folk.getX(), folk.getY(), folk.getZ(), 0, 0);
                    sl.addFreshEntity(child);
                    FolkEntity.spawnHearts(sl, child, 7);
                }
                folk.unionCooldown = 20 * 60 * 5;      // five minutes
                partner.unionCooldown = 20 * 60 * 5;
                folk.promisedTo = -1;
                partner.promisedTo = -1;
                ceremony = -1;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (folk.isSleeping()) folk.stopSleeping();
        ceremony = -1;
    }
}
