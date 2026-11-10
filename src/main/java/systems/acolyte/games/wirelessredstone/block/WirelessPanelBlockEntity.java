package systems.acolyte.games.wirelessredstone.block;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;
import systems.acolyte.games.wirelessredstone.channel.Channel;
import systems.acolyte.games.wirelessredstone.channel.ChannelNetwork;

/** Holds a panel's three dye slots. Synced to clients so the pips can be drawn in color. */
public class WirelessPanelBlockEntity extends BlockEntity {
	private static final int EMPTY = -1;

	private final int[] dyes = {EMPTY, EMPTY, EMPTY};

	/** The channel this panel is registered under in the {@link ChannelNetwork} (server only). */
	private int registeredChannel = Channel.NONE;

	public WirelessPanelBlockEntity(BlockPos pos, BlockState state) {
		super(WirelessRedstone.PANEL_BLOCK_ENTITY, pos, state);
	}

	/** The dye in {@code slot} (0-2), or null if that slot is empty. */
	public @Nullable DyeColor dye(int slot) {
		return dyes[slot] == EMPTY ? null : DyeColor.byId(dyes[slot]);
	}

	/** This panel's channel, or {@link Channel#NONE} until all three slots hold a dye. */
	public int channel() {
		for (int dye : dyes) {
			if (dye == EMPTY) {
				return Channel.NONE;
			}
		}
		return Channel.of(DyeColor.byId(dyes[0]), DyeColor.byId(dyes[1]), DyeColor.byId(dyes[2]));
	}

	/** Puts a dye in the next empty slot. Returns false if all slots are full. */
	public boolean addDye(DyeColor color) {
		for (int slot = 0; slot < dyes.length; slot++) {
			if (dyes[slot] == EMPTY) {
				dyes[slot] = color.getId();
				changed();
				return true;
			}
		}
		return false;
	}

	/** Removes the most recently added dye, or returns null if there is none. */
	public @Nullable DyeColor popDye() {
		for (int slot = dyes.length - 1; slot >= 0; slot--) {
			if (dyes[slot] != EMPTY) {
				DyeColor color = DyeColor.byId(dyes[slot]);
				dyes[slot] = EMPTY;
				changed();
				return color;
			}
		}
		return null;
	}

	private void changed() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Keeps the network registration in step with the dyes. Returns the channel the panel
	 * was on before (so the caller can clear a transmitter's old signal), or the current
	 * channel if nothing changed.
	 */
	int syncRegistration(ChannelNetwork network) {
		int channel = channel();
		int previous = registeredChannel;
		if (channel != previous) {
			network.removeListener(previous, worldPosition);
			network.addListener(channel, worldPosition);
			registeredChannel = channel;
		}
		return previous;
	}

	/** Called when the chunk loads. */
	public void onLoad(ServerLevel level) {
		ChannelNetwork network = ChannelNetwork.get(level);
		registeredChannel = channel();
		network.addListener(registeredChannel, worldPosition);
		network.queueLoad(worldPosition);
	}

	/** Called when the chunk unloads or the block is removed. */
	public void onUnload(ServerLevel level) {
		ChannelNetwork.get(level).removeListener(registeredChannel, worldPosition);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			ChannelNetwork network = ChannelNetwork.get(serverLevel);
			network.removeListener(registeredChannel, pos);
			network.setSource(serverLevel, registeredChannel, pos, 0);
			for (int slot = 0; slot < dyes.length; slot++) {
				if (dyes[slot] != EMPTY) {
					Block.popResource(serverLevel, pos, new ItemStack(Items.DYE.pick(DyeColor.byId(dyes[slot]))));
					dyes[slot] = EMPTY;
				}
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putIntArray("dyes", dyes.clone());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Arrays.fill(dyes, EMPTY);
		input.getIntArray("dyes").ifPresent(saved -> {
			for (int slot = 0; slot < Math.min(saved.length, dyes.length); slot++) {
				dyes[slot] = saved[slot] >= 0 && saved[slot] < 16 ? saved[slot] : EMPTY;
			}
		});
		// On the client, redraw the pips with the new colors.
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
