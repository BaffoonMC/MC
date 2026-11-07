package net.baffoon.brood;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The little black body other players see: a tick made of block displays that chase the (invisible) player.
 * They follow by teleporting every tick rather than riding, so the player can still teleport, use portals and mount things.
 * Local frame: +z is forward, +x is the bug's left, y is up. Origin is the player's feet.
 */
final class Bug {
    private static final BlockData SHELL = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData SAC = Material.OBSIDIAN.createBlockData();
    private static final BlockData EYE = Material.REDSTONE_BLOCK.createBlockData();
    private static final BlockData VEIN = Material.CRYING_OBSIDIAN.createBlockData();

    private final Player owner;
    private final float lift;            // fine height adjustment from the config
    private final List<BlockDisplay> parts = new ArrayList<>();
    private final BlockDisplay[] legs = new BlockDisplay[12];   // thigh and shin for each of six legs
    private final BlockDisplay[] feelers = new BlockDisplay[2];
    private int phase;

    Bug(Player owner, float lift) {
        this.owner = owner;
        this.lift = lift;
        // body, swollen sac at the back, a glowing seam down the sac, a head, two red eyes
        box(SHELL, 0, 0.09f, 0.02f, 0.20f, 0.10f, 0.20f, 0);
        box(SAC, 0, 0.11f, -0.15f, 0.24f, 0.16f, 0.22f, 0);
        box(VEIN, 0, 0.19f, -0.15f, 0.04f, 0.012f, 0.18f, 15);
        box(SHELL, 0, 0.08f, 0.16f, 0.12f, 0.08f, 0.09f, 0);
        box(EYE, 0.035f, 0.10f, 0.205f, 0.03f, 0.03f, 0.012f, 15);
        box(EYE, -0.035f, 0.10f, 0.205f, 0.03f, 0.03f, 0.012f, 15);
        for (int i = 0; i < 12; i++) legs[i] = stick(SHELL);
        for (int i = 0; i < 2; i++) feelers[i] = stick(SHELL);
        pose(false);
    }

    private BlockDisplay spawn(BlockData data, int light) {
        BlockDisplay d = owner.getWorld().spawn(owner.getLocation(), BlockDisplay.class, e -> {
            e.setBlock(data);
            e.setPersistent(false);
            e.setInterpolationDuration(3);
            e.setTeleportDuration(3);   // other players see players smoothed over about 3 ticks, so match it
            e.setViewRange(0.35f);
            e.setShadowRadius(0);
            if (light > 0) e.setBrightness(new Display.Brightness(light, light));
        });
        parts.add(d);
        return d;
    }

    private void box(BlockData data, float x, float y, float z, float w, float h, float l, int light) {
        BlockDisplay d = spawn(data, light);
        d.setTransformation(new Transformation(new Vector3f(x - w / 2, y - h / 2 - lift, z - l / 2),
                new Quaternionf(), new Vector3f(w, h, l), new Quaternionf()));
    }

    private BlockDisplay stick(BlockData data) {
        return spawn(data, 0);
    }

    /** A thin stick from {@code from} pointing along {@code dir} for {@code len}. */
    private void aim(BlockDisplay d, Vector3f from, Vector3f dir, float len, float t) {
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), dir.normalize(new Vector3f()));
        Vector3f off = q.transform(new Vector3f(0, t / 2, t / 2));
        Vector3f tr = new Vector3f(from).sub(off).sub(0, lift, 0);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(tr, q, new Vector3f(len, t, t), new Quaternionf()));
    }

    /** Six jointed legs in a tripod gait, two feelers that twitch. Call every couple of ticks. */
    void pose(boolean walking) {
        if (walking) phase++;
        double t = phase * 1.3;
        float swing = walking ? (float) Math.sin(t) * 0.4f : 0f;
        float lift = walking ? (float) Math.cos(t) * 0.035f : 0f;
        float[] base = {0.8f, 0.05f, -0.7f};    // front, middle, back, in radians off sideways
        float[] hipZ = {0.08f, 0.02f, -0.05f};
        for (int i = 0; i < 6; i++) {
            int row = i % 3;
            float side = i < 3 ? 1 : -1;
            boolean group = (row % 2 == 0) == (side > 0);
            float a = base[row] + (group ? swing : -swing);
            Vector3f out = new Vector3f(side * (float) Math.cos(a), 0, (float) Math.sin(a));
            Vector3f hip = new Vector3f(side * 0.08f, 0.09f, hipZ[row]);
            Vector3f knee = new Vector3f(out).mul(0.11f).add(hip).add(0, 0.07f, 0);
            Vector3f foot = new Vector3f(out).mul(0.25f).add(hip);
            foot.y = 0.004f + Math.max(0, group ? lift : -lift);
            Vector3f thigh = new Vector3f(knee).sub(hip), shin = new Vector3f(foot).sub(knee);
            aim(legs[i * 2], hip, thigh, thigh.length() + 0.01f, 0.024f);
            aim(legs[i * 2 + 1], knee, shin, shin.length(), 0.018f);
        }
        float tw = (float) Math.sin(phase * 0.7 + owner.getTicksLived() * 0.05) * 0.15f;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1 : -1;
            aim(feelers[i], new Vector3f(side * 0.03f, 0.11f, 0.20f), new Vector3f(side * 0.45f, 0.55f + side * tw, 1f), 0.13f, 0.012f);
        }
    }

    /** Move the bug onto the player and turn it to face where they walk. */
    void follow(Location at, float yaw) {
        Location l = at.clone();
        l.setYaw(yaw);
        l.setPitch(0);
        for (BlockDisplay d : parts) if (d.isValid()) d.teleport(l);
    }

    /** Still all there, in the player's world, and close enough to chase rather than slide across the map. */
    boolean intact(Location at) {
        for (BlockDisplay d : parts) {
            if (!d.isValid() || d.getWorld() != at.getWorld() || d.getLocation().distanceSquared(at) > 16) return false;
        }
        return true;
    }

    void remove() {
        for (BlockDisplay d : parts) d.remove();
        parts.clear();
    }
}
