package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** A small public door for BaffoonMC's other plugins (BaffoonVotes asks for "hat_token" for its shop lists). */
public final class SoulboundApi {
    private SoulboundApi() {}

    /** One of Soulbound's items by id, or null if there's no such thing. */
    public static ItemStack item(String id, int amount) {
        if (!(Bukkit.getPluginManager().getPlugin("Soulbound") instanceof Soulbound sb) || !sb.isEnabled()) return null;
        if (id.equalsIgnoreCase("hat_token") && sb.wardrobe() != null) return sb.wardrobe().token(Math.max(1, amount));
        return null;
    }
}
