package net.soulbound;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.profile.PlayerTextures;

import java.util.List;

/**
 * Long Arms: held in either hand, a sleeve slips over the fist and runs on for another forearm's length, so the arm
 * looks twice as long. Purely for looks. The pack has two models, one for slim (3 px) arms and one for classic (4 px)
 * arms; whoever holds it gets the one that matches their skin, so it sits flush. The texture is one particular skin's
 * arm, so it's seamless on that skin only.
 */
final class LongArms {
    static final NamespacedKey SLIM = new NamespacedKey("soulbound", "long_arms");
    static final NamespacedKey WIDE = new NamespacedKey("soulbound", "long_arms_wide");

    private LongArms() {}

    static ItemStack item() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Long Arms", TextColor.color(0xB06CE0)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Hold it and your arm goes on, and on.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Just for looks.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(SLIM);
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    /** Twice a second, fit any Long Arms in a player's hands to their skin's arm width. */
    static void start(Soulbound plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerInventory inv = p.getInventory();
                NamespacedKey want = slim(p) ? SLIM : WIDE;
                fit(inv.getItemInMainHand(), want);
                fit(inv.getItemInOffHand(), want);
            }
        }, 40L, 10L);
    }

    private static void fit(ItemStack it, NamespacedKey want) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return;
        ItemMeta m = it.getItemMeta();
        NamespacedKey has = m.getItemModel();
        if (has == null || !(has.equals(SLIM) || has.equals(WIDE)) || has.equals(want)) return;
        m.setItemModel(want);
        it.setItemMeta(m);
    }

    private static boolean slim(Player p) {
        try {
            PlayerProfile prof = p.getPlayerProfile();
            return prof.getTextures().getSkinModel() == PlayerTextures.SkinModel.SLIM;
        } catch (Throwable t) {
            return false;
        }
    }
}
