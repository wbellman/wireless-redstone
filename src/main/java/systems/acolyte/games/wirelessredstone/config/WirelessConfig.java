package systems.acolyte.games.wirelessredstone.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;

/** Server settings, read from config/wireless_redstone.json (created with defaults if missing). */
public final class WirelessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = WirelessRedstone.MOD_ID + ".json";

	private static volatile Values current = new Values();

	private WirelessConfig() {
	}

	/** Maximum transmitter-to-receiver distance in blocks; 0 means unlimited. */
	public static int maxRange() {
		return current.maxRange;
	}

	/** Reads the config file (or writes the defaults) when the server starts. */
	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Values values = new Values();
		try {
			if (Files.exists(file)) {
				Values read = GSON.fromJson(Files.readString(file), Values.class);
				if (read != null) {
					values = read;
				}
			}
			values.maxRange = Math.max(0, values.maxRange);
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(values));
		} catch (IOException | JsonParseException e) {
			WirelessRedstone.LOGGER.error("Could not read {}; using defaults", file, e);
		}
		current = values;
		WirelessRedstone.LOGGER.info("maxRange = {}", values.maxRange == 0 ? "unlimited" : values.maxRange);
	}

	private static final class Values {
		int maxRange = 0;
	}
}
