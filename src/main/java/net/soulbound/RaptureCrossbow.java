package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Rapture Crossbow: Heaven's crossbow, sold by the Peacekeeper, and only the Graced can load it. It loads fast
 * (Quick Charge built in), its bolts fly faster and hit harder, and whatever they hit is lifted into the air for a
 * moment (undead for longer; the inhuman are lit up too). Nobody can be lifted again for a few seconds. Safe zones
 * (spawn, the temple, God's island) stop the hit, so they stop the lift too.
 */
final class RaptureCrossbow implements Listener {
    private static final NamespacedKey MODEL = new NamespacedKey("soulbound", "rapture_crossbow");
    private static final TextColor NAME = TextColor.color(0xBFDDFF);
    private final Soulbound plugin;
    private final NamespacedKey itemKey, boltKey;
    private final Map<UUID, Long> lifted = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();

    RaptureCrossbow(Soulbound plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "rapture_crossbow");
        boltKey = new NamespacedKey(plugin, "rapture_bolt");
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("rapture-crossbow." + k, d); }

    ItemStack create() {
        ItemStack it = new ItemStack(Material.CROSSBOW);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Rapture Crossbow", NAME, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Whatever it strikes is lifted up.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Loads quick, hits hard.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Only the Graced can draw it.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(MODEL);
        int qc = (int) cfg("quick-charge", 2);
        if (qc > 0) m.addEnchant(Enchantment.QUICK_CHARGE, qc, true);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean is(ItemStack it) {
        return it != null && it.getType() == Material.CROSSBOW && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private boolean graced(Player p) { return plugin.grace() != null && plugin.grace().is(p); }

    private void refuse(Player p) {
        long now = System.currentTimeMillis();
        if (now - warned.getOrDefault(p.getUniqueId(), 0L) < 2000) return;
        warned.put(p.getUniqueId(), now);
        p.sendActionBar(Component.text("The string won't pull for you.", NamedTextColor.GRAY));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /** Only the Graced can load it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLoad(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!is(e.getItem()) || graced(e.getPlayer())) return;
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        refuse(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!is(e.getBow())) return;
        if (e.getEntity() instanceof Player p && !graced(p)) { e.setCancelled(true); refuse(p); return; }
        Entity proj = e.getProjectile();
        if (!(proj instanceof AbstractArrow)) return;   // fireworks fly as usual
        proj.getPersistentDataContainer().set(boltKey, PersistentDataType.BYTE, (byte) 1);
        proj.setVelocity(proj.getVelocity().multiply(cfg("speed-multiplier", 1.3)));
        // a trail of cloud behind the bolt
        final int[] life = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, t -> {
            if (!proj.isValid() || ((AbstractArrow) proj).isInBlock() || ++life[0] > 30) { t.cancel(); return; }
            proj.getWorld().spawnParticle(Particle.CLOUD, proj.getLocation(), 1, 0, 0, 0, 0);
        }, 1L, 2L);
        proj.getWorld().playSound(proj.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
    }

    /** Harder hits. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDamage(EntityDamageByEntityEvent e) {
        if (!e.getDamager().getPersistentDataContainer().has(boltKey, PersistentDataType.BYTE)) return;
        e.setDamage(e.getDamage() * cfg("damage-multiplier", 1.5));
    }

    /** The lift: after every other plugin's had its say (a cancelled hit, e.g. in a safe zone, lifts nobody). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!e.getDamager().getPersistentDataContainer().has(boltKey, PersistentDataType.BYTE) || !(e.getEntity() instanceof LivingEntity v)) return;
        long now = System.currentTimeMillis();
        if (now - lifted.getOrDefault(v.getUniqueId(), 0L) < cfg("relift-cooldown-seconds", 3) * 1000) return;
        lifted.put(v.getUniqueId(), now);
        boolean undead = plugin.grace() != null && plugin.grace().undead(v);
        int ticks = (int) (cfg(undead ? "undead-levitation-seconds" : "levitation-seconds", undead ? 2 : 1) * 20);
        v.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, 2, false, true, true));
        if (v instanceof Player p && plugin.inhuman() != null && plugin.inhuman().is(p))
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (cfg("inhuman-glow-seconds", 5) * 20), 0, false, false, true));
        v.getWorld().spawnParticle(Particle.CLOUD, v.getLocation().add(0, 0.2, 0), 16, 0.4, 0.1, 0.4, 0.04);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_ALLAY_ITEM_THROWN, 1f, 0.6f);
        if (lifted.size() > 256) lifted.values().removeIf(t -> now - t > 60_000);
    }
}
