package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flying, the donor way.
 *
 * Claim flight (soulbound.claimfly, the Patron perk): inside a GriefPrevention claim you own, or one you're trusted to
 * build in, you can fly. Nothing to type: walk in and double-tap jump. Fly out of the claim and a countdown on the
 * action bar gives you a few seconds to get back before flight switches off. /claimfly turns it off and on.
 *
 * Fights (everyone flying in survival, claim flight or /fly): hit a player or a monster, or get hit by one, and flight
 * is off for combat-seconds. No-fly zones: Moloch's realm and God's island.
 *
 * Whenever this takes someone's flight away in mid-air, their next landing does no fall damage.
 *
 * soulbound.fly.bypass (ops) is left alone. Players who can already /fly (essentials.fly, the Chosen perk) don't need
 * claim flight, so it leaves them to /fly.
 */
final class Flight implements Listener, CommandExecutor {
    private final Soulbound plugin;
    private final NamespacedKey optOutKey, fallKey;
    private final Set<UUID> granted = new HashSet<>();          // flight this class switched on
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, Integer> leaving = new HashMap<>(); // ticks left on the "leaving your claim" countdown
    private final Map<UUID, Long> fallSafe = new HashMap<>();   // when their flight was taken, for the free landing

    private Object dataStore;
    private Method getClaimAt, checkPermission, allowBuild, getOwnerId;
    private Object buildPerm;
    private boolean gpTried;

    Flight(Soulbound plugin) {
        this.plugin = plugin;
        this.optOutKey = new NamespacedKey(plugin, "claimfly_off");
        this.fallKey = new NamespacedKey(plugin, "fall_safe");
    }

    void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L); }

    private boolean on() { return plugin.getConfig().getBoolean("flight.enabled", true); }
    private long combatMillis() { return plugin.getConfig().getLong("flight.combat-seconds", 10) * 1000L; }
    private int countdownTicks() { return plugin.getConfig().getInt("flight.leave-seconds", 3) * 20; }

    // ---------------------------------------------------------------- every quarter second

    private void tick() {
        if (!on()) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Long safeSince = fallSafe.get(id);
            if (safeSince != null && ((p.isOnGround() && now - safeSince > 1000) || p.isInWater() || p.isFlying() || now - safeSince > 60_000))
                fallSafe.remove(id);

            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) { granted.remove(id); leaving.remove(id); continue; }
            if (p.hasPermission("soulbound.fly.bypass")) continue;

            boolean fighting = combatUntil.getOrDefault(id, 0L) > now;
            boolean noFly = noFlyZone(p.getLocation());

            if (granted.contains(id)) {
                if (fighting) { cut(p, "No flying mid-fight."); continue; }
                if (noFly) { cut(p, "Nobody flies here."); continue; }
                if (canClaimFly(p) && inTrustedClaim(p)) {
                    if (leaving.remove(id) != null) bar(p, "✦ Claim flight on", NamedTextColor.AQUA);
                    if (!p.getAllowFlight()) p.setAllowFlight(true);    // something else switched it off: put it back
                    continue;
                }
                if (!p.isFlying()) { cut(p, null); continue; }          // walked out: nothing to warn about
                int left = leaving.merge(id, countdownTicks(), (a, b) -> a - 5);
                if (left <= 0) { cut(p, "Claim flight off."); continue; }
                bar(p, "Leaving your claim… " + (left + 19) / 20, NamedTextColor.GOLD);
                continue;
            }

            // Flight that isn't ours (/fly): only fights and no-fly zones take it.
            if (p.getAllowFlight() && (fighting || noFly)) {
                boolean air = p.isFlying() || !p.isOnGround();
                p.setFlying(false);
                p.setAllowFlight(false);
                if (air) fallSafe.put(id, now);
                bar(p, fighting ? "No flying mid-fight." : "Nobody flies here.", NamedTextColor.RED);
                continue;
            }

            if (!fighting && !noFly && !p.getAllowFlight() && canClaimFly(p) && inTrustedClaim(p)) {
                p.setAllowFlight(true);
                granted.add(id);
                bar(p, "✦ Claim flight on", NamedTextColor.AQUA);
            }
        }
    }

    private boolean canClaimFly(Player p) {
        return p.hasPermission("soulbound.claimfly") && !p.hasPermission("essentials.fly")
                && !p.getPersistentDataContainer().has(optOutKey, PersistentDataType.BYTE);
    }

    private boolean noFlyZone(Location l) {
        Zones z = plugin.zones();
        if (z != null && z.inRealm(l)) return true;
        IslandGuard island = plugin.island();
        return island != null && island.inZone(l);
    }

    private void cut(Player p, String why) {
        UUID id = p.getUniqueId();
        boolean air = p.isFlying() || !p.isOnGround();
        p.setFlying(false);
        p.setAllowFlight(false);
        granted.remove(id);
        leaving.remove(id);
        if (air) fallSafe.put(id, System.currentTimeMillis());
        if (why != null) bar(p, why, NamedTextColor.RED);
    }

    private static void bar(Player p, String text, NamedTextColor colour) { p.sendActionBar(Component.text(text, colour)); }

    // ---------------------------------------------------------------- GriefPrevention, found by name

    private boolean inTrustedClaim(Player p) {
        if (!hookGp()) return false;
        try {
            Object claim = getClaimAt.invoke(dataStore, p.getLocation(), true, null);
            if (claim == null) return false;
            if (checkPermission != null) return checkPermission.invoke(claim, p, buildPerm, null) == null;
            if (allowBuild != null) return allowBuild.invoke(claim, p, org.bukkit.Material.STONE) == null;
            return getOwnerId != null && p.getUniqueId().equals(getOwnerId.invoke(claim));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private boolean hookGp() {
        if (dataStore != null) return true;
        if (gpTried) return false;
        gpTried = true;
        Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp == null || !gp.isEnabled()) { plugin.getLogger().info("Claim flight: GriefPrevention isn't here, so it's off."); return false; }
        try {
            ClassLoader cl = gp.getClass().getClassLoader();
            Class<?> claimC = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, cl);
            Field ds = gp.getClass().getField("dataStore");
            Object store = ds.get(gp);
            Method at = store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimC);
            try {
                Class<?> permC = Class.forName("me.ryanhamshire.GriefPrevention.ClaimPermission", true, cl);
                for (Object c : permC.getEnumConstants()) if (((Enum<?>) c).name().equals("Build")) buildPerm = c;
                if (buildPerm != null) checkPermission = claimC.getMethod("checkPermission", Player.class, permC, org.bukkit.event.Event.class);
            } catch (ReflectiveOperationException ignored) {}
            if (checkPermission == null) try { allowBuild = claimC.getMethod("allowBuild", Player.class, org.bukkit.Material.class); } catch (ReflectiveOperationException ignored) {}
            try { getOwnerId = claimC.getMethod("getOwnerID"); } catch (ReflectiveOperationException ignored) {}
            getClaimAt = at;
            dataStore = store;
            plugin.getLogger().info("Claim flight: hooked into GriefPrevention.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Claim flight: couldn't read GriefPrevention claims (" + e + "), so it's off.");
            return false;
        }
    }

    // ---------------------------------------------------------------- fights, landings, logging out

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!on()) return;
        Entity hitter = e.getDamager();
        if (hitter instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) hitter = shooter;
        Entity victim = e.getEntity();
        long until = System.currentTimeMillis() + combatMillis();
        if (victim instanceof Player v && (hitter instanceof Player || hitter instanceof Enemy) && hitter != v) combatUntil.put(v.getUniqueId(), until);
        if (hitter instanceof Player a && (victim instanceof Player || victim instanceof Enemy) && victim != a) combatUntil.put(a.getUniqueId(), until);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if (fallSafe.remove(p.getUniqueId()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean air = (granted.contains(id) && (p.isFlying() || !p.isOnGround())) || fallSafe.containsKey(id);
        if (granted.contains(id)) { p.setFlying(false); p.setAllowFlight(false); }
        if (air) p.getPersistentDataContainer().set(fallKey, PersistentDataType.BYTE, (byte) 1);
        granted.remove(id);
        leaving.remove(id);
        fallSafe.remove(id);
        combatUntil.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getPersistentDataContainer().has(fallKey, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().remove(fallKey);
            fallSafe.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ---------------------------------------------------------------- /claimfly

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("soulbound.claimfly")) {
            p.sendMessage(Component.text("Claim flight comes with the Patron rank: store.playbaffoonmc.net", NamedTextColor.GRAY));
            return true;
        }
        if (p.hasPermission("essentials.fly")) {
            p.sendMessage(Component.text("You can fly anywhere: use /fly.", NamedTextColor.GRAY));
            return true;
        }
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(optOutKey, PersistentDataType.BYTE)) {
            pdc.remove(optOutKey);
            p.sendMessage(Component.text("Claim flight is on. Double-tap jump inside your claim.", NamedTextColor.AQUA));
        } else {
            pdc.set(optOutKey, PersistentDataType.BYTE, (byte) 1);
            if (granted.contains(p.getUniqueId())) cut(p, null);
            p.sendMessage(Component.text("Claim flight is off. /claimfly to turn it back on.", NamedTextColor.GRAY));
        }
        return true;
    }
}
