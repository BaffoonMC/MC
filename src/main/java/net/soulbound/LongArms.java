package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Long Arms: held in either hand, a sleeve of the owner's own skin slips over the fist and runs on for another
 * forearm's length, so the arm looks twice as long. Purely for looks; the pack holds the model (soulbound:long_arms),
 * which is textured from one particular skin, so it's seamless on that skin only.
 */
final class LongArms {
    private LongArms() {}

    static ItemStack item() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Long Arms", TextColor.color(0xB06CE0)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Hold it and your arm goes on, and on.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Just for looks.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(new NamespacedKey("soulbound", "long_arms"));
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }
}
