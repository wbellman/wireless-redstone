package systems.acolyte.games.wirelessredstone.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import systems.acolyte.games.wirelessredstone.channel.Channel;
import systems.acolyte.games.wirelessredstone.channel.ChannelNetwork;
import systems.acolyte.games.wirelessredstone.item.RemoteItem;

/**
 * A wireless panel: a transmitter or a receiver, attached to any face.
 *
 * <p>Floor panels work through their front, the side the arrow points to (the direction the
 * player faced when placing): a transmitter reads the block there, a receiver powers it like
 * a repeater's output. Wall and ceiling panels act like
 * levers (they read, or power, the block they are mounted on).
 *
 * <p>Each step of a signal change happens on a scheduled tick of {@link #STEP_DELAY}: the
 * transmitter reads its input on one tick and its receivers update on the next, for a fixed
 * delay of 2 game ticks.
 */
public class WirelessPanelBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
	public static final EnumProperty<PanelMode> MODE = EnumProperty.create("mode", PanelMode.class);
	public static final IntegerProperty POWER = BlockStateProperties.POWER;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final int STEP_DELAY = 1;

	/** A full 16x16 plate, 2 px thick, on the south wall; rotated for every face and facing. */
	private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES =
		Shapes.rotateAttachFace(Block.boxZ(16.0, 16.0, 14.0, 16.0));

	public WirelessPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(FACE, AttachFace.FLOOR)
			.setValue(FACING, Direction.NORTH)
			.setValue(MODE, PanelMode.TRANSMITTER)
			.setValue(POWER, 0)
			.setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, MODE, POWER, LIT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WirelessPanelBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACE)).get(state.getValue(FACING));
	}

	public static Direction supportDirection(BlockState state) {
		return getConnectedDirection(state).getOpposite();
	}

	// ---------------------------------------------------------------- interaction

	@Override
	protected InteractionResult useItemOn(
		ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
	) {
		if (stack.getItem() instanceof RemoteItem) {
			// The remote copies this panel's channel (RemoteItem.useOn) instead of toggling the panel.
			return InteractionResult.PASS;
		}
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level.getBlockEntity(pos) instanceof WirelessPanelBlockEntity panel) || panel.channel() != Channel.NONE) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel && panel.addDye(dye)) {
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			refresh(state, serverLevel, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (level.getBlockEntity(pos) instanceof WirelessPanelBlockEntity panel) {
				DyeColor removed = panel.popDye();
				if (removed != null) {
					ItemStack dyeStack = new ItemStack(Items.DYE.pick(removed));
					if (!player.getInventory().add(dyeStack)) {
						Block.popResource(level, pos, dyeStack);
					}
					level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
					refresh(state, serverLevel, pos);
				}
			}
			return InteractionResult.SUCCESS;
		}

		BlockState toggled = state.cycle(MODE).setValue(POWER, 0).setValue(LIT, false);
		level.setBlock(pos, toggled, Block.UPDATE_ALL);
		boolean nowReceiver = toggled.getValue(MODE) == PanelMode.RECEIVER;
		level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, nowReceiver ? 0.55F : 0.5F);
		if (state.getValue(MODE) == PanelMode.RECEIVER && state.getValue(POWER) > 0) {
			updateOutputNeighbours(toggled, level, pos);
		}
		refresh(toggled, serverLevel, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, STEP_DELAY);
		}
	}

	// ---------------------------------------------------------------- updates

	@Override
	protected void neighborChanged(
		BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston
	) {
		if (state.getValue(MODE) == PanelMode.TRANSMITTER
			&& readInput(level, pos, state) != state.getValue(POWER)
			&& !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, STEP_DELAY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		refresh(state, level, pos);
	}

	/** Re-reads everything for this panel and brings its block state and the network up to date. */
	private void refresh(BlockState state, ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof WirelessPanelBlockEntity panel)) {
			return;
		}
		ChannelNetwork network = ChannelNetwork.get(level);
		int channel = panel.channel();
		int previousChannel = panel.syncRegistration(network);
		if (previousChannel != channel) {
			network.setSource(level, previousChannel, pos, 0);
		}

		int power;
		boolean lit;
		if (state.getValue(MODE) == PanelMode.TRANSMITTER) {
			power = readInput(level, pos, state);
			network.setSource(level, channel, pos, power);
			lit = network.strength(level, channel, pos) > 0;
		} else {
			network.setSource(level, channel, pos, 0);
			power = network.strength(level, channel, pos);
			lit = power > 0;
		}

		BlockState updated = state.setValue(POWER, power).setValue(LIT, lit);
		if (updated != state) {
			level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
			if (state.getValue(MODE) == PanelMode.RECEIVER && state.getValue(POWER) != power) {
				updateOutputNeighbours(updated, level, pos);
			}
		}
	}

	/**
	 * What a transmitter sees: the block its arrow points at (floor), or the block it is mounted on plus any
	 * dust running into it (wall, ceiling).
	 */
	private static int readInput(Level level, BlockPos pos, BlockState state) {
		boolean floor = state.getValue(FACE) == AttachFace.FLOOR;
		Direction toInput = floor ? state.getValue(FACING) : supportDirection(state);
		BlockPos inputPos = pos.relative(toInput);
		int signal = Math.max(level.getSignal(inputPos, toInput), wirePower(level, inputPos, toInput));
		if (!floor) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (side != toInput) {
					signal = Math.max(signal, wirePower(level, pos.relative(side), side));
				}
			}
		}
		return Math.min(signal, 15);
	}

	/** Power of the dust at {@code wirePos} if it runs into us ({@code toWire} points from us to it), else 0. */
	private static int wirePower(Level level, BlockPos wirePos, Direction toWire) {
		BlockState wire = level.getBlockState(wirePos);
		if (!wire.is(Blocks.REDSTONE_WIRE)
			|| !wire.getValue(RedstoneWireBlock.PROPERTY_BY_DIRECTION.get(toWire.getOpposite())).isConnected()) {
			return 0;
		}
		return wire.getValue(RedstoneWireBlock.POWER);
	}

	private void updateOutputNeighbours(BlockState state, Level level, BlockPos pos) {
		if (state.getValue(FACE) == AttachFace.FLOOR) {
			// Repeater-style: the block in front, and everything around it.
			Direction front = state.getValue(FACING);
			BlockPos frontPos = pos.relative(front);
			Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front, Direction.UP);
			level.neighborChanged(frontPos, this, orientation);
			level.updateNeighborsAtExceptFromFacing(frontPos, this, front.getOpposite(), orientation);
		} else {
			// Lever-style: our neighbours, and the neighbours of the block we are mounted on.
			Direction toSupport = supportDirection(state);
			Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
				level, toSupport, toSupport.getAxis().isHorizontal() ? Direction.UP : state.getValue(FACING));
			level.updateNeighborsAt(pos, this, orientation);
			level.updateNeighborsAt(pos.relative(toSupport), this, orientation);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (state.getValue(MODE) == PanelMode.RECEIVER && state.getValue(POWER) > 0) {
			updateOutputNeighbours(state, level, pos);
		}
	}

	// ---------------------------------------------------------------- redstone output

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		if (state.getValue(MODE) != PanelMode.RECEIVER) {
			return 0;
		}
		if (state.getValue(FACE) == AttachFace.FLOOR) {
			// direction points from the neighbour asking towards us, so the front neighbour asks with -FACING.
			return direction == state.getValue(FACING).getOpposite() ? state.getValue(POWER) : 0;
		}
		return state.getValue(POWER);
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		if (state.getValue(MODE) != PanelMode.RECEIVER) {
			return 0;
		}
		if (state.getValue(FACE) == AttachFace.FLOOR) {
			return getSignal(state, level, pos, direction);
		}
		return getConnectedDirection(state) == direction ? state.getValue(POWER) : 0;
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
		// Dust only joins a panel where the panel actually reads or outputs, so "looks connected" means
		// "is connected". direction points from the dust towards us.
		if (direction == null) {
			return false;
		}
		if (state.getValue(FACE) != AttachFace.FLOOR) {
			return true; // wall/ceiling: receivers power all around, transmitters read dust running in
		}
		return direction == state.getValue(FACING).getOpposite(); // floor: dust at the arrow end only
	}
}
