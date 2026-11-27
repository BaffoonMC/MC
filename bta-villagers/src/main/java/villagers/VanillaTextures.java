package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The classic villager textures, read from the player's own copy of Minecraft (they are not shipped with this mod).
 * Looked for, in order: config/btavillagers/farmer.png and friends, then any Minecraft 1.8-1.13 jar found in the
 * launcher's folders. What is found is copied into config/btavillagers so the search only happens once.
 * If nothing is found the mod's own drawn textures are used.
 */
@Environment(EnvType.CLIENT)
public final class VanillaTextures {
	private VanillaTextures() {}

	private static final String IN_JAR = "assets/minecraft/textures/entity/villager/";
	/** One image per profession (Villagers.PROFESSIONS order), or null while searching or when nothing was found. */
	public static volatile BufferedImage[] images;

	public static void findInBackground() {
		Thread t = new Thread(() -> {
			try { images = find(); }
			catch (Throwable e) { Villagers.LOG.warn("Villagers: couldn't look for the classic textures: {}", e.toString()); }
		}, "villagers-textures");
		t.setDaemon(true);
		t.start();
	}

	private static File folder() { return FabricLoader.getInstance().getConfigDir().resolve("btavillagers").toFile(); }

	private static BufferedImage[] find() throws Exception {
		File dir = folder();
		BufferedImage[] own = fromFolder(dir);
		if (own != null) { Villagers.LOG.info("Villagers: using the textures in {}", dir); return own; }
		for (File jar : candidateJars()) {
			BufferedImage[] got = fromJar(jar);
			if (got == null) continue;
			Villagers.LOG.info("Villagers: using the classic villager textures from your own {}", jar);
			try {
				dir.mkdirs();
				for (int i = 0; i < got.length; i++) ImageIO.write(got[i], "png", new File(dir, Villagers.PROFESSIONS[i] + ".png"));
			} catch (Exception e) { Villagers.LOG.warn("Villagers: couldn't save a copy to {}: {}", dir, e.toString()); }
			return got;
		}
		Villagers.LOG.info("Villagers: no Minecraft 1.8-1.13 jar found, so the mod's own textures are used. "
			+ "To use others, put farmer.png, librarian.png, priest.png, smith.png and butcher.png (64x64) in {}", dir);
		return null;
	}

	private static boolean fits(BufferedImage img) { return img != null && img.getWidth() == 64 && img.getHeight() == 64; }

	private static BufferedImage[] fromFolder(File dir) {
		BufferedImage[] out = new BufferedImage[Villagers.PROFESSIONS.length];
		for (int i = 0; i < out.length; i++) {
			File f = new File(dir, Villagers.PROFESSIONS[i] + ".png");
			if (!f.isFile()) return null;
			try { out[i] = ImageIO.read(f); } catch (Exception e) { return null; }
			if (!fits(out[i])) return null;
		}
		return out;
	}

	private static BufferedImage[] fromJar(File jar) {
		try (ZipFile zip = new ZipFile(jar)) {
			BufferedImage[] out = new BufferedImage[Villagers.PROFESSIONS.length];
			for (int i = 0; i < out.length; i++) {
				ZipEntry e = zip.getEntry(IN_JAR + Villagers.PROFESSIONS[i] + ".png");
				if (e == null) return null;
				try (InputStream in = zip.getInputStream(e)) { out[i] = ImageIO.read(in); }
				if (!fits(out[i])) return null;      // 1.14 and later use a different layout
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	private static void jarsIn(File dir, int depth, List<File> out) {
		File[] list = dir.listFiles();
		if (list == null) return;
		for (File f : list) {
			if (f.isDirectory()) { if (depth > 0) jarsIn(f, depth - 1, out); }
			else if (f.getName().endsWith(".jar")) out.add(f);
		}
	}

	private static List<File> candidateJars() {
		Set<File> roots = new LinkedHashSet<>();
		// the launcher this instance lives in (MultiMC and Prism keep the game jars under libraries/com/mojang/minecraft)
		Path p = FabricLoader.getInstance().getGameDir().toAbsolutePath();
		for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
			roots.add(p.resolve("libraries").resolve("com").resolve("mojang").resolve("minecraft").toFile());
			roots.add(p.resolve("versions").toFile());
		}
		// the official launcher
		String home = System.getProperty("user.home", "."), appdata = System.getenv("APPDATA");
		if (appdata != null) roots.add(new File(appdata, ".minecraft/versions"));
		roots.add(new File(home, ".minecraft/versions"));
		roots.add(new File(home, "Library/Application Support/minecraft/versions"));
		List<File> jars = new ArrayList<>();
		for (File r : roots) if (r.isDirectory()) jarsIn(r, 1, jars);
		return jars;
	}
}
