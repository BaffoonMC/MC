package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
    private final NamespacedKey key, eyeKey, wildKey;
    private final Map<UUID, Integer> charge = new HashMap<>();     // creaking -> ticks charged
    private final Map<UUID, UUID> aim = new HashMap<>();           // creaking -> player being charged at
    private final Map<UUID, Long> rest = new HashMap<>();          // creaking -> tick it may fire again
    private final Map<UUID, Map<UUID, Double>> hurt = new HashMap<>();   // creaking -> player -> damage dealt
    private BukkitTask task;
    private long tick;

    GiantCreaking(Soulbound plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "giant_creaking");
        this.eyeKey = new NamespacedKey(plugin, "giant_creaking_eye");
        this.wildKey = new NamespacedKey(plugin, "giant_creaking_wild");
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("giant-creaking." + k, d); }

    boolean is(Entity e) { return e instanceof Creaking && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }

    String summon(Location at, double size) { return summon(at, size, false); }

    String summon(Location at, double size, boolean wild) {
        double scale = size > 0 ? size : cfg("size", 16);
        Creaking c = at.getWorld().spawn(at, Creaking.class, cr -> {
            cr.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            cr.setPersistent(!wild);            // a wild one isn't saved: it's gone when its chunk unloads
            cr.setRemoveWhenFarAway(false);     // but it never wanders off mid-fight like a normal monster would
            if (wild) cr.getPersistentDataContainer().set(wildKey, PersistentDataType.BYTE, (byte) 1);
            cr.customName(Component.text(name(), NamedTextColor.GOLD));
            cr.setCustomNameVisible(false);
            set(cr, Attribute.SCALE, scale);
            set(cr, Attribute.MAX_HEALTH, cfg("health", 300));
            set(cr, Attribute.FOLLOW_RANGE, cfg("sight", 64));
            set(cr, Attribute.ATTACK_DAMAGE, cfg("melee-damage", 12));
            cr.setHealth(cfg("health", 300));
        });
        ensureEye(c);
        return name() + " (" + fmt(scale) + "x) stands up. It sees " + (int) cfg("sight", 64) + " blocks.";
    }

    private String name() { return plugin.getConfig().getString("giant-creaking.name", "Jotun"); }

    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

    private static void set(org.bukkit.entity.LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    String removeNear(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(48, 64, 48)) if (is(e)) { removeEye(e); e.remove(); n++; }
        return n == 0 ? "No " + name() + " within 48 blocks." : "Removed " + n + " " + name() + (n == 1 ? "" : "s") + ".";
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        wildTask = Bukkit.getScheduler().runTaskTimer(plugin, this::wild, 200L, 20L * Math.max(5, (long) cfg("wild.check-seconds", 30)));
    }

    private BukkitTask wildTask;

    /**
     * In the wild: Jotuns walk the last stretch of Moloch's realm, just before The Wall (between wild.from and wild.to
     * blocks from the temple). Every check, each player out there has wild.chance of one rising wild.distance blocks
     * away, unless one is already near them or wild.max are about. Wild ones sink back into the ground once nobody is within
     * 200 blocks (and aren't saved when their chunk unloads).
     */
    private void wild() {
        if (!plugin.getConfig().getBoolean("giant-creaking.wild.enabled", true) || plugin.zones() == null) return;
        World w = plugin.zones().world();
        if (w == null || w.getPlayers().isEmpty()) return;
        double from = cfg("wild.from", 6300), to = cfg("wild.to", 6800);
        int alive = 0;
        for (Creaking c : w.getEntitiesByClass(Creaking.class)) {
            if (!is(c)) continue;
            // wild ones nobody is anywhere near go back into the ground
            if (c.getPersistentDataContainer().has(wildKey, PersistentDataType.BYTE) && c.getLocation().getNearbyPlayers(200).isEmpty()) { removeEye(c); c.remove(); continue; }
            alive++;
        }
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (Player p : w.getPlayers()) {
            if (alive >= (int) cfg("wild.max", 3)) return;
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            double d = Math.hypot(p.getLocation().getX(), p.getLocation().getZ());
            if (d < from - 64 || d > to + 64) continue;
            if (r.nextDouble() >= cfg("wild.chance", 0.15)) continue;
            boolean near = false;
            for (Entity e : p.getNearbyEntities(160, 128, 160)) if (is(e)) { near = true; break; }
            if (near) continue;
            for (int tries = 0; tries < 12; tries++) {
                double ang = r.nextDouble(Math.PI * 2), dist = cfg("wild.distance", 90) * (0.8 + r.nextDouble() * 0.4);
                double x = p.getLocation().getX() + Math.cos(ang) * dist, z = p.getLocation().getZ() + Math.sin(ang) * dist;
                double rr = Math.hypot(x, z);
                if (rr < from || rr > to) continue;
                if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) continue;
                org.bukkit.block.Block ground = w.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z));
                if (ground.getY() <= w.getMinHeight() + 1 || ground.isLiquid() && ground.getType() == org.bukkit.Material.WATER) continue;
                summon(ground.getLocation().add(0.5, 1, 0.5), 0, true);
                alive++;
                plugin.getLogger().info("A wild " + name() + " rose at " + (int) x + " " + (int) z + " in Moloch's realm.");
                break;
            }
        }
    }

    void stop() {
        if (task != null) task.cancel();
        if (wildTask != null) wildTask.cancel();
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

    /** Who hurt it, and how much: arrows, tridents and the like count for whoever fired them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!is(e.getEntity())) return;
        Player p = e.getDamageSource().getCausingEntity() instanceof Player pl ? pl
                : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        if (p == null) return;
        hurt.computeIfAbsent(e.getEntity().getUniqueId(), k -> new HashMap<>()).merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    /**
     * Its fall: every fighter gets XP straight into them (fair in a crowd, no scramble for orbs), a little more bursts out
     * where it fell, and whoever hurt it most takes Jotun's Crown.
     */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!is(e.getEntity())) return;
        removeEye(e.getEntity());
        Location at = e.getEntity().getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_CREAKING_DEATH, 3f, 0.5f);
        e.getDrops().clear();
        e.setDroppedExp((int) cfg("xp-burst", 100));
        Map<UUID, Double> fought = hurt.remove(e.getEntity().getUniqueId());
        if (fought == null || fought.isEmpty()) return;
        int each = (int) cfg("xp-each", 600);
        UUID top = null; double most = -1;
        for (var en : fought.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && p.isOnline()) { p.giveExp(each); p.sendMessage(Component.text("Jotun falls. You feel wiser for having survived it.", NamedTextColor.GOLD)); }
            if (en.getValue() > most) { most = en.getValue(); top = en.getKey(); }
        }
        Player best = top == null ? null : Bukkit.getPlayer(top);
        if (plugin.cute() == null) return;
        org.bukkit.inventory.ItemStack crown = plugin.cute().create(Cute.Kind.JOTUN);
        if (best != null && best.isOnline()) {
            for (org.bukkit.inventory.ItemStack left : best.getInventory().addItem(crown).values()) best.getWorld().dropItemNaturally(best.getLocation(), left);
            best.sendMessage(Component.text("You hurt it most. Its crown is yours.", NamedTextColor.GOLD));
        } else {
            at.getWorld().dropItemNaturally(at, crown);
        }
    }
}
