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

    /** Seconds of Moloch's Curse this player has left (0 = not cursed). */
    public static int curseSeconds(org.bukkit.entity.Player p) {
        if (!(Bukkit.getPluginManager().getPlugin("Soulbound") instanceof Soulbound sb) || sb.curses() == null) return 0;
        return sb.curses().secondsLeft(p);
    }

    /** Take seconds off their curse; lifts it entirely if that covers what's left. */
    public static void reduceCurse(org.bukkit.entity.Player p, int seconds) {
        if (Bukkit.getPluginManager().getPlugin("Soulbound") instanceof Soulbound sb && sb.curses() != null) sb.curses().reduce(p, seconds);
    }

    /** Is the Moloch fight on right now? */
    public static boolean molochFighting() {
        return Bukkit.getPluginManager().getPlugin("Soulbound") instanceof Soulbound sb && sb.boss() != null && sb.boss().active();
    }
}
