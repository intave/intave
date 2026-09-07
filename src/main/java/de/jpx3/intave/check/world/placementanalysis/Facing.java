package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.view.PacketEventsBlockInteractionView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Facing extends CheckPart<PlacementAnalysis> {
	public Facing(PlacementAnalysis parentCheck) {
		super(parentCheck);
	}

	@PacketSubscription(
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void checkPlacementVector(PacketEvent event) {
		Player player = event.getPlayer();
		PacketContainer packet = event.getPacket();
		if (blockingPlacementPacket(packet)) {
			return;
		}
		StructureModifier<Float> floatStructureModifier = packet.getFloat();
		if (floatStructureModifier.size() < 3) {
			return;
		}
		checkCursorPosition(
			player,
			floatStructureModifier.read(0),
			floatStructureModifier.read(1),
			floatStructureModifier.read(2)
		);
	}

	/**
	 * PacketEvents entry point. The cursor position is read through
	 * {@link PacketEventsBlockInteractionView}, whose face index reports the same 255 "empty
	 * interaction" marker the ProtocolLib path filters on, and whose facing vector is null exactly
	 * when the packet carries no cursor position - the two conditions the ProtocolLib body checks
	 * before it evaluates the vector.
	 */
	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void checkPlacementVector(PacketReceiveEvent event) {
		PacketEventsBlockInteractionView view = PacketEventsBlockInteractionView.of(event);
		if (view == null || view.enumDirection() == 255) {
			return;
		}
		Vector cursor = view.facingVector();
		Player player = view.player();
		if (cursor == null || player == null) {
			return;
		}
		checkCursorPosition(player, (float) cursor.getX(), (float) cursor.getY(), (float) cursor.getZ());
	}

	/** Engine independent body: the in-block cursor position has to stay inside the unit cube. */
	private void checkCursorPosition(Player player, float f1, float f2, float f3) {
		if (f1 < 0 || f2 < 0 || f3 < 0 || f1 > 1 || f2 > 1 || f3 > 1) {
			Violation violation = Violation.builderFor(PlacementAnalysis.class)
				.forPlayer(player).withMessage(COMMON_FLAG_MESSAGE)
				.withDefaultThreshold()
				.withVL(5).build();
			Modules.violationProcessor().processViolation(violation);
			//dmc14
//      user.nerf(AttackNerfStrategy.CANCEL_FIRST_HIT, "14");
//      user.nerf(AttackNerfStrategy.DMG_LIGHT, "14");
		}
	}

	private boolean blockingPlacementPacket(PacketContainer packet) {
		Integer integer = packet.getIntegers().readSafely(0);
		return integer != null && integer == 255;
	}

	@BukkitEventSubscription(
		ignoreCancelled = true
	)
	public void onPlace(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);
		MetadataBundle meta = user.meta();
		ViolationMetadata violationMetadata = meta.violationLevel();
		if (place.isCancelled()) {
			violationMetadata.facingFailedCounter = -10;
		}
		int facingFailedCounter = violationMetadata.facingFailedCounter;
		if (facingFailedCounter > 3) {
			Violation violation = Violation.builderFor(PlacementAnalysis.class)
				.forPlayer(player)
				.withMessage(COMMON_FLAG_MESSAGE)
				.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
				.withDetails("repeated placement faults")
				.withVL(0)
				.build();
//      ViolationContext context = Modules.violationProcessor().processViolation(violation);
//      if (context.shouldCounterThreat()) {
//        place.setCancelled(true);
//      }
		}
	}
}
