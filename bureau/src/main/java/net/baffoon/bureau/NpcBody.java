package net.baffoon.bureau;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * A walking lizard run by the Citizens plugin: a Mannequin NPC that really walks (Citizens moves it like a player),
 * still wearing the pack's lizard skin. Only loaded when Citizens is installed. With Sentinel too, a bodyguard
 * becomes a proper Sentinel guard.
 */
final class NpcBody {
    private static NPCRegistry registry;
    final NPC npc;
    private final double speed;
    boolean sentinel;

    private NpcBody(NPC npc, double speed) { this.npc = npc; this.speed = speed; }

    static NpcBody spawn(Bureau plugin, Location at, Lizards.Role role, Component name, double speed) {
        if (registry == null) registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        NPC npc = registry.createNPC(EntityType.MANNEQUIN, LegacyComponentSerializer.legacyAmpersand().serialize(name));
        npc.setProtected(true);
        plugin.spawning = true;
        try { npc.spawn(at); } finally { plugin.spawning = false; }
        if (!(npc.getEntity() instanceof Mannequin m)) { npc.destroy(); return null; }
        m.setProfile(Lizards.skin(role));
        m.setPersistent(false);
        m.getPersistentDataContainer().set(Walker.key, PersistentDataType.BYTE, (byte) 1);
        // the old husk walkers used the movement-speed attribute (about 0.25): turn that into a pace for Citizens
        float pace = (float) Math.max(0.5, Math.min(2.0, speed / 0.25));
        npc.getNavigator().getDefaultParameters().speedModifier(pace).range(64f).distanceMargin(1.5);
        return new NpcBody(npc, pace);
    }

    boolean valid() { return npc.isSpawned() && npc.getEntity() != null && npc.getEntity().isValid(); }
    Entity entity() { return npc.getEntity(); }
    Location at() { Entity e = npc.getEntity(); return e != null ? e.getLocation() : npc.getStoredLocation(); }

    void walkTo(LivingEntity target, double mul) {
        var nav = npc.getNavigator();
        if (nav.isNavigating() && nav.getEntityTarget() != null && target.equals(nav.getEntityTarget().getTarget())) return;
        nav.setTarget(target, false);
        nav.getLocalParameters().speedModifier((float) (speed * mul));
    }

    void stop() { if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation(); }
    void teleport(Location l) { npc.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN); }
    void face(Location l) { if (!npc.getNavigator().isNavigating()) npc.faceLocation(l); }
    void remove() { npc.destroy(); }

    void target(java.util.UUID who) { if (sentinel) try { SentinelHook.target(npc, who); } catch (Throwable ignored) {} }

    /** With Sentinel installed: guard this player and fight monsters. False if Sentinel isn't there or refused. */
    boolean guard(Player client, double damage, double health, boolean players) {
        try {
            SentinelHook.guard(npc, client, damage, health, players);
            sentinel = true;
            if (npc.getEntity() != null) npc.getEntity().getPersistentDataContainer().set(Walker.key, PersistentDataType.BYTE, (byte) 2);
        } catch (Throwable t) { sentinel = false; }
        return sentinel;
    }
}
