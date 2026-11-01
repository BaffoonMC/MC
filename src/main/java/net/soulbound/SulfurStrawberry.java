package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Sulfur Strawberry: a little carrot idol with a gold shimmer, very rarely dug up when harvesting ripe carrots.
 * Held in either hand:
 * - Well Fed: hunger drains half as fast.
 * - Fruitful: animals you breed sometimes have twins (an identical second baby).
 * No recipe; it only comes out of the ground.
 */
final class SulfurStrawberry implements Listener {
    static final String NAME = "Sulfur Strawberry";
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private final Soulbound plugin;
    private final NamespacedKey key;

    SulfurStrawberry(Soulbound plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "sulfur_strawberry"); }

    ItemStack create(int n) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD, Math.max(1, Math.min(64, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(NAME, GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("A little idol that grew among the carrots.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                Component.text("While held:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Well Fed: ", GOLD).append(Component.text("hunger drains half as fast.", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false),
                Component.text("Fruitful: ", GOLD).append(Component.text("animals you breed may have twins.", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false)));
        m.setItemModel(new NamespacedKey("soulbound", "sulfur_strawberry"));
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean is(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    boolean holding(Player p) { return is(p.getInventory().getItemInMainHand()) || is(p.getInventory().getItemInOffHand()); }

    // ---------------------------------------------------------------- Well Fed

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaust(EntityExhaustionEvent e) {
        if (!(e.getEntity() instanceof Player p) || !holding(p)) return;
        e.setExhaustion(e.getExhaustion() * (float) plugin.getConfig().getDouble("sulfur-strawberry.hunger-multiplier", 0.5));
    }

    // ---------------------------------------------------------------- Fruitful

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || !holding(p)) return;
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("sulfur-strawberry.twin-chance", 0.33)) return;
        LivingEntity baby = e.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!baby.isValid()) return;
            // an identical twin: same breed, colours and markings
            var twin = baby.createSnapshot();
            if (twin == null) return;
            var t = twin.createEntity(baby.getLocation().add(ThreadLocalRandom.current().nextDouble(-.3, .3), 0, ThreadLocalRandom.current().nextDouble(-.3, .3)));
            if (t instanceof org.bukkit.entity.Ageable a) a.setBaby();
            if (t instanceof Tameable tw && baby instanceof Tameable tb && tb.getOwner() != null) tw.setOwner(tb.getOwner());
            baby.getWorld().spawnParticle(Particle.HEART, baby.getLocation().add(0, 0.8, 0), 6, 0.4, 0.3, 0.4);
            p.sendActionBar(Component.text("Twins!", GOLD));
        });
    }

    // ---------------------------------------------------------------- the drop

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.CARROTS || !(b.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("sulfur-strawberry.drop-chance", 0.004)) return;
        b.getWorld().dropItemNaturally(b.getLocation().add(.5, .3, .5), create(1));
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        b.getWorld().spawnParticle(Particle.WAX_OFF, b.getLocation().add(.5, .5, .5), 12, .3, .3, .3, 0.5);
        p.sendActionBar(Component.text("Something strange was growing among the carrots...", GOLD));
    }
}
