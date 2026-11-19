package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bureau Security, three jobs:
 *  - Bodyguards: hire one from a Security lizard. He follows you, swats hostile mobs that come for you, and when the
 *    Taxman gets close he steps in and holds him back (a long stagger, every so often), so you can get away.
 *  - Firing: /bureau fire <player>. YOU'RE FIRED, two guards appear at their side, and they're escorted up the lift and
 *    barred from riding it down again for a day.
 *  - Keeping the peace: no player-on-player fighting near a Security lizard.
 */
final class Security implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF0C85A), RED = TextColor.color(0xE05050), GREEN = TextColor.color(0x7BD36A);

    final class Guard {
        final UUID client;
        final Walker w;
        final long until;
        int ticks, lastBlock = -10_000;
        Guard(UUID client, Walker w, long until) { this.client = client; this.w = w; this.until = until; }
    }

    private final Bureau plugin;
    private final Map<UUID, Guard> guards = new HashMap<>();
    private final Map<UUID, Long> firedUntil = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();

    Security(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("security." + k, d); }

    // ---------------------------------------------------------------- bodyguards

    Component offer() {
        int price = (int) cfg("bodyguard-price", 8), mins = (int) cfg("bodyguard-minutes", 60);
        return Component.text("[Hire a bodyguard: " + price + " Cold Hard Cash for " + mins + " min]", GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/bureau hire"))
                .hoverEvent(HoverEvent.showText(Component.text("He follows you, swats mobs, and holds the Taxman back.", NamedTextColor.GRAY)));
    }

    String hire(Player p) {
        if (guards.containsKey(p.getUniqueId())) return "You already have a bodyguard.";
        boolean nearGuard = false;
        for (Mannequin m : p.getWorld().getEntitiesByClass(Mannequin.class))
            if (plugin.lizards().role(m) == Lizards.Role.SECURITY && m.getLocation().distanceSquared(p.getLocation()) < 8 * 8) { nearGuard = true; break; }
        if (!nearGuard && !p.hasPermission("bureau.admin")) return "Ask one of the Security lizards at the Bureau.";
        int price = (int) cfg("bodyguard-price", 8);
        int pockets = Cash.count(p), held = plugin.vaults().held(p.getUniqueId());
        if (pockets + held < price) return "A bodyguard costs " + price + " Cold Hard Cash. You have " + (pockets + held) + ".";
        int fromPockets = Math.min(pockets, price);
        Cash.take(p, fromPockets);
        if (price > fromPockets) plugin.vaults().add(p.getUniqueId(), -(price - fromPockets));
        Location at = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
        Walker w = Walker.spawn(plugin, at, Lizards.Role.SECURITY, Component.text(p.getName() + "'s Bodyguard", GREEN), cfg("speed", 0.3));
        if (w == null) { Cash.give(p, price); return "Your bodyguard couldn't get here. You've been refunded."; }
        guards.put(p.getUniqueId(), new Guard(p.getUniqueId(), w, System.currentTimeMillis() + (long) (cfg("bodyguard-minutes", 60) * 60_000L)));
        plugin.log(p.getUniqueId(), "Hired a bodyguard for " + price);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.8f);
        return "A bodyguard falls in behind you. \"Lead the way.\"";
    }

    void tick() {
        long now = System.currentTimeMillis();
        for (Guard g : List.copyOf(guards.values())) {
            Player p = Bukkit.getPlayer(g.client);
            if (p == null || now > g.until) { dismiss(g.client, p == null ? null : "Your bodyguard's shift is over. \"Stay safe out there.\""); continue; }
            if (!g.w.valid() || g.w.at().getWorld() != p.getWorld() || g.w.at().distanceSquared(p.getLocation()) > 32 * 32) {
                // keep up: across portals, chunk edges, long sprints
                if (g.w.valid()) g.w.teleport(behind(p)); else { dismiss(g.client, "Your bodyguard got lost. \"Sorry, boss.\""); continue; }
            }
            g.ticks++;
            g.w.tick(p.getLocation());
            if (g.ticks % 10 == 0) {
                if (g.w.at().distanceSquared(p.getLocation()) > 3.5 * 3.5) g.w.walkTo(p, 1.2); else g.w.stop();
            }
            if (g.ticks % 20 == 0) protect(p, g);
        }
    }

    private Location behind(Player p) {
        Location l = p.getLocation().clone();
        Vector back = l.getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
        return l.add(back.normalize().multiply(-1.5));
    }

    /** Once a second: hold back the Taxman, swat mobs coming for the client. */
    private void protect(Player p, Guard g) {
        Walker tax = plugin.taxman().hunterOf(p.getUniqueId());
        if (tax != null && tax.valid() && tax.at().getWorld() == p.getWorld() && tax.at().distanceSquared(p.getLocation()) < 8 * 8
                && g.ticks - g.lastBlock > cfg("hold-cooldown-seconds", 25) * 20) {
            tax.freeze((int) (cfg("hold-seconds", 10) * 20));
            g.lastBlock = g.ticks;
            Vector toClient = p.getLocation().toVector().subtract(tax.at().toVector()).setY(0);
            if (toClient.lengthSquared() > 1e-3) g.w.teleport(tax.at().clone().add(toClient.normalize()));   // step in between
            p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text("Bodyguard", GREEN)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Back off, pal. Go, boss, I've got him.", NamedTextColor.WHITE)));
            p.playSound(tax.at(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        }
        double dmg = cfg("swat-damage", 4);
        for (Entity e : p.getNearbyEntities(5, 3, 5)) {
            if (!(e instanceof Mob m) || !(e instanceof Enemy) || Walker.isPart(e)) continue;
            if (m.getTarget() == null || !m.getTarget().equals(p)) continue;
            m.damage(dmg);
            m.setVelocity(m.getLocation().toVector().subtract(g.w.at().toVector()).setY(0).normalize().multiply(0.6).setY(0.3));
            m.getWorld().playSound(m.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 0.9f);
            break;
        }
    }

    void dismiss(UUID id, String why) {
        Guard g = guards.remove(id);
        if (g == null) return;
        g.w.remove();
        Player p = Bukkit.getPlayer(id);
        if (p != null && why != null) p.sendMessage(Component.text(why, NamedTextColor.GRAY));
    }

    void endAll() { for (UUID id : List.copyOf(guards.keySet())) dismiss(id, null); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { dismiss(e.getPlayer().getUniqueId(), null); }

    // ---------------------------------------------------------------- firing

    boolean fired(UUID id) { return firedUntil.getOrDefault(id, 0L) > System.currentTimeMillis(); }
    long firedLeft(UUID id) { return Math.max(0, firedUntil.getOrDefault(id, 0L) - System.currentTimeMillis()); }

    void unfire(UUID id) { firedUntil.remove(id); plugin.dirty(); }

    void fire(Player p, String reason) {
        firedUntil.put(p.getUniqueId(), System.currentTimeMillis() + (long) (cfg("fired-hours", 24) * 3_600_000L));
        plugin.dirty();
        p.showTitle(Title.title(Component.text("YOU'RE FIRED", RED, TextDecoration.BOLD),
                Component.text(reason == null || reason.isBlank() ? "Director Sssterling has let you go." : reason, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
        // two guards appear either side
        Vector side = p.getLocation().getDirection().setY(0);
        if (side.lengthSquared() < 1e-4) side = new Vector(1, 0, 0);
        side = new Vector(-side.getZ(), 0, side.getX()).normalize().multiply(1.2);
        Walker a = Walker.spawn(plugin, p.getLocation().clone().add(side), Lizards.Role.SECURITY, Component.text("Security", GREEN), 0.25);
        Walker b = Walker.spawn(plugin, p.getLocation().clone().subtract(side), Lizards.Role.SECURITY, Component.text("Security", GREEN), 0.25);
        p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text("Security", GREEN)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(Component.text("This way, please. Don't make it difficult.", NamedTextColor.WHITE)));
        Bukkit.broadcast(Component.text("✖ ", RED).append(Component.text(p.getName(), NamedTextColor.WHITE))
                .append(Component.text(" has been fired from the Bureau. Cold-blooded. Nothing personal.", NamedTextColor.GRAY)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (a != null) a.remove();
            if (b != null) b.remove();
            if (!p.isOnline()) return;
            Location out = plugin.lift().top != null ? plugin.lift().top : p.getWorld().getSpawnLocation();
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
            p.teleport(out);
            p.playSound(out, Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.7f);
            p.sendActionBar(Component.text("Escorted out. You can't ride the lift down for " + (int) cfg("fired-hours", 24) + " hours.", NamedTextColor.GRAY));
        }, 60L);
    }

    // ---------------------------------------------------------------- no fighting near Security

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity by = e.getDamager();
        if (by instanceof Projectile pr && pr.getShooter() instanceof Entity s) by = s;
        if (!(by instanceof Player attacker) || attacker.equals(victim)) return;
        double r = cfg("peace-radius", 16);
        if (r <= 0) return;
        for (LivingEntity m : victim.getWorld().getEntitiesByClass(Mannequin.class)) {
            if (plugin.lizards().role(m) != Lizards.Role.SECURITY || m.getLocation().distanceSquared(victim.getLocation()) > r * r) continue;
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - warned.getOrDefault(attacker.getUniqueId(), 0L) > 3000) {
                warned.put(attacker.getUniqueId(), now);
                attacker.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text("Security", GREEN)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Take it outside.", NamedTextColor.WHITE)));
            }
            return;
        }
    }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection d) {
        firedUntil.clear();
        if (d == null) return;
        for (String k : d.getKeys(false)) try { firedUntil.put(UUID.fromString(k), d.getLong(k)); } catch (IllegalArgumentException ignored) {}
    }

    void save(ConfigurationSection d) {
        long now = System.currentTimeMillis();
        for (var e : firedUntil.entrySet()) if (e.getValue() > now) d.set(e.getKey().toString(), e.getValue());
    }
}
