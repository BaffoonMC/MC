package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** /store, /buy, /donate: a link to the store you can click. */
final class StoreLink implements CommandExecutor {
    private final Soulbound plugin;

    StoreLink(Soulbound plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        String url = plugin.getConfig().getString("welcome.store-url", "https://store.playbaffoonmc.net");
        String shown = url.replaceFirst("^https?://", "");
        s.sendMessage(Component.empty());
        s.sendMessage(Component.text("BaffoonMC", TextColor.color(0xFFAA00), TextDecoration.BOLD)
                .append(Component.text(" > ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Ranks, hats and perks: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(shown, NamedTextColor.YELLOW, TextDecoration.UNDERLINED).decoration(TextDecoration.BOLD, false)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Open the store", NamedTextColor.GRAY)))));
        s.sendMessage(Component.text("Thank you for keeping the lights on.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        s.sendMessage(Component.empty());
        return true;
    }
}
