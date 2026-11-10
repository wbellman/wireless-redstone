package systems.acolyte.games.wirelessredstone.test;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;

/**
 * Builds a showcase of panels in a real client and saves screenshots, so the models,
 * textures, pip colors, gauge and light can be checked by eye.
 *   ./gradlew :wireless-redstone:runClientGameTest   (screenshots land in build/run/clientGameTest/screenshots)
 */
public class PanelScreenshotTest implements FabricClientGameTest {
	private static final String PANEL = "wireless_redstone:wireless_panel";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("fill -1 99 -1 11 99 7 minecraft:smooth_stone");
			server.runCommand("fill -1 100 -1 11 104 7 minecraft:air");
			server.runCommand("fill -1 100 0 11 102 0 minecraft:stone");

			// Red/Lime/Blue: a powered floor transmitter and its floor + wall receivers.
			panel(server, 1, 3, "floor", "south", "transmitter", "14,5,11");
			server.runCommand("setblock 1 100 4 minecraft:redstone_block");
			panel(server, 3, 3, "floor", "north", "receiver", "14,5,11");
			server.runCommand("setblock 3 100 2 minecraft:redstone_lamp");
			server.runCommand("setblock 2 101 1 " + PANEL + "[face=wall,facing=south,mode=receiver]{dyes:[I;14,5,11]}");

			// Yellow x3: a transmitter fed by dust at strength 11, and its receiver.
			panel(server, 5, 3, "floor", "south", "transmitter", "4,4,4");
			server.runCommand("fill 5 100 4 9 100 4 minecraft:redstone_wire");
			server.runCommand("setblock 10 100 4 minecraft:redstone_block");
			panel(server, 7, 3, "floor", "north", "receiver", "4,4,4");

			// An empty panel (no channel) and an idle wall transmitter.
			server.runCommand("setblock 9 100 2 " + PANEL + "[face=floor,facing=north,mode=transmitter]");
			server.runCommand("setblock 6 101 1 " + PANEL + "[face=wall,facing=south,mode=transmitter]{dyes:[I;0,15,1]}");

			// A ceiling receiver on Red/Lime/Blue, hanging from a stone block.
			server.runCommand("setblock 9 103 6 minecraft:stone");
			server.runCommand("setblock 9 102 6 " + PANEL + "[face=ceiling,facing=north,mode=receiver]{dyes:[I;14,5,11]}");

			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			server.runCommand("gamemode spectator @a");
			server.runCommand("tp @a 5 103.5 9 180 30");
			context.waitTicks(100);
			log(context.takeScreenshot("wireless_overview"));

			server.runCommand("tp @a 3 102.2 5.2 180 55");
			context.waitTicks(40);
			log(context.takeScreenshot("wireless_closeup"));

			server.runCommand("tp @a 4 101.6 3.6 180 15");
			context.waitTicks(40);
			log(context.takeScreenshot("wireless_wall"));

			server.runCommand("tp @a 9 100.2 7.6 180 -60");
			context.waitTicks(40);
			log(context.takeScreenshot("wireless_ceiling"));
		}
	}

	private static void panel(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
		int x, int z, String face, String facing, String mode, String dyes) {
		server.runCommand("setblock " + x + " 100 " + z + " " + PANEL
			+ "[face=" + face + ",facing=" + facing + ",mode=" + mode + "]{dyes:[I;" + dyes + "]}");
	}

	private static void log(Path screenshot) {
		WirelessRedstone.LOGGER.info("SCREENSHOT {}", screenshot.toAbsolutePath());
	}
}
