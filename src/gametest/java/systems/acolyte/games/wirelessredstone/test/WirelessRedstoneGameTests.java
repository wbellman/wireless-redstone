package systems.acolyte.games.wirelessredstone.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;
import systems.acolyte.games.wirelessredstone.block.PanelMode;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlock;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlockEntity;
import systems.acolyte.games.wirelessredstone.channel.Channel;
import systems.acolyte.games.wirelessredstone.channel.ChannelNetwork;
import systems.acolyte.games.wirelessredstone.item.RemoteItem;

/**
 * Headless in-game tests. Tests can run side by side in one world, and channels are
 * per world, so every test uses its own dye combination.
 *
 * Layout notes: y=0 is a stone floor laid by each test; panels sit at y=1.
 * A floor panel works through the side its arrow points to: a receiver facing NORTH outputs
 * north (-z); a transmitter facing SOUTH reads its input from the south (+z).
 */
public class WirelessRedstoneGameTests {

	@GameTest
	public void floorSignalReachesReceiverAfterTwoTicks(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 2);
		BlockPos input = transmitter.south();
		BlockPos receiver = new BlockPos(5, 1, 2);
		BlockPos lamp = receiver.north();

		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.RED, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.RED, DyeColor.RED, DyeColor.RED);
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);

		// Let placement settle, then power the transmitter's back and time the receiver.
		helper.runAfterDelay(5, () -> {
			helper.setBlock(input, Blocks.REDSTONE_BLOCK);
			helper.runAfterDelay(1, () -> helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0));
			helper.runAfterDelay(2, () -> {
				helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
				helper.assertBlockProperty(receiver, WirelessPanelBlock.LIT, true);
			});
			helper.runAfterDelay(4, () -> {
				helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
				helper.setBlock(input, Blocks.AIR);
			});
			helper.runAfterDelay(8, () -> {
				helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
				helper.assertBlockProperty(receiver, WirelessPanelBlock.LIT, false);
				helper.succeed();
			});
		});
	}

	@GameTest
	public void floorReceiverOnlyPowersItsFront(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 3);
		BlockPos receiver = new BlockPos(5, 1, 3);
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.BLUE, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.BLUE, DyeColor.RED, DyeColor.RED);
		helper.setBlock(receiver.north(), Blocks.REDSTONE_LAMP);
		helper.setBlock(receiver.south(), Blocks.REDSTONE_LAMP);
		helper.setBlock(receiver.east(), Blocks.REDSTONE_LAMP);
		helper.setBlock(transmitter.south(), Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(receiver.north(), RedstoneLampBlock.LIT, true);
			helper.assertBlockProperty(receiver.south(), RedstoneLampBlock.LIT, false);
			helper.assertBlockProperty(receiver.east(), RedstoneLampBlock.LIT, false);
			helper.succeed();
		});
	}

	@GameTest
	public void floorTransmitterIgnoresItsSides(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 3);
		BlockPos receiver = new BlockPos(5, 1, 3);
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.GREEN, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.GREEN, DyeColor.RED, DyeColor.RED);
		helper.setBlock(transmitter.east(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(transmitter.north(), Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			helper.succeed();
		});
	}

	@GameTest
	public void channelCarriesStrongestAnalogSignal(GameTestHelper helper) {
		floor(helper);
		// Transmitter A is fed by dust two blocks from a redstone block (strength 14).
		BlockPos a = new BlockPos(1, 1, 1);
		helper.setBlock(a.south(), Blocks.REDSTONE_WIRE);
		helper.setBlock(a.south().south(), Blocks.REDSTONE_WIRE);
		// Transmitter B is fed directly by a redstone block (15) that we remove later.
		BlockPos b = new BlockPos(4, 1, 1);
		BlockPos receiver = new BlockPos(6, 1, 5);

		placePanel(helper, a, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.YELLOW, DyeColor.RED, DyeColor.RED);
		placePanel(helper, b, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.YELLOW, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.YELLOW, DyeColor.RED, DyeColor.RED);
		helper.setBlock(a.south().south().south(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(b.south(), Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(a, WirelessPanelBlock.POWER, 14);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.setBlock(b.south(), Blocks.AIR);
		});
		helper.runAfterDelay(16, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 14);
			helper.succeed();
		});
	}

	@GameTest
	public void differentChannelsDoNotMix(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 3);
		BlockPos sameChannel = new BlockPos(4, 1, 3);
		BlockPos otherChannel = new BlockPos(6, 1, 3);
		BlockPos noChannel = new BlockPos(4, 1, 6);
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.PURPLE, DyeColor.RED, DyeColor.RED);
		placePanel(helper, sameChannel, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.PURPLE, DyeColor.RED, DyeColor.RED);
		// Same colors, different order: a different channel.
		placePanel(helper, otherChannel, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.RED, DyeColor.PURPLE, DyeColor.RED);
		// Only two dyes: no channel at all.
		placePanel(helper, noChannel, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.PURPLE, DyeColor.RED);
		helper.setBlock(transmitter.south(), Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(sameChannel, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(otherChannel, WirelessPanelBlock.POWER, 0);
			helper.assertBlockProperty(noChannel, WirelessPanelBlock.POWER, 0);
			helper.succeed();
		});
	}

	@GameTest
	public void wallPanelsWorkLikeLevers(GameTestHelper helper) {
		floor(helper);
		// Transmitter on the north face of a stone block; a lever on the stone's west face powers it.
		BlockPos inStone = new BlockPos(2, 1, 3);
		helper.setBlock(inStone, Blocks.STONE);
		BlockPos transmitter = inStone.north();
		BlockPos lever = inStone.west();
		placePanel(helper, transmitter, AttachFace.WALL, Direction.NORTH, PanelMode.TRANSMITTER, DyeColor.ORANGE, DyeColor.RED, DyeColor.RED);
		helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.WEST));

		// Receiver on the north face of another stone block; lamps beside the stone and beside the panel.
		BlockPos outStone = new BlockPos(6, 1, 3);
		helper.setBlock(outStone, Blocks.STONE);
		BlockPos receiver = outStone.north();
		placePanel(helper, receiver, AttachFace.WALL, Direction.NORTH, PanelMode.RECEIVER, DyeColor.ORANGE, DyeColor.RED, DyeColor.RED);
		BlockPos lampBehindStone = outStone.south();
		BlockPos lampBesidePanel = receiver.east();
		helper.setBlock(lampBehindStone, Blocks.REDSTONE_LAMP);
		helper.setBlock(lampBesidePanel, Blocks.REDSTONE_LAMP);

		helper.runAfterDelay(5, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			BlockPos leverPos = helper.absolutePos(lever);
			BlockState leverState = helper.getLevel().getBlockState(leverPos);
			((LeverBlock) Blocks.LEVER).pull(leverState, helper.getLevel(), leverPos, null);
		});
		helper.runAfterDelay(10, () -> {
			helper.assertBlockProperty(transmitter, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(lampBehindStone, RedstoneLampBlock.LIT, true);
			helper.assertBlockProperty(lampBesidePanel, RedstoneLampBlock.LIT, true);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void remotePulseEndsAfterOneSecond(GameTestHelper helper) {
		floor(helper);
		BlockPos receiver = new BlockPos(3, 1, 3);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.CYAN, DyeColor.RED, DyeColor.RED);
		int channel = Channel.of(DyeColor.CYAN, DyeColor.RED, DyeColor.RED);

		helper.runAfterDelay(5, () -> ChannelNetwork.get(helper.getLevel())
			.pulse(helper.getLevel(), channel, helper.absolutePos(receiver), 15, 20));
		helper.runAfterDelay(10, () -> helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15));
		helper.runAfterDelay(30, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void feedbackLoopLatchesInsteadOfCrashing(GameTestHelper helper) {
		floor(helper);
		// A receiver whose output feeds a transmitter on its own channel: once triggered, it stays on.
		BlockPos transmitter = new BlockPos(3, 1, 2);
		BlockPos receiver = transmitter.south();
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.LIME, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.LIME, DyeColor.RED, DyeColor.RED);
		int channel = Channel.of(DyeColor.LIME, DyeColor.RED, DyeColor.RED);

		helper.runAfterDelay(5, () -> ChannelNetwork.get(helper.getLevel())
			.pulse(helper.getLevel(), channel, helper.absolutePos(receiver), 15, 4));
		helper.runAfterDelay(60, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(transmitter, WirelessPanelBlock.POWER, 15);
			// Break the loop: removing the transmitter clears the channel.
			helper.setBlock(transmitter, Blocks.AIR);
		});
		helper.runAfterDelay(70, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			helper.succeed();
		});
	}

	@GameTest
	public void transmitterRemovedWithoutSideEffectsIsForgotten(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(1, 1, 3);
		BlockPos receiver = new BlockPos(5, 1, 3);
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.MAGENTA, DyeColor.RED, DyeColor.RED);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.MAGENTA, DyeColor.RED, DyeColor.RED);
		helper.setBlock(transmitter.south(), Blocks.REDSTONE_BLOCK);

		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			// Like /fill or a world editor: the block vanishes without its removal side effects.
			helper.getLevel().setBlock(helper.absolutePos(transmitter), Blocks.AIR.defaultBlockState(),
				Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			helper.getLevel().scheduleTick(helper.absolutePos(receiver), WirelessRedstone.PANEL, 1);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			helper.succeed();
		});
	}

	@GameTest
	public void dyesPopOutInReverseOrder(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		placePanel(helper, pos, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.WHITE, DyeColor.BLACK, DyeColor.BROWN);
		WirelessPanelBlockEntity panel = helper.getBlockEntity(pos, WirelessPanelBlockEntity.class);
		helper.assertValueEqual(panel.channel(), Channel.of(DyeColor.WHITE, DyeColor.BLACK, DyeColor.BROWN), "channel");
		helper.assertFalse(panel.addDye(DyeColor.RED), "a fourth dye should not fit");
		helper.assertValueEqual(panel.popDye(), DyeColor.BROWN, "first popped dye");
		helper.assertValueEqual(panel.channel(), Channel.NONE, "channel with two dyes");
		helper.assertValueEqual(panel.popDye(), DyeColor.BLACK, "second popped dye");
		helper.assertValueEqual(panel.popDye(), DyeColor.WHITE, "third popped dye");
		helper.assertTrue(panel.popDye() == null, "popping an empty panel should return nothing");
		helper.succeed();
	}

	@GameTest
	public void wallTransmitterOnRedstoneBlockLightsLampUnderWallReceiver(GameTestHelper helper) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 3);
		helper.setBlock(source, Blocks.REDSTONE_BLOCK);
		BlockPos transmitter = source.north();
		placePanel(helper, transmitter, AttachFace.WALL, Direction.NORTH, PanelMode.TRANSMITTER, DyeColor.LIME, DyeColor.LIME, DyeColor.RED);

		BlockPos lamp = new BlockPos(6, 1, 3);
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		BlockPos receiver = lamp.north();
		placePanel(helper, receiver, AttachFace.WALL, Direction.NORTH, PanelMode.RECEIVER, DyeColor.LIME, DyeColor.LIME, DyeColor.RED);

		helper.runAfterDelay(10, () -> {
			helper.assertBlockProperty(transmitter, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
			helper.succeed();
		});
	}

	@GameTest
	public void playerBuiltPairDyeThenToggle(GameTestHelper helper) {
		playerBuiltPair(helper, false, DyeColor.CYAN, DyeColor.CYAN, DyeColor.RED);
	}

	@GameTest
	public void playerBuiltPairToggleThenDye(GameTestHelper helper) {
		playerBuiltPair(helper, true, DyeColor.CYAN, DyeColor.CYAN, DyeColor.BLUE);
	}

	@GameTest
	public void floorDustOnlyJoinsTheWorkingSide(GameTestHelper helper) {
		floor(helper);
		BlockPos transmitter = new BlockPos(2, 1, 3);
		BlockPos receiver = new BlockPos(5, 1, 3);
		placePanel(helper, transmitter, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.PINK, DyeColor.PINK, DyeColor.PINK);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.PINK, DyeColor.PINK, DyeColor.PINK);
		for (BlockPos panel : new BlockPos[] {transmitter, receiver}) {
			wire(helper, panel.north());
			wire(helper, panel.south());
		}
		// The dust north of a panel faces it with its SOUTH side, and vice versa.
		assertWireSide(helper, transmitter.north(), Direction.SOUTH, false); // transmitter back: ignored
		assertWireSide(helper, transmitter.south(), Direction.NORTH, true); // transmitter front (arrow): input
		assertWireSide(helper, receiver.north(), Direction.SOUTH, true); // receiver front: output
		assertWireSide(helper, receiver.south(), Direction.NORTH, false); // receiver back: nothing
		helper.succeed();
	}

	@GameTest
	public void wallTransmitterReadsDustRunningIntoIt(GameTestHelper helper) {
		floor(helper);
		BlockPos support = new BlockPos(2, 1, 4);
		helper.setBlock(support, Blocks.STONE);
		BlockPos transmitter = support.north();
		placePanel(helper, transmitter, AttachFace.WALL, Direction.NORTH, PanelMode.TRANSMITTER, DyeColor.GRAY, DyeColor.GRAY, DyeColor.GRAY);
		BlockPos receiver = new BlockPos(6, 1, 3);
		placePanel(helper, receiver, AttachFace.FLOOR, Direction.NORTH, PanelMode.RECEIVER, DyeColor.GRAY, DyeColor.GRAY, DyeColor.GRAY);
		helper.runAfterDelay(3, () -> {
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 0);
			helper.setBlock(transmitter.east().east(), Blocks.REDSTONE_BLOCK);
			wire(helper, transmitter.east());
			assertWireSide(helper, transmitter.east(), Direction.WEST, true);
		});
		helper.runAfterDelay(8, () -> {
			helper.assertBlockProperty(transmitter, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.succeed();
		});
	}

	@GameTest
	public void remoteCopiesChannelOnPlainRightClick(GameTestHelper helper) {
		floor(helper);
		BlockPos panel = new BlockPos(2, 1, 2);
		placePanel(helper, panel, AttachFace.FLOOR, Direction.SOUTH, PanelMode.TRANSMITTER, DyeColor.PURPLE, DyeColor.PURPLE, DyeColor.PURPLE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack remote = new ItemStack(WirelessRedstone.REMOTE);
		player.setItemInHand(InteractionHand.MAIN_HAND, remote);
		helper.useBlock(panel, player);
		helper.assertValueEqual(RemoteItem.channel(remote), Channel.of(DyeColor.PURPLE, DyeColor.PURPLE, DyeColor.PURPLE), "remote channel");
		helper.assertBlockProperty(panel, WirelessPanelBlock.MODE, PanelMode.TRANSMITTER);
		helper.succeed();
	}

	/** Places dust and lets it settle its connections, as if a player had placed it. */
	private static void wire(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.REDSTONE_WIRE);
		BlockPos absolute = helper.absolutePos(pos);
		BlockState settled = Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(absolute), helper.getLevel(), absolute);
		helper.getLevel().setBlock(absolute, settled, Block.UPDATE_ALL);
	}

	private static void assertWireSide(GameTestHelper helper, BlockPos wire, Direction side, boolean connected) {
		BlockState state = helper.getBlockState(wire);
		helper.assertValueEqual(state.getValue(RedstoneWireBlock.PROPERTY_BY_DIRECTION.get(side)).isConnected(), connected,
			"dust at " + wire.toShortString() + " connected on its " + side + " side");
	}

	/** Builds a wall transmitter on a redstone block and a wall receiver on a lamp the way a player would. */
	private static void playerBuiltPair(GameTestHelper helper, boolean toggleFirst, DyeColor... dyes) {
		floor(helper);
		BlockPos source = new BlockPos(2, 1, 3);
		BlockPos lamp = new BlockPos(6, 1, 3);
		helper.setBlock(source, Blocks.REDSTONE_BLOCK);
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		BlockPos transmitter = source.north();
		BlockPos receiver = lamp.north();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(1, () -> {
			place(helper, transmitter);
			place(helper, receiver);
		});
		helper.runAfterDelay(4, () -> dye(helper, player, transmitter, dyes));
		if (toggleFirst) {
			helper.runAfterDelay(7, () -> toggle(helper, player, receiver));
			helper.runAfterDelay(10, () -> dye(helper, player, receiver, dyes));
		} else {
			helper.runAfterDelay(7, () -> dye(helper, player, receiver, dyes));
			helper.runAfterDelay(10, () -> toggle(helper, player, receiver));
		}
		helper.runAfterDelay(20, () -> {
			helper.assertBlockProperty(transmitter, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.MODE, PanelMode.RECEIVER);
			helper.assertBlockProperty(receiver, WirelessPanelBlock.POWER, 15);
			helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
			helper.succeed();
		});
	}

	private static void place(GameTestHelper helper, BlockPos pos) {
		BlockState state = WirelessRedstone.PANEL.defaultBlockState()
			.setValue(WirelessPanelBlock.FACE, AttachFace.WALL)
			.setValue(WirelessPanelBlock.FACING, Direction.NORTH);
		helper.setBlock(pos, state);
		BlockPos absolute = helper.absolutePos(pos);
		state.getBlock().setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), null, ItemStack.EMPTY);
	}

	private static void dye(GameTestHelper helper, Player player, BlockPos pos, DyeColor... dyes) {
		for (DyeColor dye : dyes) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(dye)));
			helper.useBlock(pos, player);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static void toggle(GameTestHelper helper, Player player, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(pos, player);
	}

	// ---------------------------------------------------------------- helpers

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void placePanel(GameTestHelper helper, BlockPos pos, AttachFace face, Direction facing, PanelMode mode, DyeColor... dyes) {
		helper.setBlock(pos, WirelessRedstone.PANEL.defaultBlockState()
			.setValue(WirelessPanelBlock.FACE, face)
			.setValue(WirelessPanelBlock.FACING, facing)
			.setValue(WirelessPanelBlock.MODE, mode));
		WirelessPanelBlockEntity panel = helper.getBlockEntity(pos, WirelessPanelBlockEntity.class);
		for (DyeColor dye : dyes) {
			panel.addDye(dye);
		}
		helper.getLevel().scheduleTick(helper.absolutePos(pos), WirelessRedstone.PANEL, WirelessPanelBlock.STEP_DELAY);
	}
}
