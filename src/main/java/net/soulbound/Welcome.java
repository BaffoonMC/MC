package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;

/** A private hello on someone's very first join: a title, and a few lines in chat with a link to the rules. */
final class Welcome implements Listener {
    private static final TextColor GOLD = TextColor.color(0xFFAA00);
    private final Soulbound plugin;

    Welcome(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPlayedBefore() || !plugin.getConfig().getBoolean("welcome.enabled", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) show(p); }, 40L);
    }

    void show(Player p) {
        var c = plugin.getConfig();
        String rules = c.getString("welcome.rules-url", "https://store.playbaffoonmc.net/#rules");
        String store = c.getString("welcome.store-url", "https://store.playbaffoonmc.net");
        p.showTitle(Title.title(
                Component.text("Welcome to BaffoonMC", GOLD, TextDecoration.BOLD),
                Component.text("Make yourself at home, " + p.getName(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(800), Duration.ofSeconds(5), Duration.ofMillis(1200))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f), 4L);

        Component arrow = Component.text("> ", NamedTextColor.DARK_GRAY);
        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Welcome, ", GOLD).append(Component.text(p.getName(), NamedTextColor.YELLOW)).append(Component.text("! Here's how to get going:", GOLD)));
        p.sendMessage(arrow.append(Component.text("Jump in the hole", NamedTextColor.GREEN)).append(Component.text(" to land somewhere wild", NamedTextColor.GRAY)));
        p.sendMessage(arrow.append(Component.text("Golden shovel", NamedTextColor.YELLOW)).append(Component.text(" - claim land so it stays yours", NamedTextColor.GRAY)));
        p.sendMessage(arrow.append(Component.text("/sethome", NamedTextColor.YELLOW)).append(Component.text(" to save your base, ", NamedTextColor.GRAY))
                .append(Component.text("/home", NamedTextColor.YELLOW)).append(Component.text(" to go back", NamedTextColor.GRAY)));
        p.sendMessage(arrow.append(Component.text("/tpa <name>", NamedTextColor.YELLOW)).append(Component.text(" to visit a friend", NamedTextColor.GRAY)));
        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("[ READ THE RULES ]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(rules))
                .hoverEvent(HoverEvent.showText(Component.text("Open the rules on the website", NamedTextColor.GRAY)))
                .append(Component.text("  click to open", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
        p.sendMessage(Component.text("Ranks & perks: ", GOLD).append(Component.text(store.replaceFirst("^https?://", ""), NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.openUrl(store))));
    }
}
