package systems.acolyte.games.wirelessredstone.channel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import systems.acolyte.games.wirelessredstone.WirelessRedstone;
import systems.acolyte.games.wirelessredstone.block.PanelMode;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlock;
import systems.acolyte.games.wirelessredstone.block.WirelessPanelBlockEntity;
import systems.acolyte.games.wirelessredstone.config.WirelessConfig;

/**
 * Per-dimension channel state.
 *
 * <ul>
 *   <li><b>sources</b> (saved): every transmitter currently sending a signal above 0, by channel.
 *       Saved with the world, so a transmitter in an unloaded chunk keeps holding its value.</li>
 *   <li><b>listeners</b> (runtime): every loaded panel, by channel, so it can be told to refresh.</li>
 *   <li><b>pulses</b> (runtime): short-lived signals from remotes.</li>
 * </ul>
 *
 * Changes never update blocks directly: listeners get a scheduled tick, which keeps the
 * delay fixed and means feedback loops settle (a direct loop latches) instead of recursing.
 */
public final class ChannelNetwork extends SavedData {
	private static final Codec<Source> SOURCE_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("channel").forGetter(Source::channel),
		Codec.LONG.fieldOf("pos").forGetter(Source::pos),
		Codec.INT.fieldOf("power").forGetter(Source::power)
	).apply(i, Source::new));

	private static final Codec<ChannelNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
		SOURCE_CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(ChannelNetwork::sourceList)
	).apply(i, ChannelNetwork::new));

	public static final SavedDataType<ChannelNetwork> TYPE =
		new SavedDataType<>(WirelessRedstone.id("channels"), ChannelNetwork::new, CODEC, null);

	private final Int2ObjectMap<Long2IntMap> sources = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectMap<LongSet> listeners = new Int2ObjectOpenHashMap<>();
	private final List<Pulse> pulses = new ArrayList<>();
	private final LongSet pendingLoads = new LongOpenHashSet();

	public ChannelNetwork() {
	}

	private ChannelNetwork(List<Source> saved) {
		for (Source source : saved) {
			if (source.power > 0) {
				sources.computeIfAbsent(source.channel, c -> new Long2IntOpenHashMap()).put(source.pos, source.power);
			}
		}
	}

	public static ChannelNetwork get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Strongest signal on {@code channel} that reaches {@code at} (respecting maxRange). */
	public int strength(ServerLevel level, int channel, BlockPos at) {
		if (channel == Channel.NONE) {
			return 0;
		}
		int range = WirelessConfig.maxRange();
		long rangeSq = (long) range * range;
		int best = 0;

		Long2IntMap channelSources = sources.get(channel);
		if (channelSources != null) {
			LongArrayList stale = null;
			for (Long2IntMap.Entry entry : channelSources.long2IntEntrySet()) {
				BlockPos sourcePos = BlockPos.of(entry.getLongKey());
				if (isStale(level, channel, sourcePos)) {
					if (stale == null) {
						stale = new LongArrayList();
					}
					stale.add(entry.getLongKey());
				} else if (entry.getIntValue() > best && inRange(at, sourcePos, range, rangeSq)) {
					best = entry.getIntValue();
				}
			}
			if (stale != null) {
				stale.forEach(channelSources::remove);
				if (channelSources.isEmpty()) {
					sources.remove(channel);
				}
				setDirty();
				refreshChannel(level, channel);
			}
		}
		for (Pulse pulse : pulses) {
			if (pulse.channel == channel && inRange(at, pulse.pos, range, rangeSq)) {
				best = Math.max(best, pulse.power);
			}
		}
		return best;
	}

	/**
	 * A saved source whose chunk is loaded but which is no longer a transmitter on that channel
	 * (e.g. removed by /fill or a world editor, which skip normal block removal).
	 * Sources in unloaded chunks are trusted: that is how they keep their signal.
	 */
	private static boolean isStale(ServerLevel level, int channel, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !(state.getBlock() instanceof WirelessPanelBlock)
			|| state.getValue(WirelessPanelBlock.MODE) != PanelMode.TRANSMITTER
			|| !(level.getBlockEntity(pos) instanceof WirelessPanelBlockEntity panel)
			|| panel.channel() != channel;
	}

	/** Debug view: saved sources on a channel, as "x,y,z=power". */
	public String describeSources(int channel) {
		Long2IntMap channelSources = sources.get(channel);
		if (channelSources == null) {
			return "none";
		}
		StringBuilder text = new StringBuilder();
		for (Long2IntMap.Entry entry : channelSources.long2IntEntrySet()) {
			text.append(BlockPos.of(entry.getLongKey()).toShortString()).append('=').append(entry.getIntValue()).append(' ');
		}
		return text.toString().trim();
	}

	private static boolean inRange(BlockPos a, BlockPos b, int range, long rangeSq) {
		return range <= 0 || a.distSqr(b) <= rangeSq;
	}

	/** Records what a transmitter is sending (0 removes it) and refreshes the channel if it changed. */
	public void setSource(ServerLevel level, int channel, BlockPos pos, int power) {
		if (channel == Channel.NONE) {
			return;
		}
		long key = pos.asLong();
		Long2IntMap channelSources = sources.get(channel);
		int old = channelSources == null ? 0 : channelSources.get(key);
		if (old == power) {
			return;
		}
		if (power > 0) {
			if (channelSources == null) {
				channelSources = new Long2IntOpenHashMap();
				sources.put(channel, channelSources);
			}
			channelSources.put(key, power);
		} else {
			channelSources.remove(key);
			if (channelSources.isEmpty()) {
				sources.remove(channel);
			}
		}
		setDirty();
		refreshChannel(level, channel);
	}

	/** A remote's signal: {@code power} on {@code channel} from {@code pos} for {@code ticks} game ticks. */
	public void pulse(ServerLevel level, int channel, BlockPos pos, int power, int ticks) {
		pulses.add(new Pulse(channel, pos.immutable(), power, level.getGameTime() + ticks));
		refreshChannel(level, channel);
	}

	public void addListener(int channel, BlockPos pos) {
		if (channel != Channel.NONE) {
			listeners.computeIfAbsent(channel, c -> new LongOpenHashSet()).add(pos.asLong());
		}
	}

	public void removeListener(int channel, BlockPos pos) {
		LongSet set = listeners.get(channel);
		if (set != null) {
			set.remove(pos.asLong());
			if (set.isEmpty()) {
				listeners.remove(channel);
			}
		}
	}

	/** Called when a panel's chunk loads; handled at the end of the tick, when it is safe to schedule ticks. */
	public void queueLoad(BlockPos pos) {
		pendingLoads.add(pos.asLong());
	}

	/** Schedules a refresh tick on every loaded panel on {@code channel}. */
	public void refreshChannel(ServerLevel level, int channel) {
		LongSet set = listeners.get(channel);
		if (set == null) {
			return;
		}
		for (long key : set.toLongArray()) {
			scheduleRefresh(level, BlockPos.of(key));
		}
	}

	private static void scheduleRefresh(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof WirelessPanelBlock block && !level.getBlockTicks().hasScheduledTick(pos, block)) {
			level.scheduleTick(pos, block, WirelessPanelBlock.STEP_DELAY);
		}
	}

	/** End of every level tick: expire remote pulses and catch up panels that just loaded. */
	public void tick(ServerLevel level) {
		long now = level.getGameTime();
		IntLinkedOpenHashSet expired = new IntLinkedOpenHashSet();
		for (Iterator<Pulse> it = pulses.iterator(); it.hasNext(); ) {
			Pulse pulse = it.next();
			if (pulse.expiresAt <= now) {
				it.remove();
				expired.add(pulse.channel);
			}
		}
		expired.forEach(channel -> refreshChannel(level, channel));

		if (!pendingLoads.isEmpty()) {
			for (long key : pendingLoads.toLongArray()) {
				scheduleRefresh(level, BlockPos.of(key));
			}
			pendingLoads.clear();
		}
	}

	private List<Source> sourceList() {
		List<Source> list = new ArrayList<>();
		for (Int2ObjectMap.Entry<Long2IntMap> channel : sources.int2ObjectEntrySet()) {
			for (Long2IntMap.Entry entry : channel.getValue().long2IntEntrySet()) {
				list.add(new Source(channel.getIntKey(), entry.getLongKey(), entry.getIntValue()));
			}
		}
		return list;
	}

	private record Source(int channel, long pos, int power) {
	}

	private record Pulse(int channel, BlockPos pos, int power, long expiresAt) {
	}
}
