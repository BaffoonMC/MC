package net.soulbound;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Menu buttons are real items underneath (a bundle, a potion, an iron chestplate...), and Minecraft adds its own lines
 * to their tooltips: "Can hold a mixed stack of items", "No Effects", "When on Body: +6 Armor". This hides all of that
 * on buttons, so only our own name and lore show. Never use it on the actual goods being sold.
 */
final class MenuIcons {
    private MenuIcons() { }

    private static final TooltipDisplay QUIET = TooltipDisplay.tooltipDisplay().hiddenComponents(Set.of(
            DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS,
            DataComponentTypes.POTION_CONTENTS, DataComponentTypes.BUNDLE_CONTENTS, DataComponentTypes.CONTAINER,
            DataComponentTypes.DYED_COLOR, DataComponentTypes.TRIM, DataComponentTypes.UNBREAKABLE, DataComponentTypes.JUKEBOX_PLAYABLE,
            DataComponentTypes.FIREWORKS, DataComponentTypes.FIREWORK_EXPLOSION, DataComponentTypes.BANNER_PATTERNS,
            DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, DataComponentTypes.INSTRUMENT, DataComponentTypes.WRITTEN_BOOK_CONTENT,
            DataComponentTypes.POT_DECORATIONS, DataComponentTypes.CHARGED_PROJECTILES, DataComponentTypes.MAP_ID)).build();

    /** Strips the vanilla tooltip lines from a menu button (in place) and returns it. */
    static ItemStack quiet(ItemStack it) {
        if (it == null || it.getType().isAir()) return it;
        try { it.setData(DataComponentTypes.TOOLTIP_DISPLAY, QUIET); } catch (Throwable ignored) { }
        return it;
    }
}
