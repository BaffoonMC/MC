package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Someone joins for the very first time: staff hear a chime and get a line in chat with a [Say hi] button.
 * On for everyone with soulbound.admin; /sb newping off (or on) for yourself.
 */
final class NewPlayerPing implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private final Soulbound plugin;
    private final NamespacedKey offKey;

    NewPlayerPing(Soulbound plugin) {
        this.plugin = plugin;
        this.offKey = new NamespacedKey(plugin, "new_player_ping_off");
    }

    boolean on(Player p) { return p.hasPermission("soulbound.admin") && !p.getPersistentDataContainer().has(offKey, PersistentDataType.BYTE); }

    void set(Player p, boolean on) {
        if (on) p.getPersistentDataContainer().remove(offKey);
        else p.getPersistentDataContainer().set(offKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player n = e.getPlayer();
        if (n.hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!n.isOnline()) return;
            for (Player s : Bukkit.getOnlinePlayers()) {
                if (s.equals(n) || !on(s)) continue;
                s.sendMessage(Component.text("✦ ", GOLD).append(Component.text("New player! ", GOLD, TextDecoration.BOLD))
                        .append(Component.text(n.getName(), NamedTextColor.WHITE)).append(Component.text(" just joined for the first time.  ", NamedTextColor.GRAY))
                        .append(Component.text("[Say hi]", NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand("/msg " + n.getName() + " Welcome to BaffoonMC! Shout if you need anything."))
                                .hoverEvent(HoverEvent.showText(Component.text("Send them a welcome message", NamedTextColor.GRAY))))
                        .append(Component.text(" "))
                        .append(Component.text("[Go to them]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/tp " + n.getName()))
                                .hoverEvent(HoverEvent.showText(Component.text("Teleport to them", NamedTextColor.GRAY)))));
                s.playSound(s.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (s.isOnline()) s.playSound(s.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f); }, 4L);
            }
        }, 20L);
    }
}
