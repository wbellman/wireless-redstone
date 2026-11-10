package systems.acolyte.games.wirelessredstone.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlockEntity;

/**
 * Client entrypoint. Colors the three channel pips on each panel from the dyes stored in its
 * block entity. Everything else about the panel's look (mode, gauge, light) comes from its
 * block state and the multipart blockstate file.
 */
public class WirelessRedstoneClient implements ClientModInitializer {
	/** Color of an empty channel slot. */
	private static final int EMPTY_SLOT = 0xFF3A3A3A;

	@Override
	public void onInitializeClient() {
		// Model faces with tintindex 0, 1, 2 are the three channel pips.
		BlockColorRegistry.register(List.of(new PipTint(0), new PipTint(1), new PipTint(2)), WirelessRedstone.PANEL);
	}

	/** Tint for one pip ({@code slot} 0-2): the dye in that slot, or dark grey when empty. */
	private record PipTint(int slot) implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return EMPTY_SLOT;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			if (level.getBlockEntity(pos) instanceof WirelessPanelBlockEntity panel) {
				DyeColor dye = panel.dye(slot);
				if (dye != null) {
					return dye.getTextureDiffuseColor();
				}
			}
			return EMPTY_SLOT;
		}
	}
}
