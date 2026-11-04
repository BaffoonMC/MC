package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * No dragon heads on End ships (end-ships.dragon-head: false).
 *  - New end cities generate without them: the head is swapped for air as the ship is placed.
 *  - Ships that already exist lose theirs the first time each chunk of them loads (each chunk is checked once).
 */
final class EndShips implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey checked;

    EndShips(Soulbound plugin) { this.plugin = plugin; this.checked = new NamespacedKey(plugin, "end_ship_heads_checked"); }

    private boolean noHeads() { return !plugin.getConfig().getBoolean("end-ships.dragon-head", false); }

    private static boolean isHead(Material m) { return m == Material.DRAGON_HEAD || m == Material.DRAGON_WALL_HEAD; }

    @EventHandler
    public void onGenerate(AsyncStructureGenerateEvent e) {
        if (!noHeads() || !Structure.END_CITY.equals(e.getStructure())) return;
        e.setBlockTransformer(new NamespacedKey(plugin, "no_dragon_heads"),
                (region, x, y, z, current, state) -> isHead(current.getType()) ? Material.AIR.createBlockData().createBlockState() : current);
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!noHeads() || e.getWorld().getEnvironment() != World.Environment.THE_END) return;
        Chunk c = e.getChunk();
        if (c.getPersistentDataContainer().has(checked, PersistentDataType.BYTE)) return;
        var cities = c.getStructures(Structure.END_CITY);
        if (cities.isEmpty()) return;   // nothing to do, and new cities are already handled at generation
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!c.isLoaded()) return;
            int bx = c.getX() << 4, bz = c.getZ() << 4, removed = 0;
            for (GeneratedStructure gs : cities) {
                for (StructurePiece piece : gs.getPieces()) {
                    BoundingBox b = piece.getBoundingBox();
                    int x0 = Math.max(bx, (int) Math.floor(b.getMinX())), x1 = Math.min(bx + 15, (int) Math.ceil(b.getMaxX()));
                    int z0 = Math.max(bz, (int) Math.floor(b.getMinZ())), z1 = Math.min(bz + 15, (int) Math.ceil(b.getMaxZ()));
                    int y0 = Math.max(c.getWorld().getMinHeight(), (int) Math.floor(b.getMinY())), y1 = Math.min(c.getWorld().getMaxHeight() - 1, (int) Math.ceil(b.getMaxY()));
                    for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
                        Block bl = c.getWorld().getBlockAt(x, y, z);
                        if (isHead(bl.getType())) { bl.setType(Material.AIR, false); removed++; }
                    }
                }
            }
            c.getPersistentDataContainer().set(checked, PersistentDataType.BYTE, (byte) 1);
            if (removed > 0) plugin.getLogger().info("Took the dragon head off an End ship at chunk " + c.getX() + ", " + c.getZ() + ".");
        });
    }
}
