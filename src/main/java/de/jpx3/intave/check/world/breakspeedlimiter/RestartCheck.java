package de.jpx3.intave.check.world.breakspeedlimiter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.pe.PacketEventWrapper;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.packet.view.ProtocolLibBlockPositionView;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class RestartCheck extends MetaCheckPart<BreakSpeedLimiter, RestartCheck.BreakSpeedStartMeta> {
	private static final double CLOSE_ENOUGH_RESTART_DELAY_TICKS = 5.5D;
	private static final double EXPECTED_RESTART_DELAY_TICKS = 6.0D;
	private static final double MAX_STORED_RESTART_ADVANTAGE_TICKS = 20.0D;

	/**
	 * PacketEvents counterpart of the {@link PacketTypes#isClientEndTick} constant.
	 * <p>
	 * Resolved through {@link PacketEventsIdMapper} rather than by referencing the constant, because
	 * CLIENT_TICK_END does not exist on older PacketEvents releases; an unresolved packet yields an
	 * empty list here, which simply never matches.
	 */
	private static final List<PacketTypeCommon> CLIENT_TICK_END_TYPES =
		PacketEventsIdMapper.typesOf(CLIENT_TICK_END);

	public RestartCheck(BreakSpeedLimiter parentCheck) {
		super(parentCheck, RestartCheck.BreakSpeedStartMeta.class);
	}

	@PacketSubscription(priority = ListenerPriority.LOWEST, packetsIn = {
		POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE, CLIENT_TICK_END
	})
	public void tickUpdate(PacketEvent event) {
		handleTickUpdate(event.getPlayer(), PacketTypes.isClientEndTick(event.getPacketType()));
	}

	/**
	 * PacketEvents entry point for {@link #tickUpdate(PacketEvent)}.
	 * <p>
	 * This subscription reads no packet payload at all - only who sent the packet and whether it was
	 * the client tick end marker - so it needs no packet view; the wrapper supplies both directly.
	 */
	@PacketSubscription(engine = Engine.PACKETEVENTS, priority = ListenerPriority.LOWEST, packetsIn = {
		POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE, CLIENT_TICK_END
	})
	public void tickUpdate(PacketEventWrapper event) {
		Player player = event.player();
		if (player == null) {
			return;
		}
		handleTickUpdate(player, isClientEndTick(event.packetType()));
	}

	/** Engine independent client tick counter; the packet itself is only a tick marker. */
	private void handleTickUpdate(Player player, boolean clientTickEnd) {
		User user = userOf(player);
		ProtocolMetadata clientData = user.meta().protocol();
		if (clientData.sendsClientTickEnd()) {
			if (!clientTickEnd) {
				return;
			}
		} else if (!clientData.emptyFlyingPacketsAreExplicitlySent()) {
			return;
		}
		BreakSpeedStartMeta meta = metaOf(user);
		meta.ticks++;
	}

	private static boolean isClientEndTick(PacketTypeCommon packetType) {
		return packetType != null && CLIENT_TICK_END_TYPES.contains(packetType);
	}

	@PacketSubscription(priority = ListenerPriority.LOWEST, packetsIn = {
		BLOCK_DIG
	})
	public void receiveBlockAction(PacketEvent event) {
		ProtocolLibBlockPositionView view = new ProtocolLibBlockPositionView(event);
		try {
			handleBlockAction(view);
		} finally {
			view.release();
		}
	}

	/**
	 * PacketEvents entry point for {@link #receiveBlockAction(PacketEvent)}. The dig action and the
	 * targeted block are the only fields the body reads, and both are served by
	 * {@link PacketEventsBlockPositionView}.
	 */
	@PacketSubscription(engine = Engine.PACKETEVENTS, priority = ListenerPriority.LOWEST, packetsIn = {
		BLOCK_DIG
	})
	public void receiveBlockAction(PacketReceiveEvent event) {
		PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
		if (view == null || view.player() == null) {
			return;
		}
		try {
			handleBlockAction(view);
		} finally {
			view.release();
		}
	}

	/** Engine independent block break restart accounting; see {@link BlockPositionView}. */
	private void handleBlockAction(BlockPositionView view) {
		Player player = view.player();
		User user = userOf(player);
		RestartCheck.BreakSpeedStartMeta meta = metaOf(user);
		ProtocolMetadata clientData = user.meta().protocol();

		BlockPositionView.DigAction digType = view.digAction();
		if (digType == null) {
			return;
		}

		switch (digType) {
			case START_DESTROY_BLOCK: {
				BlockPosition blockPosition = view.blockPosition();
				if (isRepeatedActiveStart(meta.breakProcess, meta.targetBlockPosition, blockPosition)) {
					return;
				}

				if (!clientData.emptyFlyingPacketsAreExplicitlySent() && !clientData.sendsClientTickEnd()) {
					meta.breakProcess = true;
					meta.targetBlockPosition = blockPosition;
					break;
				}

				int ticksBetween = meta.ticks - meta.blockBreakTick;
				double restartDelay = resolveRestartDelayTicks(ticksBetween);
				double balance = clampBalance(meta.blockBreakStartVL, user.latency() / 50D);
				if (restartDelay >= CLOSE_ENOUGH_RESTART_DELAY_TICKS) {
					balance *= 0.9D;
				} else {
					balance += EXPECTED_RESTART_DELAY_TICKS - restartDelay;
				}
				meta.blockBreakStartVL = balance;

				if (balance > MAX_STORED_RESTART_ADVANTAGE_TICKS
					&& meta.restartFlagBreakSequence != meta.blockBreakSequence) {
					String message = "started breaking too quickly";
					String details = ((int) restartDelay) + " ticks between";
					ViolationProcessor violationProcessor = Modules.violationProcessor();
					Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
						.forPlayer(player).withMessage(message).withDetails(details).withVL(5)
						.build();
					ViolationContext violationContext = violationProcessor.processViolation(violation);
					if (violationContext.shouldCounterThreat()) {
						view.setCancelled(true);
						meta.cancelNextStop = true;
					}
					meta.restartFlagBreakSequence = meta.blockBreakSequence;
				}
				meta.breakProcess = true;
				meta.targetBlockPosition = blockPosition;
				break;
			}
			case STOP_DESTROY_BLOCK: {
				meta.blockBreakTick = meta.ticks;
				meta.blockBreakSequence++;
				meta.breakProcess = false;
				meta.targetBlockPosition = null;
				if (meta.cancelNextStop) {
					meta.cancelNextStop = false;
					view.setCancelled(true);
					BlockPosition blockPosition = view.blockPosition();
					if (blockPosition != null) {
						refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
					}
				}
				break;
			}
			case ABORT_DESTROY_BLOCK:
				meta.breakProcess = false;
				meta.targetBlockPosition = null;
				meta.cancelNextStop = false;
				break;
		}
	}

	static boolean isRepeatedActiveStart(
		boolean breakProcess,
		BlockPosition targetBlockPosition,
		BlockPosition blockPosition) {
		return breakProcess && targetBlockPosition != null && targetBlockPosition.equals(blockPosition);
	}

	static double resolveRestartDelayTicks(int ticksBetween) {
		return Math.max(0, ticksBetween);
	}

	private static double clampBalance(double balance, double balanceLimit) {
		double limit = Math.max(MAX_STORED_RESTART_ADVANTAGE_TICKS, balanceLimit);
		return MathHelper.minmax(-limit, balance, limit);
	}

	private void refreshBlocksAround(Player player, Location targetLocation) {
		User user = userOf(player);
		Synchronizer.synchronize(user, () -> {
			player.updateInventory();
			refreshBlock(player, targetLocation);
		});
	}

	private void refreshBlock(Player player, Location location) {
		PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
		if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
			return;
		}
		Block block = VolatileBlockAccess.blockAccess(location);
		Object handle = BlockVariantNativeAccess.nativeVariantAccess(block);
		WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
		packet.getBlockData().write(0, blockData);

		com.comphenix.protocol.wrappers.BlockPosition position =
			new com.comphenix.protocol.wrappers.BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		packet.getBlockPositionModifier().write(0, position);
		PacketSender.sendServerPacket(player, packet);
	}

	public static final class BreakSpeedStartMeta extends CheckCustomMetadata {
		private BlockPosition targetBlockPosition;
		private int ticks;
		private int blockBreakTick;
		private long blockBreakSequence;
		private boolean breakProcess;
		private double blockBreakStartVL;
		private long restartFlagBreakSequence = Long.MIN_VALUE;
		private boolean cancelNextStop;
	}
}
