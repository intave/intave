package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class Constraint extends PlayerCheckPart<PlacementAnalysis> {
	private int backwardsStreak;
	private int lastBlockClicks;
	private int blockClicks;
	private int tickCount;

	public Constraint(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			FLYING, LOOK, POSITION, POSITION_LOOK
		}
	)
	public void receiveMovementPacket(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		MovementMetadata movement = user.meta().movement();

		if (movement.ticksPast(TELEPORT) == 0) {
			return;
		}

		int forward = movement.keyForward;
		int strafe = movement.keyStrafe;
//    player.sendMessage(resolveKeysFromInput(forward, strafe) + " ("+backwardsStreak+") " + movement.rotationYaw + " " + movement.rotationPitch);

		if (forward == -1 && strafe == 0) {
			backwardsStreak++;
		} else {
			if (forward == 0 && strafe == 0) {
				backwardsStreak -= 4;
				backwardsStreak = Math.max(0, backwardsStreak);
			} else {
				backwardsStreak = 0;
			}
		}

		tickCount++;

		if (tickCount > 20) {
			tickCount = 0;
			lastBlockClicks = blockClicks;
			blockClicks = 0;
		}

		boolean bad = lastBlockClicks < 10 && blockClicks < 10 && backwardsStreak > 30;
		if (bad) {

		}
//    player.sendMessage((bad ? ChatColor.RED : ChatColor.GRAY) + "bs" + backwardsStreak + " lbc" + lastBlockClicks + " bc" + blockClicks);
	}

	@PacketSubscription(
		packetsIn = {USE_ITEM, BLOCK_PLACE},
		priority = ListenerPriority.LOW
	)
	public void rightClick(
		User user, PacketContainer packet, BlockInteractionReader reader
	) {
		Player player = user.player();
		String name = packet.getType().name();

		Direction direction = reader.direction();
		String k = MathHelper.formatMotion(reader.facingVector());
		if (reader.direction() == null) {
			blockClicks++;
		}
//    Synchronizer.synchronize(() -> {
//      player.sendMessage(name + " " + direction + " " + k);
//    });

//    if ()
	}

	private static String resolveKeysFromInput(int forward, int strafe) {
		String key = "";
		if (forward == 1) {
			key += "W";
		} else if (forward == -1) {
			key += "S";
		}
		if (strafe == 1) {
			key += "A";
		} else if (strafe == -1) {
			key += "D";
		}
		return key;
	}

}
