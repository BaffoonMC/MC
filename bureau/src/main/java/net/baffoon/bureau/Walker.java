package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataType;

/**
 * A lizard that walks: an invisible, harmless husk does the pathfinding (doors, stairs, round walls), and a Mannequin
 * in the lizard's suit is moved onto it every tick, so players only ever see the lizard. Neither is saved with the
 * world; they exist only while the Bureau needs them.
 */
final class Walker {
    static NamespacedKey key;   // set by Bureau: marks both halves
    final Husk driver;
    final Mannequin body;
    final NpcBody npc;      // set instead of driver and body when Citizens runs the walker
    private int frozenUntil;
    private int age;

    private Walker(Husk driver, Mannequin body) { this.driver = driver; this.body = body; this.npc = null; }
    private Walker(NpcBody npc) { this.driver = null; this.body = null; this.npc = npc; }

    /** Null if something (spawn protection, another plugin) refused to let it appear. */
    static Walker spawn(Bureau plugin, Location at, Lizards.Role role, Component name, double speed) {
        if (plugin.citizens()) {
            try {
                NpcBody n = NpcBody.spawn(plugin, at, role, name, speed);
                if (n != null) return new Walker(n);
            } catch (Throwable t) {
                plugin.getLogger().warning("Citizens couldn't make a walking lizard (" + t + "); using the built-in walker.");
            }
        }
        plugin.spawning = true;
        Husk h;
        Mannequin m;
        try {
            h = at.getWorld().spawn(at, Husk.class, x -> {
                x.setInvisible(true);
                x.setSilent(true);
                x.setInvulnerable(true);
                x.setPersistent(false);
                x.setRemoveWhenFarAway(false);
                x.setCanPickupItems(false);
                x.setCollidable(false);
                x.setAdult();
                x.setShouldBurnInDay(false);
                x.getEquipment().clear();
                x.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                var sp = x.getAttribute(Attribute.MOVEMENT_SPEED);
                if (sp != null) sp.setBaseValue(speed);
                var fr = x.getAttribute(Attribute.FOLLOW_RANGE);
                if (fr != null) fr.setBaseValue(64);
            });
            m = at.getWorld().spawn(at, Mannequin.class, x -> {
                x.setProfile(Lizards.skin(role));
                x.setPersistent(false);
                x.setInvulnerable(true);
                x.setGravity(false);
                x.setCollidable(false);
                x.customName(name);
                x.setCustomNameVisible(true);
                x.setDescription(null);
                x.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            });
        } finally {
            plugin.spawning = false;
        }
        if (!h.isValid() || !m.isValid()) { h.remove(); m.remove(); return null; }
        return new Walker(h, m);
    }

    static boolean isPart(Entity e) { return e != null && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }

    boolean valid() { return npc != null ? npc.valid() : driver.isValid() && body.isValid(); }
    boolean owns(Entity e) { return e != null && (npc != null ? e.equals(npc.entity()) : e.equals(driver) || e.equals(body)); }
    Location at() { return npc != null ? npc.at() : driver.getLocation(); }
    /** True when Sentinel is doing this walker's following and fighting. */
    boolean sentinel() { return npc != null && npc.sentinel; }
    boolean guard(org.bukkit.entity.Player client, double damage, double health, boolean players) { return npc != null && npc.guard(client, damage, health, players); }
    void target(java.util.UUID who) { if (npc != null) npc.target(who); }
    /** A Sentinel bodyguard: he deals and takes real damage, unlike every other walker. */
    static boolean fighter(Entity e) {
        if (e == null) return false;
        Byte b = e.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b != null && b == 2;
    }

    void walkTo(LivingEntity target, double speedMul) {
        if (age < frozenUntil) return;
        if (npc != null) { npc.walkTo(target, speedMul); return; }
        driver.getPathfinder().moveTo(target, speedMul);
    }

    void walkTo(Location to, double speedMul) {
        if (age < frozenUntil) return;
        if (npc != null) { npc.walkTo(to, speedMul); return; }
        driver.getPathfinder().moveTo(to, speedMul);
    }

    void stop() { if (npc != null) npc.stop(); else driver.getPathfinder().stopPathfinding(); }

    /** Stagger: stand still for ticks. */
    void freeze(int ticks) { frozenUntil = age + ticks; stop(); }
    boolean frozen() { return age < frozenUntil; }

    void teleport(Location l) { if (npc != null) npc.teleport(l); else { driver.teleport(l); body.teleport(l); } }

    /** Every tick: the lizard stands where the husk is, facing what it's after. */
    void tick(Location lookAt) {
        age++;
        if (age < frozenUntil) stop();
        if (npc != null) { if (npc.sentinel) return; if (lookAt != null && age % 5 == 0 && lookAt.getWorld() == npc.at().getWorld()) npc.face(lookAt); return; }
        Location l = driver.getLocation();
        if (lookAt != null && lookAt.getWorld() == l.getWorld()) {
            double dx = lookAt.getX() - l.getX(), dz = lookAt.getZ() - l.getZ();
            if (dx * dx + dz * dz > 1e-4) l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        l.setPitch(0);
        body.teleport(l);
    }

    void remove() { if (npc != null) npc.remove(); else { driver.remove(); body.remove(); } }
}
