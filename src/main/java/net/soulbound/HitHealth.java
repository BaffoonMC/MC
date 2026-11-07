package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Hit someone and their hearts show above your hotbar: "minecraftmurdere ❤❤❤❤❤❤❤❤ 7.5/10". Handy for knowing when a
 * rival is weak enough to capture. Players by default; hit-health.mobs: true shows it for monsters and animals too.
 */
final class HitHealth implements Listener {
    private static final TextColor FULL = TextColor.color(0xFF4A4A), HALF = TextColor.color(0xB83A3A), EMPTY = TextColor.color(0x4A2A2A), GOLD = TextColor.color(0xF2C04A);
    private final Soulbound plugin;

    HitHealth(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("hit-health.enabled", true)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
        if (!(d instanceof Player attacker) || attacker.equals(victim)) return;
        if (!(victim instanceof Player) && !plugin.getConfig().getBoolean("hit-health.mobs", false)) return;
        if (plugin.boss() != null && plugin.boss().isFoe(victim)) return;   // Moloch and his servants have the boss bar
        // a tick later, once the damage (and armour, and absorption) has actually been taken off
        Bukkit.getScheduler().runTask(plugin, () -> { if (attacker.isOnline()) attacker.sendActionBar(bar(victim)); });
    }

    private static Component bar(LivingEntity v) {
        AttributeInstance a = v.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? 20 : a.getValue(), hp = v.isDead() ? 0 : Math.max(0, v.getHealth()), abs = v.getAbsorptionAmount();
        String name = v instanceof Player p ? p.getName()
                : v.customName() != null ? PlainTextComponentSerializer.plainText().serialize(v.customName()) : nice(v.getType().name());
        TextComponent.Builder b = Component.text().append(Component.text(name + "  ", NamedTextColor.WHITE));
        int hearts = (int) Math.ceil(max / 2);
        if (hearts <= 20) {   // a row of hearts, like their own health bar
            int halves = (int) Math.ceil(hp);
            for (int i = 0; i < hearts; i++) {
                int left = halves - i * 2;
                b.append(Component.text("❤", left >= 2 ? FULL : left == 1 ? HALF : EMPTY));
            }
            for (int i = 0; i < (int) Math.ceil(abs / 2) && i < 10; i++) b.append(Component.text("❤", GOLD));
        }
        b.append(Component.text("  " + fmt(hp / 2) + (abs > 0 ? "+" + fmt(abs / 2) : "") + "/" + fmt(max / 2), NamedTextColor.GRAY));
        if (hp <= 0) b.append(Component.text("  ☠", NamedTextColor.DARK_RED));
        return b.build();
    }

    private static String fmt(double h) {
        double r = Math.round(h * 2) / 2.0;
        return r == Math.floor(r) ? String.valueOf((int) r) : String.valueOf(r);
    }

    private static String nice(String type) {
        String s = type.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
