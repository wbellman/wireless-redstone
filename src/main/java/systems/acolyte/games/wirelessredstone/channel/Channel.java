package systems.acolyte.games.wirelessredstone.channel;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;

/**
 * A channel is three dye colors in order, packed into an int from 0 to 4095.
 * {@link #NONE} means "no channel" (fewer than three dyes).
 */
public final class Channel {
	public static final int NONE = -1;
	public static final int SLOTS = 3;

	private Channel() {
	}

	/** The channel for three dyes, in slot order. */
	public static int of(DyeColor first, DyeColor second, DyeColor third) {
		return first.getId() * 256 + second.getId() * 16 + third.getId();
	}

	/** The dye in {@code slot} (0-2) of {@code channel}. */
	public static DyeColor color(int channel, int slot) {
		return DyeColor.byId((channel >> (4 * (SLOTS - 1 - slot))) & 0xF);
	}

	/** e.g. "Red / Lime / Blue" in each dye's color. */
	public static Component describe(int channel) {
		if (channel == NONE) {
			return Component.translatable("wireless_redstone.channel.none");
		}
		MutableComponent text = Component.empty();
		for (int slot = 0; slot < SLOTS; slot++) {
			DyeColor color = color(channel, slot);
			if (slot > 0) {
				text.append(Component.literal(" / "));
			}
			text.append(Component.translatable("color.minecraft." + color.getName()).withColor(color.getTextColor()));
		}
		return text;
	}
}
