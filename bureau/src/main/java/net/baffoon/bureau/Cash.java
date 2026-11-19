package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Cold Hard Cash: the lizards' banknote. A real item (it stacks to 64, it can be stolen), marked so nothing else can
 * pass for it. The note's picture lives in the pack (bureau:cold_hard_cash).
 */
final class Cash {
    static final TextColor ICE = TextColor.color(0x7FE6E6);
    static final NamespacedKey MODEL = new NamespacedKey("bureau", "cold_hard_cash");
    static NamespacedKey key;

    private Cash() {}

    static ItemStack item(int n) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Cold Hard Cash", ICE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Lizard legal tender.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Kept cold. Spent colder.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(MODEL);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    static boolean is(ItemStack it) {
        return it != null && it.getType() == Material.PAPER && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** How many notes they're carrying. */
    static int count(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (is(it)) n += it.getAmount();
        if (is(p.getInventory().getItemInOffHand())) n += p.getInventory().getItemInOffHand().getAmount();
        return n;
    }

    /** Take n notes from their pockets; false (and nothing taken) if they don't have that many. */
    static boolean take(Player p, int n) {
        if (n <= 0) return true;
        if (count(p) < n) return false;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && n > 0; i++) {
            if (!is(inv[i])) continue;
            int t = Math.min(n, inv[i].getAmount());
            n -= t;
            if (t == inv[i].getAmount()) inv[i] = null; else inv[i].setAmount(inv[i].getAmount() - t);
        }
        p.getInventory().setStorageContents(inv);
        if (n > 0) {
            ItemStack off = p.getInventory().getItemInOffHand();
            if (is(off)) { int t = Math.min(n, off.getAmount()); off.setAmount(off.getAmount() - t); p.getInventory().setItemInOffHand(off.getAmount() > 0 ? off : null); }
        }
        return true;
    }

    /** Hand them n notes; whatever won't fit drops at their feet. */
    static void give(Player p, int n) {
        while (n > 0) {
            int s = Math.min(64, n);
            n -= s;
            for (ItemStack left : p.getInventory().addItem(item(s)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
