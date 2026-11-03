package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The Tumour Pie (double stuffed): two tumours, nether wart, an egg and sugar, crafted like a pumpkin pie. Its texture
 * throbs gently. Inhumans treat it as their kind of food and nothing else happens. The living get fed and feel fine...
 * then 30 seconds later "Something doesn't feel right", and they swell to 1.5x their size and are poisoned.
 */
final class TumourPie implements Listener {
    private static final NamespacedKey MODEL = new NamespacedKey("soulbound", "tumour_pie");
    private final Soulbound plugin;
    private final NamespacedKey recipeKey;

    TumourPie(Soulbound plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "tumour_pie");
    }

    ItemStack create(int amount) {
        ItemStack it = new ItemStack(Material.PUMPKIN_PIE, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Tumour Pie", TextColor.color(0xEE7884)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Two loads of filling.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Double stuffed.", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Swallow, don't chew.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(MODEL);
        FoodComponent food = m.getFood();
        food.setNutrition(Math.max(1, plugin.getConfig().getInt("tumour-pie.hunger", 8)));
        food.setSaturation((float) plugin.getConfig().getDouble("tumour-pie.saturation", 9.6));
        m.setFood(food);
        it.setItemMeta(m);
        return it;
    }

    boolean is(ItemStack it) {
        return it != null && it.getType() == Material.PUMPKIN_PIE && it.hasItemMeta() && MODEL.equals(it.getItemMeta().getItemModel());
    }

    void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        if (plugin.tumourCrop() == null) return;
        ShapelessRecipe r = new ShapelessRecipe(recipeKey, create(1));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.tumourCrop().tumour(1)));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.tumourCrop().tumour(1)));
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.NETHER_WART));
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.EGG, Material.BROWN_EGG, Material.BLUE_EGG));
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.SUGAR));
        r.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.MISC);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
    }

    /** Inhumans: just food. The living: nothing... then, half a minute later, it starts growing in them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!is(e.getItem())) return;
        Player p = e.getPlayer();
        if (plugin.inhuman() != null && plugin.inhuman().is(p)) return;
        java.util.UUID id = p.getUniqueId();
        long delay = plugin.getConfig().getLong("tumour-pie.delay-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q == null || q.isDead() || (plugin.inhuman() != null && plugin.inhuman().is(q))) return;
            q.sendMessage(Component.text("Something doesn't feel right.", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, true));
            q.playSound(q.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.3f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!q.isOnline() || q.isDead()) return;
                int secs = plugin.getConfig().getInt("tumour-pie.growth-seconds", 120);
                if (plugin.growthPotions() != null) plugin.growthPotions().apply(q, GrowthPotions.Kind.GROWTH, 2, secs * 20);
                q.addPotionEffect(new PotionEffect(PotionEffectType.POISON, plugin.getConfig().getInt("tumour-pie.poison-seconds", 20) * 20, 0, false, true, true));
                q.getWorld().spawnParticle(org.bukkit.Particle.DUST, q.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xEE7884), 1.4f));
            }, 40L);
        }, delay);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { e.getPlayer().discoverRecipe(recipeKey); }
}
