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
 * Cloud Candy: candy floss spun from Heaven's Cloud Cotton (2 cotton + sugar + a stick). Cotton only grows in Heaven,
 * so it's worth the trip: a big meal, then you float (Slow Falling), and it cures what the Tumour Pie does (the
 * swelling and the poison). Inhumans can't eat it: it melts away to nothing.
 */
final class CloudCandy implements Listener {
    private static final NamespacedKey MODEL = new NamespacedKey("soulbound", "cloud_candy");
    private static final TextColor NAME = TextColor.color(0xBFDDFF);
    private final Soulbound plugin;
    private final NamespacedKey recipeKey;

    CloudCandy(Soulbound plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "cloud_candy");
    }

    ItemStack create(int amount) {
        ItemStack it = new ItemStack(Material.COOKIE, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Cloud Candy", NAME).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Spun from Heaven's own cotton.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Light as air. Somehow very filling.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(MODEL);
        FoodComponent food = m.getFood();
        food.setNutrition(Math.max(1, plugin.getConfig().getInt("cloud-candy.hunger", 10)));
        food.setSaturation((float) plugin.getConfig().getDouble("cloud-candy.saturation", 16.0));
        m.setFood(food);
        m.setMaxStackSize(16);
        it.setItemMeta(m);
        return it;
    }

    boolean is(ItemStack it) {
        return it != null && it.getType() == Material.COOKIE && it.hasItemMeta() && MODEL.equals(it.getItemMeta().getItemModel());
    }

    void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        if (plugin.cloudCotton() == null) return;
        ShapelessRecipe r = new ShapelessRecipe(recipeKey, create(1));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.cloudCotton().cotton(1)));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.cloudCotton().cotton(1)));
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.SUGAR));
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.STICK));
        r.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.MISC);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
    }

    /** Inhumans can't get it down (checked before the inhuman food rules run). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInhumanBite(PlayerItemConsumeEvent e) {
        if (!is(e.getItem()) || plugin.inhuman() == null || !plugin.inhuman().is(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("It dissolves to nothing on your tongue.", NamedTextColor.GRAY));
        e.getPlayer().getWorld().spawnParticle(Particle.CLOUD, e.getPlayer().getEyeLocation(), 6, 0.15, 0.1, 0.15, 0.01);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!is(e.getItem())) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, plugin.getConfig().getInt("cloud-candy.float-seconds", 30) * 20, 0, false, true, true));
            boolean poisoned = p.hasPotionEffect(PotionEffectType.POISON) && (plugin.inhuman() == null || !plugin.inhuman().is(p));
            if (plugin.growthPotions() != null) plugin.growthPotions().clear(p);
            if (poisoned) p.removePotionEffect(PotionEffectType.POISON);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0.02);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            p.sendActionBar(Component.text(poisoned ? "Sweet, and the sickness lifts. You feel light." : "Sweet as anything. You feel light.", NAME));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { e.getPlayer().discoverRecipe(recipeKey); }
}
