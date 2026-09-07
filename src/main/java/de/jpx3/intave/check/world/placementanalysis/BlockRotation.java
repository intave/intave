package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.view.BlockInteractionView;
import de.jpx3.intave.packet.view.PacketEventsBlockInteractionView;
import de.jpx3.intave.packet.view.ProtocolLibBlockInteractionView;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class BlockRotation extends PlayerCheckPart<PlacementAnalysis> {
	private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
	private long lastPlacement;
	private double vl;

	public BlockRotation(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		priority = ListenerPriority.LOW,
		packetsIn = {
			BLOCK_PLACE, USE_ITEM
		}
	)
	public void receivePlacementPacket(PacketEvent event) {
		handlePlacement(new ProtocolLibBlockInteractionView(event));
	}

	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		priority = ListenerPriority.LOW,
		packetsIn = {
			BLOCK_PLACE, USE_ITEM
		}
	)
	public void receivePlacementPacket(PacketReceiveEvent event) {
		PacketEventsBlockInteractionView view = PacketEventsBlockInteractionView.of(event);
		if (view != null && view.player() != null) {
			handlePlacement(view);
		}
	}

	/** Engine independent placement handling; see {@link BlockInteractionView}. */
	private void handlePlacement(BlockInteractionView view) {
		Player player = view.player();
		User user = userOf(player);
		MovementMetadata movement = user.meta().movement();
		AbilityMetadata abilities = user.meta().abilities();

		BlockPosition blockPosition = view.blockPosition();

		if (blockPosition == null || view.cancelled() || movement.isInVehicle()) {
			view.release();
			return;
		}

		int enumDirection = view.enumDirection();
		if (enumDirection == 255) {
			view.release();
			return;
		}

		Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
		boolean clickableInteraction = BlockInteractionAccess.isClickable(clickedType);
		Material heldItemType = user.meta().inventory().heldItemType();
		boolean interactionIsPlacement = heldItemType != Material.AIR && heldItemType.isBlock() && !clickableInteraction && !abilities.inGameMode(GameMode.ADVENTURE);

		if (!interactionIsPlacement || enumDirection < 2) {
			view.release();
			return;
		}

		placementSpeedHistory.add(Math.min(1000, System.currentTimeMillis() - lastPlacement));
		lastPlacement = System.currentTimeMillis();
		double average = 500;

		if (placementSpeedHistory.size() >= 8) {
			average = placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
			placementSpeedHistory.remove(0);
		}

		if (movement.rotationPitch > 85 && average < 400) {
			if (vl++ > 3) {
				int pitch = (int) movement.rotationPitch;
				int ticksPerBlock = (int) (average / 50d);
				String details = "pitch of " + pitch + " placing blocks in " + ticksPerBlock + " t/b";
				Violation violation = Violation.builderFor(PlacementAnalysis.class)
					.forPlayer(player).withMessage(COMMON_FLAG_MESSAGE).withDetails(details)
					.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
					.withDefaultThreshold()
					.withVL(10).build();
				Modules.violationProcessor().processViolation(violation);
			}
		} else if (vl > 0) {
			vl *= 0.98;
			vl -= 0.002;
		}
		view.release();
	}
}
