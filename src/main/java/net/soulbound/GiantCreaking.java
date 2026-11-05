package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Giant Creaking (/sb world creaking [size] | remove): a towering creaking that turns to watch you and, while it can
 * see you, charges a guardian's beam from its eyes. Hold its gaze for the whole charge and the beam kills you outright.
 * Break its line of sight (a wall, a tree, distance) and the beam fizzles. Fire and lava can't hurt it.
 *
 * The beam comes from an invisible, AI-less guardian kept at the Creaking's eyes: the game draws a guardian's beam to its
 * target by itself. Only the drawing is the guardian's; the timing and the kill are done here.
 */
final class GiantCreaking implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey key, eyeKey;
    private final Map<UUID, Integer> charge = new HashMap<>();     // creaking -> ticks charged
    private final Map<UUID, UUID> aim = new HashMap<>();           // creaking -> player being charged at
    private final Map<UUID, Long> rest = new HashMap<>();          // creaking -> tick it may fire again
    private BukkitTask task;
    private long tick;

    GiantCreaking(Soulbound plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "giant_creaking");
        this.eyeKey = new NamespacedKey(plugin, "giant_creaking_eye");
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("giant-creaking." + k, d); }

    boolean is(Entity e) { return e instanceof Creaking && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }

    String summon(Location at, double size) {
        double scale = size > 0 ? size : cfg("size", 4);
        Creaking c = at.getWorld().spawn(at, Creaking.class, cr -> {
            cr.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            cr.setPersistent(true);
            cr.setRemoveWhenFarAway(false);
            cr.customName(Component.text("Giant Creaking", NamedTextColor.GOLD));
            cr.setCustomNameVisible(false);
            set(cr, Attribute.SCALE, scale);
            set(cr, Attribute.MAX_HEALTH, cfg("health", 300));
            set(cr, Attribute.FOLLOW_RANGE, cfg("sight", 64));
            set(cr, Attribute.ATTACK_DAMAGE, cfg("melee-damage", 12));
            cr.setHealth(cfg("health", 300));
        });
        ensureEye(c);
        return "A Giant Creaking (" + scale + "x) stands up. It sees " + (int) cfg("sight", 64) + " blocks.";
    }

    private static void set(org.bukkit.entity.LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    String removeNear(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(32, 32, 32)) if (is(e)) { removeEye(e); e.remove(); n++; }
        return n == 0 ? "No Giant Creaking within 32 blocks." : "Removed " + n + " Giant Creaking" + (n == 1 ? "" : "s") + ".";
    }

    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L); }

    void stop() {
        if (task != null) task.cancel();
        for (var w : Bukkit.getWorlds()) for (Creaking c : w.getEntitiesByClass(Creaking.class)) if (is(c)) removeEye(c);
    }

    // ---------------------------------------------------------------- the eye

    private Location eyeSpot(Creaking c) {
        return c.getEyeLocation().add(c.getLocation().getDirection().setY(0).normalize().multiply(0.3 * c.getHeight() / 2.9));
    }

    private Guardian eye(Entity c) {
        String s = c.getPersistentDataContainer().get(eyeKey, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Bukkit.getEntity(UUID.fromString(s)) instanceof Guardian g && g.isValid() ? g : null; } catch (IllegalArgumentException ex) { return null; }
    }

    private Guardian ensureEye(Creaking c) {
        Guardian g = eye(c);
        if (g != null) return g;
        g = c.getWorld().spawn(eyeSpot(c), Guardian.class, gu -> {
            gu.setAI(false);
            gu.setInvisible(true);
            gu.setSilent(true);
            gu.setInvulnerable(true);
            gu.setGravity(false);
            gu.setCollidable(false);
            gu.setPersistent(false);
            gu.setRemoveWhenFarAway(false);
            AttributeInstance sc = gu.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(0.2);
            gu.getPersistentDataContainer().set(eyeKey, PersistentDataType.STRING, c.getUniqueId().toString());
        });
        c.getPersistentDataContainer().set(eyeKey, PersistentDataType.STRING, g.getUniqueId().toString());
        return g;
    }

    private void removeEye(Entity c) {
        Guardian g = eye(c);
        if (g != null) g.remove();
        charge.remove(c.getUniqueId()); aim.remove(c.getUniqueId());
    }

    // ---------------------------------------------------------------- watching and the beam

    private Player pick(Creaking c, double range) {
        Player best = null; double bd = range * range;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(c.getLocation());
            if (d < bd && c.hasLineOfSight(p)) { bd = d; best = p; }
        }
        return best;
    }

    private void tick() {
        tick++;
        double range = cfg("sight", 64);
        int need = (int) Math.round(cfg("charge-seconds", 3) * 20);
        long restTicks = Math.round(cfg("cooldown-seconds", 3) * 20);
        for (var w : Bukkit.getWorlds()) {
            if (w.getPlayers().isEmpty()) continue;
            for (Creaking c : w.getEntitiesByClass(Creaking.class)) {
                if (!is(c) || c.isDead()) continue;
                Guardian g = ensureEye(c);
                g.teleport(eyeSpot(c));
                c.setFireTicks(0);
                UUID id = c.getUniqueId();
                Player cur = aim.get(id) == null ? null : Bukkit.getPlayer(aim.get(id));
                boolean keep = cur != null && cur.getWorld().equals(w) && !cur.isDead()
                        && (cur.getGameMode() == GameMode.SURVIVAL || cur.getGameMode() == GameMode.ADVENTURE)
                        && cur.getLocation().distanceSquared(c.getLocation()) <= range * range && c.hasLineOfSight(cur);
                if (!keep) {
                    if (cur != null) fizzle(c, g);
                    if (tick < rest.getOrDefault(id, 0L)) continue;
                    cur = pick(c, range);
                    if (cur == null) continue;
                    aim.put(id, cur.getUniqueId());
                    charge.put(id, 0);
                    g.setTarget(cur);
                    g.setLaser(true);
                    w.playSound(c.getLocation(), Sound.BLOCK_CREAKING_HEART_HURT, 2f, 0.5f);
                }
                c.lookAt(cur);                                   // it turns to watch you
                int t = charge.merge(id, 1, Integer::sum);
                if (t % 10 == 0) cur.sendActionBar(Component.text("It's looking at you. Break its line of sight!", NamedTextColor.RED));
                if (t >= need) fire(c, g, cur, restTicks);
            }
        }
    }

    private void fizzle(Creaking c, Guardian g) {
        g.setLaser(false);
        g.setTarget(null);
        aim.remove(c.getUniqueId()); charge.remove(c.getUniqueId());
    }

    private void fire(Creaking c, Guardian g, Player p, long restTicks) {
        fizzle(c, g);
        rest.put(c.getUniqueId(), tick + restTicks);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.6f);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM, p.getLocation().add(0, 1, 0), 1);
        double dmg = cfg("beam-damage", 10000);
        p.damage(dmg, DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(c).withDirectEntity(g).build());
    }

    // ---------------------------------------------------------------- toughness

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Guardian g && g.getPersistentDataContainer().has(eyeKey, PersistentDataType.STRING)) { e.setCancelled(true); return; }
        if (!is(e.getEntity())) return;
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!is(e.getEntity())) return;
        removeEye(e.getEntity());
        e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_CREAKING_DEATH, 3f, 0.5f);
    }
}
