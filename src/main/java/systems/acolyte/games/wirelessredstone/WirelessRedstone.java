package systems.acolyte.games.wirelessredstone;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlock;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlockEntity;
import systems.acolyte.games.wirelessredstone.channel.ChannelNetwork;
import systems.acolyte.games.wirelessredstone.config.WirelessConfig;
import systems.acolyte.games.wirelessredstone.item.RemoteItem;

/**
 * Common entrypoint (runs on clients and servers): registers the Wireless Panel block, its
 * item and block entity, the Wireless Remote and the remote's channel component, and hooks the
 * server events that keep each dimension's {@link ChannelNetwork} up to date.
 */
public class WirelessRedstone implements ModInitializer {
	public static final String MOD_ID = "wireless_redstone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The channel stored on a remote. */
	public static final DataComponentType<Integer> CHANNEL = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		id("channel"),
		DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 4095)).networkSynchronized(ByteBufCodecs.VAR_INT).build()
	);

	/** The Wireless Panel: a transmitter or receiver mounted on a floor, wall or ceiling. */
	public static final Block PANEL = registerBlock("wireless_panel", WirelessPanelBlock::new,
		BlockBehaviour.Properties.of()
			.noCollision()
			.instabreak()
			.sound(SoundType.METAL)
			.pushReaction(PushReaction.POPPED));

	public static final Item PANEL_ITEM = registerItem("wireless_panel",
		properties -> new BlockItem(PANEL, properties), new Item.Properties().useBlockDescriptionPrefix());

	/** The Wireless Remote: a handheld momentary transmitter. */
	public static final Item REMOTE = registerItem("remote", RemoteItem::new, new Item.Properties().stacksTo(1));

	public static final BlockEntityType<WirelessPanelBlockEntity> PANEL_BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		id("wireless_panel"),
		FabricBlockEntityTypeBuilder.create(WirelessPanelBlockEntity::new, PANEL).build()
	);

	@Override
	public void onInitialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.accept(PANEL_ITEM);
			output.accept(REMOTE);
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> WirelessConfig.load());

		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof WirelessPanelBlockEntity panel) {
				panel.onLoad(level);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof WirelessPanelBlockEntity panel) {
				panel.onUnload(level);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> ChannelNetwork.get(level).tick(level));
	}

	/** An identifier in this mod's namespace, e.g. {@code wireless_redstone:remote}. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerItem(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
