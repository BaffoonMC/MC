package net.baffoon.bureau;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Taxman. Once a player's bill reaches "assigned" (see Taxes), he comes for them whenever they're out in the wild:
 * he walks, never runs, opens doors, and finds them again if they get far ahead. If he reaches them, he collects what's
 * owed (money, then Cold Hard Cash, then up to three backpack stacks into impound) and leaves.
 *
 * Ways out: pay; stand somewhere safe (spawn, the Bureau, your own claim); go somewhere cold (lizards slow to a crawl
 * and give up after a minute of it); hire Security; or outlast him (he gives up after hunt-minutes, back next day).
 * Hitting him only staggers him, and adds a fee for resisting an audit. He never hurts anyone.
 */
final class Taxman implements Listener {
    private static final TextColor RED = Taxes.RED, GOLD = Taxes.GOLD;
    private static final Set<Material> COLD = Set.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE,
            Material.SNOW_BLOCK, Material.SNOW, Material.POWDER_SNOW);

    final class Hunt {
        final UUID target;
        Walker w;
        final BossBar bar;
        final long started = System.currentTimeMillis();
        int ticks, coldTicks, safeTicks, lostTicks;
        boolean saidWait, saidCold;
        Hunt(UUID target, BossBar bar) { this.target = target; this.bar = bar; }
    }

    private final Bureau plugin;
    private final Map<UUID, Hunt> hunts = new HashMap<>();

    Taxman(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("taxman." + k, d); }

    Walker hunterOf(UUID id) { Hunt h = hunts.get(id); return h == null ? null : h.w; }
    boolean hunting(UUID id) { return hunts.containsKey(id); }

    // ---------------------------------------------------------------- where he can't follow

    boolean safe(Player p) {
        Location l = p.getLocation();
        double sr = cfg("spawn-radius", 96);
        Location sp = l.getWorld().getSpawnLocation();
        if (sr > 0 && sp.distanceSquared(l) < sr * sr) return true;
        Location bureau = plugin.lift().bottom;
        double br = cfg("bureau-radius", 48);
        if (bureau != null && bureau.getWorld() == l.getWorld() && bureau.distanceSquared(l) < br * br) return true;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                String owner = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%griefprevention_currentclaim_ownername%");
                if (owner != null && owner.equalsIgnoreCase(p.getName())) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    static boolean cold(Player p) {
        Block b = p.getLocation().getBlock();
        if (b.getTemperature() < 0.15) return true;
        return COLD.contains(b.getType()) || COLD.contains(b.getRelative(0, -1, 0).getType());
    }

    // ---------------------------------------------------------------- starting a hunt

    /** Every second: anyone assigned, online, out in the wild and not hunted today gets a visit. */
    void second() {
        long now = System.currentTimeMillis(), cool = (long) (cfg("cooldown-hours", 20) * 3_600_000L);
        for (var e : plugin.taxes().all()) {
            Taxes.Case c = e.getValue();
            if (c.stage != Taxes.Stage.ASSIGNED || c.owed <= 0 || hunts.containsKey(e.getKey())) continue;
            if (now - c.lastHunt < cool) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || p.isDead() || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) continue;
            if (plugin.taxes().exempt(p, c) || safe(p)) continue;
            start(p, c);
        }
    }

    void start(Player p, Taxes.Case c) {
        Location at = spot(p, 16, 22);
        Walker w = Walker.spawn(plugin, at, Lizards.Role.TAXMAN, Component.text("The Taxman", RED), cfg("speed", 0.23));
        if (w == null) return;
        BossBar bar = BossBar.bossBar(Component.text("AUDIT IN PROGRESS · " + Money.fmt(c.owed) + " owed", RED), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Hunt h = new Hunt(p.getUniqueId(), bar);
        h.w = w;
        hunts.put(p.getUniqueId(), h);
        c.lastHunt = System.currentTimeMillis();
        plugin.dirty();
        p.showBossBar(bar);
        p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text("The Taxman", RED)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(Component.text("There you are.", NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.6f);
    }

    /** A standable spot min..max blocks from the player, preferably behind them. */
    private Location spot(Player p, int min, int max) {
        Location base = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double behind = Math.toRadians(base.getYaw() - 90);   // facing is (-sin yaw, cos yaw); this angle points the other way
        for (int tries = 0; tries < 24; tries++) {
            double ang = (tries < 12 ? behind + r.nextDouble(-0.9, 0.9) : r.nextDouble(0, Math.PI * 2));
            double d = r.nextDouble(min, max);
            int x = base.getBlockX() + (int) Math.round(Math.cos(ang) * d), z = base.getBlockZ() + (int) Math.round(Math.sin(ang) * d);
            for (int y = base.getBlockY() + 6; y >= base.getBlockY() - 8; y--) {
                Block feet = base.getWorld().getBlockAt(x, y, z);
                if (feet.getRelative(0, -1, 0).getType().isSolid() && feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                        && !feet.isLiquid() && !feet.getRelative(0, -1, 0).isLiquid())
                    return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return base.clone().add(base.getDirection().multiply(-4).setY(0));
    }

    // ---------------------------------------------------------------- the chase

    void tick() {
        for (Hunt h : List.copyOf(hunts.values())) {
            Player p = Bukkit.getPlayer(h.target);
            Taxes.Case c = plugin.taxes().peek(h.target);
            if (p == null || c == null || c.owed <= 0 || c.stage != Taxes.Stage.ASSIGNED) { endHunt(h.target, null); continue; }
            if (!h.w.valid() || h.w.at().getWorld() != p.getWorld()) {
                // a portal, a chunk that unloaded: he'll be back in a minute
                long cool = (long) (cfg("cooldown-hours", 20) * 3_600_000L);
                c.lastHunt = System.currentTimeMillis() - cool + 60_000L;
                endHunt(h.target, "He lost your trail. For now.");
                continue;
            }
            h.ticks++;
            h.w.tick(p.getLocation());
            double d2 = h.w.at().distanceSquared(p.getLocation());
            boolean cold = cold(p), safe = safe(p);

            if (h.ticks % 10 == 0) {
                if (safe) {
                    h.w.stop();
                    h.safeTicks += 10;
                    if (!h.saidWait) { say(p, "I'll wait."); h.saidWait = true; }
                    if (h.safeTicks > cfg("wait-minutes", 5) * 60 * 20) { endHunt(h.target, "The Taxman tips his hat and leaves. Tomorrow, then."); continue; }
                } else {
                    h.saidWait = false;
                    h.w.walkTo(p, cold ? cfg("cold-speed", 0.35) : 1.0);
                }
                if (cold) {
                    h.coldTicks += 10;
                    if (!h.saidCold) { say(p, "It's... too... cold..."); h.saidCold = true; }
                    if (h.coldTicks > cfg("cold-seconds", 60) * 20) { endHunt(h.target, "The Taxman can't stand the cold. He'll be back tomorrow."); continue; }
                }
                // got too far ahead: he finds you (no more than every 30 s)
                if (d2 > 40 * 40) {
                    h.lostTicks += 10;
                    if (h.lostTicks > 600) { h.w.teleport(spot(p, 14, 18)); h.lostTicks = 0; say(p, "Found you."); }
                } else h.lostTicks = 0;
                float left = (float) Math.max(0, 1 - (System.currentTimeMillis() - h.started) / (cfg("hunt-minutes", 20) * 60_000.0));
                h.bar.progress(left);
                h.bar.name(Component.text("AUDIT IN PROGRESS · " + Money.fmt(c.owed) + " owed" + (safe ? " · waiting" : cold ? " · freezing" : ""), RED));
                if (left <= 0) { endHunt(h.target, "The Taxman checks his watch and leaves. Tomorrow, then."); continue; }
            }
            // footsteps and a briefcase clicking, when he's close
            if (h.ticks % 12 == 0 && d2 < 24 * 24) p.playSound(h.w.at(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 0.7f, 0.55f);
            if (h.ticks % 400 == 0 && d2 < 30 * 30 && !safe) say(p, List.of("Sssettle up.", "I have all day. And all night.", "Everyone pays. Eventually.", "Running only adds interest.").get(ThreadLocalRandom.current().nextInt(4)));
            // caught
            if (!safe && !h.w.frozen() && d2 < 1.7 * 1.7) caught(p, h, c);
        }
    }

    private void caught(Player p, Hunt h, Taxes.Case c) {
        if (plugin.items().takeOne(p, "tax_receipt")) {
            say(p, "...A receipt. Hm. In order. Good day.");
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
            c.lastHunt = System.currentTimeMillis();
            plugin.dirty();
            endHunt(h.target, "The Taxman leaves for the day. (You still owe " + Money.fmt(c.owed) + ".)");
            return;
        }
        double before = c.owed;
        plugin.taxes().collect(p, c, true);
        double took = before - c.owed;
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        say(p, "Pleasure doing business.");
        Bukkit.broadcast(Component.text("✎ ", GOLD).append(Component.text(p.getName(), NamedTextColor.WHITE))
                .append(Component.text(" has been audited by the Taxman.", NamedTextColor.GRAY)));
        if (c.owed <= 0.009) { endHunt(h.target, "Collected " + Money.fmt(took) + ". Your account is settled."); plugin.taxes().clear(p.getUniqueId()); }
        else endHunt(h.target, "Collected " + Money.fmt(took) + ". Still owing " + Money.fmt(c.owed) + ". He'll be back.");
    }

    void endHunt(UUID id, String why) {
        Hunt h = hunts.remove(id);
        if (h == null) return;
        h.w.remove();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.hideBossBar(h.bar);
            if (why != null) p.sendMessage(Component.text(why, NamedTextColor.GRAY));
        }
    }

    void endAll() { for (UUID id : List.copyOf(hunts.keySet())) endHunt(id, null); }

    private static void say(Player p, String line) {
        p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text("The Taxman", RED)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(Component.text(line, NamedTextColor.WHITE)));
    }

    // ---------------------------------------------------------------- keeping him harmless (and unhurtable)

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity(), by = e.getDamager();
        if (by instanceof Projectile pr && pr.getShooter() instanceof Entity s) by = s;
        if (Walker.fighter(by) || Walker.fighter(victim)) return;         // Sentinel bodyguards fight for real
        if (Walker.isPart(by)) { e.setCancelled(true); return; }          // other walkers never hurt anyone
        if (!Walker.isPart(victim)) return;
        e.setCancelled(true);
        for (Hunt h : hunts.values()) {
            if (!h.w.owns(victim)) continue;
            h.w.freeze((int) (cfg("stagger-seconds", 3) * 20));
            if (by instanceof Player p && p.getUniqueId().equals(h.target)) {
                Taxes.Case c = plugin.taxes().peek(h.target);
                if (c != null) {
                    double fee = Exchange.round(c.owed * cfg("resist-fee", 0.05));
                    if (fee > 0) { c.owed = Exchange.round(c.owed + fee); plugin.dirty(); }
                    p.sendMessage(Component.text("Resisting an audit: +" + Money.fmt(fee) + ". He staggers, then straightens his tie.", RED));
                }
            }
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.8f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHurt(EntityDamageEvent e) { if (Walker.isPart(e.getEntity()) && !Walker.fighter(e.getEntity()) && !(e instanceof EntityDamageByEntityEvent)) e.setCancelled(true); }

    @EventHandler public void onTarget(EntityTargetEvent e) { if (Walker.isPart(e.getEntity()) && !Walker.fighter(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onBurn(EntityCombustEvent e) { if (Walker.isPart(e.getEntity()) && !Walker.fighter(e.getEntity())) e.setCancelled(true); }

    /** Spawn protection doesn't apply to the Bureau's own walkers. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent e) { if (plugin.spawning && e.isCancelled()) e.setCancelled(false); }

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent e) {
        if (!Walker.isPart(e.getRightClicked())) return;
        e.setCancelled(true);
        for (Hunt h : hunts.values()) if (h.w.owns(e.getRightClicked())) { plugin.taxes().status(e.getPlayer()); return; }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!hunts.containsKey(e.getPlayer().getUniqueId())) return;
        Taxes.Case c = plugin.taxes().peek(e.getPlayer().getUniqueId());
        if (c != null) c.lastHunt = System.currentTimeMillis() - (long) (cfg("cooldown-hours", 20) * 3_600_000L) + 120_000L;   // logging out only buys two minutes
        endHunt(e.getPlayer().getUniqueId(), null);
    }
}
