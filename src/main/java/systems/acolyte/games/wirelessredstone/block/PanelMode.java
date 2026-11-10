package systems.acolyte.games.wirelessredstone.block;

import net.minecraft.util.StringRepresentable;

/**
 * What a wireless panel does, stored in its block state ({@code mode=transmitter|receiver}).
 * New panels start as transmitters; right-clicking with an empty hand toggles the mode.
 */
public enum PanelMode implements StringRepresentable {
	/** Reads a redstone signal and sends it on the panel's channel. */
	TRANSMITTER("transmitter"),
	/** Outputs the strongest signal on the panel's channel. */
	RECEIVER("receiver");

	private final String name;

	PanelMode(String name) {
		this.name = name;
	}

	/** The other mode. */
	public PanelMode toggle() {
		return this == TRANSMITTER ? RECEIVER : TRANSMITTER;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
