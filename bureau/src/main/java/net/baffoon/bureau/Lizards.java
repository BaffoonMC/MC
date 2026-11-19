package net.baffoon.bureau;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The lizards in suits: Mannequins wearing the pack's skins (bureau:entity/lizard_clerk, _director, _security, _taxman).
 * They turn to watch whoever's nearest. The teller opens the Exchange; the others say a line.
 */
final class Lizards implements Listener {
    enum Role {
        TELLER("clerk", "Mr. Coldwell", "The Exchange"),
        RECEPTIONIST("clerk", "Mrs. Sssmith", "Reception"),
        DIRECTOR("director", "Director Sssterling", "The Director"),
        SECURITY("security", "Security", "Bureau Security"),
        CLERK("clerk", "Clerk", "Contracts Department"),
        TAXMAN("taxman", "The Taxman", "Collections");
        final String skin, name, title;
        Role(String skin, String name, String title) { this.skin = skin; this.name = name; this.title = title; }
        static Role of(String s) { try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; } }
    }

    private static final TextColor GREEN = TextColor.color(0x7BD36A), GOLD = TextColor.color(0xF0C85A);
    private final Bureau plugin;
    private final NamespacedKey roleKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    Lizards(Bureau plugin) {
        this.plugin = plugin;
        this.roleKey = new NamespacedKey(plugin, "lizard_role");
    }

    Role role(Entity e) {
        if (!(e instanceof Mannequin)) return null;
        String r = e.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        return r == null ? null : Role.of(r);
    }

    private static ResolvableProfile skin(Role r) {
        return ResolvableProfile.resolvableProfile()
                .skinPatch(s -> s.body(new NamespacedKey("bureau", "entity/lizard_" + r.skin)).model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC))
                .build();
    }

    String spawn(Player p, Role r, String name) {
        Location l = p.getLocation();
        l.setPitch(0);
        Mannequin m = l.getWorld().spawn(l, Mannequin.class, x -> {
            x.setProfile(skin(r));
            x.setPersistent(true);
            x.setRemoveWhenFarAway(false);
            x.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, r.name());
        });
        if (!m.isValid()) return "Something stopped the lizard appearing (another plugin cancelled it).";
        m.setInvulnerable(true);
        m.setImmovable(true);
        m.customName(Component.text(name == null || name.isBlank() ? r.name : name, GREEN));
        m.setCustomNameVisible(true);
        m.setDescription(Component.text(r.title, NamedTextColor.GRAY));
        return "A lizard in a suit takes their post: " + r.name.toLowerCase(Locale.ROOT).replace("the ", "") + ".";
    }

    String removeNear(Player p) {
        Mannequin best = null; double bd = 6 * 6;
        for (Mannequin m : p.getWorld().getEntitiesByClass(Mannequin.class)) {
            if (role(m) == null) continue;
            double d = m.getLocation().distanceSquared(p.getLocation());
            if (d < bd) { bd = d; best = m; }
        }
        if (best == null) return "No Bureau lizard within 6 blocks.";
        best.remove();
        return "The lizard clocks out for good.";
    }

    String reskinAll() {
        int n = 0;
        for (var w : Bukkit.getWorlds()) for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
            Role r = role(m);
            if (r != null) { m.setProfile(skin(r)); n++; }
        }
        return n + " lizard" + (n == 1 ? "" : "s") + " re-dressed.";
    }

    /** Every half second: lizards turn to face the nearest player within 8 blocks. */
    void watch() {
        for (var w : Bukkit.getWorlds()) for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
            if (role(m) == null) continue;
            Player near = null; double bd = 8 * 8;
            for (Player p : m.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(m.getLocation());
                if (d < bd) { bd = d; near = p; }
            }
            if (near == null) continue;
            Vector v = near.getEyeLocation().toVector().subtract(m.getEyeLocation().toVector());
            if (v.lengthSquared() < 1e-6) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
            float pitch = (float) Math.toDegrees(-Math.atan2(v.getY(), Math.hypot(v.getX(), v.getZ())));
            m.setRotation(yaw, Math.max(-30, Math.min(30, pitch)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent e) {
        Role r = role(e.getRightClicked());
        if (r == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 600) return;
        cooldown.put(p.getUniqueId(), now);
        if (r == Role.TELLER) { say(p, e.getRightClicked(), r, "teller"); plugin.capital().open(p); return; }
        say(p, e.getRightClicked(), r, r.name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (role(e.getEntity()) != null) e.setCancelled(true);
    }

    private void say(Player p, Entity lizard, Role r, String key) {
        List<String> lines = plugin.getConfig().getStringList("lines." + key);
        if (lines.isEmpty()) return;
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        Component name = lizard.customName() != null ? lizard.customName() : Component.text(r.name, GREEN);
        p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(name).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(Component.text(line, r == Role.DIRECTOR ? GOLD : NamedTextColor.WHITE)));
        p.playSound(lizard.getLocation(), Sound.ENTITY_LLAMA_SPIT, 0.3f, 0.6f);
        p.playSound(lizard.getLocation(), Sound.BLOCK_SAND_STEP, 0.5f, 1.6f);
    }
}
