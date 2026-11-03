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
 * The Rapture Crossbow: Heaven's crossbow, sold by the Peacekeeper, and only the Graced can load it. It loads in a
 * blink (a quarter of a second, no drawing: faster than Quick Charge III), its bolts fly faster and hit harder, and whatever they hit is lifted into the air for a
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
        m.lore(lore());
        m.setItemModel(MODEL);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private static Component line(String icon, String text, String extra) {
        Component c = Component.text(icon + " ", NAME).append(Component.text(text, NamedTextColor.WHITE));
        if (extra != null) c = c.append(Component.text(" " + extra, NamedTextColor.DARK_GRAY));
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0); }

    /** The tooltip: what makes it different from a normal crossbow, from the current settings. */
    private List<Component> lore() {
        double load = Math.max(1, cfg("load-ticks", 5)) / 20.0;
        int speed = (int) Math.round((cfg("speed-multiplier", 1.3) - 1) * 100), dmg = (int) Math.round((cfg("damage-multiplier", 1.5) - 1) * 100);
        return List.of(
                Component.text("Whatever it strikes is lifted up.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                Component.text("Better than a crossbow:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                line("\u26A1", "Loads in " + num(load) + "s", "(a crossbow takes 1.25s)"),
                line("\u27B6", "Bolts fly " + speed + "% faster", null),
                line("\u2694", dmg + "% more damage", null),
                line("\u2601", "Lifts what it hits for " + num(cfg("levitation-seconds", 1)) + "s", "(undead " + num(cfg("undead-levitation-seconds", 2)) + "s)"),
                line("\u2726", "The inhuman it hits glow for " + num(cfg("inhuman-glow-seconds", 5)) + "s", null),
                Component.empty(),
                Component.text("Only the Graced can draw it.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
    }

    /** Older ones get the new tooltip (and lose the Quick Charge they no longer need). */
    private void refresh(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        List<Component> want = lore();
        boolean changed = false;
        if (!want.equals(m.lore())) { m.lore(want); changed = true; }
        if (m.hasEnchant(Enchantment.QUICK_CHARGE)) { m.removeEnchant(Enchantment.QUICK_CHARGE); changed = true; }
        if (changed) it.setItemMeta(m);
    }

    @EventHandler
    public void onHold(org.bukkit.event.player.PlayerItemHeldEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (is(it)) refresh(it);
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

    /** Only the Graced can load it, and it loads far faster than any crossbow can: a flick of the wrist, no drawing.
     *  (Right-click while it's empty: a quarter of a second later it's loaded. Loaded, right-click fires as usual.) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLoad(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!is(e.getItem())) return;
        Player p = e.getPlayer();
        if (!graced(p)) { e.setUseItemInHand(org.bukkit.event.Event.Result.DENY); refuse(p); return; }
        if (!(e.getItem().getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta cm) || cm.hasChargedProjectiles()) return;   // loaded: let it fire
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);   // no slow draw
        org.bukkit.inventory.EquipmentSlot hand = e.getHand();
        if (hand == null || loading.contains(p.getUniqueId())) return;
        if (ammo(p) == null) { p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f); return; }
        loading.add(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 1.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            loading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            ItemStack bow = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
            if (!is(bow) || !(bow.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta m) || m.hasChargedProjectiles()) return;
            ItemStack src = ammo(p);
            if (src == null) return;
            ItemStack one = src.clone(); one.setAmount(1);
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) src.setAmount(src.getAmount() - 1);
            int n = m.hasEnchant(Enchantment.MULTISHOT) ? 3 : 1;
            for (int i = 0; i < n; i++) m.addChargedProjectile(one.clone());
            bow.setItemMeta(m);
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.5f);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6)), 4, 0.1, 0.1, 0.1, 0.01);
        }, Math.max(1L, (long) cfg("load-ticks", 5)));
    }

    private final java.util.Set<UUID> loading = new java.util.HashSet<>();

    /** What it loads, the way a crossbow picks: a rocket in the off hand first, then the first arrow in the inventory. */
    private static ItemStack ammo(Player p) {
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.getType() == Material.FIREWORK_ROCKET) return off;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && (it.getType() == Material.ARROW || it.getType() == Material.TIPPED_ARROW || it.getType() == Material.SPECTRAL_ARROW)) return it;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return new ItemStack(Material.ARROW);
        return null;
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
