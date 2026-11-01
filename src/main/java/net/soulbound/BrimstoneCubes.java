package net.soulbound;

import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SulfurCube;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brimstone Cubes: lumps of Hell's sulfur that drifted up into the Seven Heavens. They're vanilla sulfur cubes grown
 * big enough to hold a person, and they hop after anyone out on the outer islands. Touch one and it swallows you:
 * a few seconds trapped inside, burning slowly, then it spits you out in a random direction - sometimes over the void,
 * where Heaven's Slow Falling is all that gives you time to save yourself.
 *
 * They come and go like the Zephyrs: one per player at most, never near God's island or during His fight, and gone
 * once nobody is near. You can't bucket, shear or feed them.
 */
final class BrimstoneCubes implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey key;

    /** Player -> the engulfing cube and the tick it ends. */
    private record Hold(UUID cube, int until) {}
    private final Map<UUID, Hold> held = new HashMap<>();
    /** Player -> server tick until which no cube may swallow them again. */
    private final Map<UUID, Integer> spared = new HashMap<>();
    /** Cube -> tick until which it rests after spitting someone out. */
    private final Map<UUID, Integer> resting = new HashMap<>();
    private int now;
    private final NamespacedKey pinKey;

    BrimstoneCubes(Soulbound plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "brimstone_cube"); this.pinKey = new NamespacedKey(plugin, "engulfed"); }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnTick, 240L, 100L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    boolean is(Entity e) { return e instanceof SulfurCube && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }

    SulfurCube summon(Location at) {
        FileConfiguration c = plugin.getConfig();
        return at.getWorld().spawn(at, SulfurCube.class, s -> {
            s.setAdult();
            double hp = Math.max(1, c.getDouble("brimstone-cubes.health", 40));
            var mh = s.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) { mh.setBaseValue(hp); s.setHealth(hp); }
            var sc = s.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(Math.max(1, c.getDouble("brimstone-cubes.scale", 3.0)));
            s.setRemoveWhenFarAway(true);
            s.setCollidable(false);   // you walk straight into it, not bounce off
            s.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** For /sb: how big a fresh cube is, to check it can really hold a player. */
    String describe(SulfurCube s) {
        var b = s.getBoundingBox();
        return String.format(java.util.Locale.ROOT, "Brimstone Cube: size %d, hitbox %.2f wide x %.2f tall", s.getSize(), b.getWidthX(), b.getHeight());
    }

    // ---------------------------------------------------------------- spawning

    private void spawnTick() {
        FileConfiguration c = plugin.getConfig();
        boolean on = c.getBoolean("brimstone-cubes.enabled", true);
        for (World w : plugin.getServer().getWorlds()) {
            if (w.getEnvironment() != World.Environment.THE_END) continue;
            for (SulfurCube s : w.getEntitiesByClass(SulfurCube.class))
                if (is(s) && (!on || nobodyNear(s.getLocation(), 96)) && !holding(s)) { nextHop.remove(s.getUniqueId()); nextPounce.remove(s.getUniqueId()); resting.remove(s.getUniqueId()); s.remove(); }
            if (!on || (plugin.god() != null && plugin.god().fighting())) continue;
            double minR = c.getDouble("brimstone-cubes.min-distance-from-centre", 300);
            int max = c.getInt("brimstone-cubes.max-per-player", 1);
            double chance = c.getDouble("brimstone-cubes.chance", 0.06);
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                Location l = p.getLocation();
                if (l.getX() * l.getX() + l.getZ() * l.getZ() < minR * minR) continue;
                if (ThreadLocalRandom.current().nextDouble() >= chance) continue;
                int near = 0;
                for (Entity e : p.getNearbyEntities(64, 64, 64)) if (is(e)) near++;
                if (near >= max) continue;
                Location at = ground(l, minR);
                if (at != null) {
                    summon(at);
                    w.spawnParticle(Particle.DUST_PLUME, at.clone().add(0, 1, 0), 30, 0.8, 0.6, 0.8, 0.02);
                }
            }
        }
    }

    private boolean holding(SulfurCube s) {
        for (Hold h : held.values()) if (h.cube.equals(s.getUniqueId())) return true;
        return false;
    }

    private static boolean nobodyNear(Location l, double r) {
        for (Player p : l.getWorld().getPlayers()) if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(l) < r * r) return false;
        return true;
    }

    /** A spot on island ground 16-32 blocks away with room for a big cube, out on the Heavens (never over the void). */
    private static Location ground(Location from, double minR) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = from.getWorld();
        for (int tries = 0; tries < 10; tries++) {
            double a = r.nextDouble(Math.PI * 2), d = 16 + r.nextDouble(16);
            int x = (int) Math.floor(from.getX() + Math.cos(a) * d), z = (int) Math.floor(from.getZ() + Math.sin(a) * d);
            if ((double) x * x + (double) z * z < minR * minR) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getHighestBlockYAt(x, z);
            if (y < w.getMinHeight() + 4 || Math.abs(y - from.getY()) > 24) continue;
            Block top = w.getBlockAt(x, y, z);
            if (!top.getType().isSolid() || top.getType() == Material.BEDROCK) continue;
            boolean room = true;
            for (int dy = 1; dy <= 4 && room; dy++) for (int dx = -1; dx <= 1 && room; dx++) for (int dz = -1; dz <= 1 && room; dz++)
                if (!w.getBlockAt(x + dx, y + dy, z + dz).isPassable()) room = false;
            if (!room) continue;
            boolean seen = false;
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(new Location(w, x, y, z)) < 12 * 12) { seen = true; break; }
            if (seen) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    // ---------------------------------------------------------------- hunting and engulfing

    private void tick() {
        now++;
        FileConfiguration c = plugin.getConfig();
        tickHeld(c);
        if (now % 2 != 0) return;
        double sight = c.getDouble("brimstone-cubes.sight", 24);
        for (World w : plugin.getServer().getWorlds()) {
            if (w.getEnvironment() != World.Environment.THE_END) continue;
            for (SulfurCube s : w.getEntitiesByClass(SulfurCube.class)) {
                if (!is(s) || s.isDead() || holding(s)) continue;
                if (resting.getOrDefault(s.getUniqueId(), 0) > now) continue;
                Player target = nearest(s, sight);
                if (target == null) continue;
                // swallow whoever it touches
                if (s.getBoundingBox().clone().expand(0.15).overlaps(target.getBoundingBox()) && spared.getOrDefault(target.getUniqueId(), 0) <= now) {
                    engulf(s, target, c);
                    continue;
                }
                chase(s, target, c, w);
            }
        }
        if (now % 4 == 0) {
            lastSeen.clear();
            for (World w : plugin.getServer().getWorlds())
                if (w.getEnvironment() == World.Environment.THE_END) for (Player p : w.getPlayers()) lastSeen.put(p.getUniqueId(), new Seen(p.getLocation().toVector(), now));
        }
    }

    /** Cube -> tick it may next jump; cube -> tick it may next pounce. */
    private final Map<UUID, Integer> nextHop = new HashMap<>(), nextPounce = new HashMap<>();

    /**
     * Agile hunting: it bounces again almost as soon as it lands, steers in the air towards you, and from close range
     * it pounces - a long, flat lunge aimed at where you're about to be, not where you are.
     */
    private void chase(SulfurCube s, Player target, FileConfiguration c, World w) {
        UUID id = s.getUniqueId();
        Vector me = s.getLocation().toVector(), them = target.getLocation().toVector();
        face(s, them.clone().subtract(me), c);
        if (!s.isOnGround()) {
            // air control: drift towards the target, up to a top speed
            Vector v = s.getVelocity(), to = them.clone().subtract(me).setY(0);
            if (to.lengthSquared() > 0.25) {
                to.normalize().multiply(c.getDouble("brimstone-cubes.air-steer", 0.06));
                double max = c.getDouble("brimstone-cubes.pounce-speed", 1.1);
                Vector h = new Vector(v.getX() + to.getX(), 0, v.getZ() + to.getZ());
                if (h.lengthSquared() > max * max) h.normalize().multiply(max);
                s.setVelocity(new Vector(h.getX(), v.getY(), h.getZ()));
            }
            return;
        }
        if (nextHop.getOrDefault(id, 0) > now) return;
        double dist = them.clone().subtract(me).setY(0).length();
        boolean pounce = dist <= c.getDouble("brimstone-cubes.pounce-range", 7) && nextPounce.getOrDefault(id, 0) <= now;
        Vector aim = them.clone();
        if (pounce) aim.add(runVelocity(target).multiply(c.getDouble("brimstone-cubes.pounce-lead-ticks", 8)));
        Vector to = aim.subtract(me).setY(0);
        if (to.lengthSquared() > 0.01) to.normalize();
        if (pounce) {
            s.setVelocity(to.clone().multiply(c.getDouble("brimstone-cubes.pounce-speed", 1.1)).setY(c.getDouble("brimstone-cubes.pounce-height", 0.42)));
            nextPounce.put(id, now + (int) (c.getDouble("brimstone-cubes.pounce-cooldown-seconds", 2.5) * 20));
            w.playSound(s.getLocation(), Sound.ENTITY_SLIME_ATTACK, 1.2f, 0.5f);
            w.spawnParticle(Particle.DUST_PLUME, s.getLocation().add(0, 0.3, 0), 12, 0.8, 0.1, 0.8, 0.02);
        } else {
            s.setVelocity(to.clone().multiply(c.getDouble("brimstone-cubes.hop-speed", 0.7)).setY(c.getDouble("brimstone-cubes.hop-height", 0.5)));
            w.playSound(s.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.8f, 0.6f);
        }
        nextHop.put(id, now + c.getInt("brimstone-cubes.hop-interval-ticks", 6));
    }

    /** A player's real running speed (blocks per tick), from where they were a moment ago: the server's own velocity for players is mostly zero. */
    private record Seen(Vector at, int tick) {}
    private final Map<UUID, Seen> lastSeen = new HashMap<>();

    private Vector runVelocity(Player p) {
        Seen was = lastSeen.get(p.getUniqueId());
        Vector at = p.getLocation().toVector();
        if (was == null || now - was.tick <= 0 || now - was.tick > 10) return new Vector();
        Vector v = at.subtract(was.at).setY(0).multiply(1.0 / (now - was.tick));
        return v.lengthSquared() > 1 ? v.normalize() : v;   // ignore teleports
    }

    /** Turn its face towards the player (the model's face points backwards from the yaw, hence face-offset 180). */
    private static void face(SulfurCube s, Vector to, FileConfiguration c) {
        if (to.getX() * to.getX() + to.getZ() * to.getZ() < 0.01) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(-to.getX(), to.getZ())) + c.getDouble("brimstone-cubes.face-offset", 180));
        s.setRotation(yaw, 0);
        s.setBodyYaw(yaw);
    }

    private static boolean prey(Player p) {
        return (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.isDead();
    }

    private Player nearest(SulfurCube s, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : s.getWorld().getPlayers()) {
            if (!prey(p) || held.containsKey(p.getUniqueId())) continue;
            double d = p.getLocation().distanceSquared(s.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private void engulf(SulfurCube s, Player p, FileConfiguration c) {
        int secs = Math.max(1, c.getInt("brimstone-cubes.engulf-seconds", 3));
        held.put(p.getUniqueId(), new Hold(s.getUniqueId(), now + secs * 20));
        s.setVelocity(new Vector(0, 0, 0));
        s.setAware(false);   // it sits still while it digests
        pin(p, true);
        p.teleport(inside(s, p));
        World w = s.getWorld();
        w.playSound(s.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.4f, 0.5f);
        w.playSound(s.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 0.6f);
        w.spawnParticle(Particle.DUST_PLUME, s.getLocation().add(0, 1, 0), 40, 0.8, 0.8, 0.8, 0.03);
        p.sendActionBar(net.kyori.adventure.text.Component.text("You've been swallowed by a Brimstone Cube!", net.kyori.adventure.text.format.TextColor.color(0xE8D44D)));
    }

    /** Standing on the cube's floor, dead centre, still facing wherever they were looking. */
    private static Location inside(SulfurCube s, Player p) {
        Location in = s.getLocation();
        in.setYaw(p.getLocation().getYaw());
        in.setPitch(p.getLocation().getPitch());
        return in;
    }

    /** Can't walk or jump while inside (they can still look around and swing). */
    private void pin(Player p, boolean on) {
        for (var a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            var inst = p.getAttribute(a);
            if (inst == null) continue;
            inst.removeModifier(pinKey);
            if (on) inst.addModifier(new org.bukkit.attribute.AttributeModifier(pinKey, -1, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void tickHeld(FileConfiguration c) {
        if (held.isEmpty()) return;
        double dmg = c.getDouble("brimstone-cubes.damage-per-second", 4);
        var it = held.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Player p = plugin.getServer().getPlayer(en.getKey());
            Entity e = plugin.getServer().getEntity(en.getValue().cube);
            if (p == null || !p.isOnline()) { it.remove(); if (e instanceof SulfurCube s) s.setAware(true); continue; }
            if (p.isDead() || !prey(p)) { it.remove(); pin(p, false); if (e instanceof SulfurCube s) s.setAware(true); continue; }
            if (!(e instanceof SulfurCube s) || s.isDead() || !s.isValid() || s.getWorld() != p.getWorld()) { it.remove(); free(p); continue; }
            if (now >= en.getValue().until) { it.remove(); spit(s, p, c); continue; }
            s.setVelocity(new Vector(0, Math.min(0, s.getVelocity().getY()), 0));
            if (p.getLocation().distanceSquared(s.getLocation()) > 0.09) p.teleport(inside(s, p));
            p.setFallDistance(0);
            if ((en.getValue().until - now) % 20 == 0) {
                p.damage(dmg, s);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 0.7f);
            }
            if (now % 4 == 0) p.getWorld().spawnParticle(Particle.DUST_PLUME, p.getLocation().add(0, 1, 0), 3, 0.4, 0.5, 0.4, 0.01);
        }
    }

    /** Out you go: flung away in a random direction. */
    private void spit(SulfurCube s, Player p, FileConfiguration c) {
        pin(p, false);
        s.setAware(true);
        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double force = c.getDouble("brimstone-cubes.spit-force", 1.6);
        Location out = s.getLocation().add(0, s.getBoundingBox().getHeight() * 0.6, 0);
        out.setYaw(p.getLocation().getYaw());
        out.setPitch(p.getLocation().getPitch());
        p.teleport(out);
        p.setVelocity(new Vector(Math.cos(a) * force, c.getDouble("brimstone-cubes.spit-lift", 0.7), Math.sin(a) * force));
        p.setFallDistance(0);
        s.getWorld().playSound(s.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.4f, 1.4f);
        s.getWorld().playSound(s.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.5f);
        s.getWorld().spawnParticle(Particle.DUST_PLUME, out, 30, 0.4, 0.4, 0.4, 0.15);
        spared.put(p.getUniqueId(), now + c.getInt("brimstone-cubes.spared-seconds", 5) * 20);
        resting.put(s.getUniqueId(), now + c.getInt("brimstone-cubes.rest-seconds", 4) * 20);
    }

    /** The cube died or vanished with someone inside: they drop out where it was. */
    private void free(Player p) {
        pin(p, false);
        spared.put(p.getUniqueId(), now + 60);
        p.setFallDistance(0);
    }

    // ---------------------------------------------------------------- housekeeping

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Hold h = held.remove(e.getPlayer().getUniqueId());
        if (h != null && plugin.getServer().getEntity(h.cube) instanceof SulfurCube s) s.setAware(true);
        spared.remove(e.getPlayer().getUniqueId());
        pin(e.getPlayer(), false);
    }

    /** Belt and braces: never leave anyone stuck unable to walk (say the server stopped mid-swallow). */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) { pin(e.getPlayer(), false); }

    void stop() {
        for (var en : held.entrySet()) {
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p != null) pin(p, false);
            if (plugin.getServer().getEntity(en.getValue().cube) instanceof SulfurCube s) s.setAware(true);
        }
        held.clear();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!is(e.getEntity())) return;
        resting.remove(e.getEntity().getUniqueId());
        nextHop.remove(e.getEntity().getUniqueId());
        nextPounce.remove(e.getEntity().getUniqueId());
        held.entrySet().removeIf(en -> {
            if (!en.getValue().cube.equals(e.getEntity().getUniqueId())) return false;
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p != null) free(p);
            return true;
        });
    }

    /** No pearling or chorus-fruiting your way out from inside. */
    @EventHandler(ignoreCancelled = true)
    public void onEscape(PlayerTeleportEvent e) {
        if (!held.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEntityEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwallowItem(SulfurCubeSwallowItemEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent e) { if (is(e.getRightClicked())) e.setCancelled(true); }
}
