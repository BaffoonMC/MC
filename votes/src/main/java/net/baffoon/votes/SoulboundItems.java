package net.baffoon.votes;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Soulbound's own items for the shop lists ("sb:hat_token"), asked for through its small public API if it's installed. */
final class SoulboundItems {
    private SoulboundItems() {}

    static ItemStack get(String id, int amount) {
        Plugin sb = Bukkit.getPluginManager().getPlugin("Soulbound");
        if (sb == null || !sb.isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.soulbound.SoulboundApi", true, sb.getClass().getClassLoader());
            Object it = api.getMethod("item", String.class, int.class).invoke(null, id, amount);
            return it instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
