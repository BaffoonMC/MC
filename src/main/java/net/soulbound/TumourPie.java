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
 * throbs gently. Inhumans treat it as their kind of food (a big meal and a few seconds of Regeneration); the living get
 * fed too, but it turns their stomach: they were told not to chew.
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!is(e.getItem())) return;
        Player p = e.getPlayer();
        boolean inhuman = plugin.inhuman() != null && plugin.inhuman().is(p);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (inhuman) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, false, true, true));
                p.sendActionBar(Component.text("It goes down in one. You could eat another.", TextColor.color(0xEE7884)));
            } else {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, plugin.getConfig().getInt("tumour-pie.nausea-seconds", 8) * 20, 0, false, true, true));
                p.sendActionBar(Component.text("You chewed, didn't you.", NamedTextColor.GRAY));
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { e.getPlayer().discoverRecipe(recipeKey); }
}
