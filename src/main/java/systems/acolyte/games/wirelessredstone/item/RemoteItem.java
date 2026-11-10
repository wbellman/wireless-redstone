package systems.acolyte.games.wirelessredstone.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlockEntity;
import systems.acolyte.games.wirelessredstone.channel.Channel;
import systems.acolyte.games.wirelessredstone.channel.ChannelNetwork;

/**
 * A handheld momentary transmitter. Right-click a panel to copy its channel;
 * right-click anywhere else to send a strength-15 pulse for one second.
 */
public class RemoteItem extends Item {
	public static final int PULSE_POWER = 15;
	public static final int PULSE_TICKS = 20;

	public RemoteItem(Properties properties) {
		super(properties);
	}

	public static int channel(ItemStack stack) {
		return stack.getOrDefault(WirelessRedstone.CHANNEL, Channel.NONE);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof WirelessPanelBlockEntity panel)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			int channel = panel.channel();
			if (channel == Channel.NONE) {
				player.sendOverlayMessage(Component.translatable("wireless_redstone.remote.panel_has_no_channel"));
			} else {
				context.getItemInHand().set(WirelessRedstone.CHANNEL, channel);
				player.sendOverlayMessage(Component.translatable("wireless_redstone.remote.copied", Channel.describe(channel)));
				level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int channel = channel(stack);
		if (channel == Channel.NONE) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("wireless_redstone.remote.no_channel"));
			}
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			ChannelNetwork.get(serverLevel).pulse(serverLevel, channel, player.blockPosition(), PULSE_POWER, PULSE_TICKS);
			level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		player.getCooldowns().addCooldown(stack, PULSE_TICKS);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(
		ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
	) {
		int channel = channel(stack);
		builder.accept(channel == Channel.NONE
			? Component.translatable("wireless_redstone.remote.how_to_set")
			: Component.translatable("wireless_redstone.remote.channel", Channel.describe(channel)));
	}
}
