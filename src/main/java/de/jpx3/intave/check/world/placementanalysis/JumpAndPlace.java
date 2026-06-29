package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

// this does not work yet, like at all

public final class JumpAndPlace extends PlayerCheckPart<PlacementAnalysis> {
	private final List<Vector> lastBlocksPlaced = new CopyOnWriteArrayList<>();
	private boolean startSneakInThisTick;
	private boolean stopSneakInThisTick;
	private boolean sneakChangedInThisTick;
	private boolean placedInThisTick;
	private boolean isSneaking;
	private boolean suspicious;
	private long lastSneakStart;
	private long lastPlace;
	private final long lastSneakDuration = 10;
	private long sneakDuration;
	private double violationLevel;
	private long tickCount;

	public JumpAndPlace(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			FLYING, LOOK, POSITION, POSITION_LOOK
		}
	)
	public void clientTickUpdate(PacketEvent event) {
		Player player = event.getPlayer();
		if (placedInThisTick || sneakChangedInThisTick) {
//      player.sendMessage(sneakInThisTick + "("+startSneakInThisTick+","+stopSneakInThisTick+")/" + placedInThisTick);
			if (placedInThisTick) {
				// difference to last sneak start
				long diff = startSneakInThisTick ? 0 : tickCount - lastSneakStart;

				boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;
				if (!suspiciousSneaking && violationLevel > 0) {
					violationLevel -= 0.05;
				} else if (suspiciousSneaking) {
					violationLevel += 1;
				}
//        player.sendMessage((suspiciousSneaking ? ChatColor.YELLOW : ChatColor.GREEN) + "Sneak start -> Place: " + diff + " last duration: " + lastSneakDuration);
			}
			if (sneakChangedInThisTick) {
				// difference to last place
				long diff = placedInThisTick ? 0 : tickCount - lastPlace;
				boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;
				if (!suspiciousSneaking && violationLevel > 0) {
					violationLevel -= 0.05;
				} else if (suspiciousSneaking) {
					violationLevel += 1;
				}
//        player.sendMessage((suspiciousSneaking ? ChatColor.YELLOW : ChatColor.GREEN) +"Place -> Sneak start: " + diff + " last duration: " + lastSneakDuration);
			}
		}
		if (startSneakInThisTick) {
			lastSneakStart = tickCount;
		}
		if (placedInThisTick) {
			lastPlace = tickCount;
		}
		startSneakInThisTick = false;
		stopSneakInThisTick = false;
		sneakChangedInThisTick = false;
		placedInThisTick = false;
		tickCount++;
		sneakDuration++;
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void receivePlacementPacket(PacketEvent event) {
		PacketContainer packet = event.getPacket();
		Player player = event.getPlayer();

		Integer facing = packet.getIntegers().readSafely(0);
		if (facing == null) {
			facing = 0;
		}
		if (facing == 255) {
			return;
		}
		User user = userOf(player);
		Material material = user.meta().inventory().heldItemType();
		boolean hasPlaceable = material.isBlock() && material.isSolid();
		if (!hasPlaceable) {
			return;
		}
		placedInThisTick = true;
	}
//
//  @PacketSubscription(
//    priority = ListenerPriority.HIGH,
//    packetsIn = {
//      ENTITY_ACTION_IN
//    }
//  )
//  public void receiveEntityActionPacket(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    PlayerActionReader reader = PacketReaders.readerOf(packet);
//
//    PlayerAction action = reader.playerAction();
//    switch (action) {
//      case START_SNEAKING:
//        startSneakInThisTick = true;
//        sneakChangedInThisTick = true;
//        isSneaking = true;
//        sneakDuration = 0;
//        break;
//      case STOP_SNEAKING:
//        stopSneakInThisTick = true;
//        sneakChangedInThisTick = true;
//        isSneaking = false;
//        lastSneakDuration = sneakDuration;
//        sneakDuration = 0;
//        break;
//    }
//
//    reader.release();
//  }

	@BukkitEventSubscription
	public void on(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);

		if (place.getBlock().getY() < player.getLocation().getBlockY() && isOneLine(lastBlocksPlaced) && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
			if (violationLevel > 5) {
				Violation violation = Violation.builderFor(PlacementAnalysis.class)
					.forPlayer(player).withDefaultThreshold()
					.withMessage(COMMON_FLAG_MESSAGE)
					.withDetails("sneaking seems to be automated (jump)")
					.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
					.withDefaultThreshold().withVL(Math.min(violationLevel / 1.5, 5)).build();
				Modules.violationProcessor().processViolation(violation);
			}
		} else {
			violationLevel = 0;
		}
		if (place.isCancelled()) {
			return;
		}
		if (lastBlocksPlaced.size() > 5) {
			lastBlocksPlaced.remove(0);
		}
		lastBlocksPlaced.add(place.getBlock().getLocation().toVector());
	}

	private boolean isOneLine(List<? extends Vector> blocks) {
		int lastBlockX = 0,
			lastBlockY = 0,
			lastBlockZ = 0;
		boolean lockedOnX = false,
			lockedOnZ = false;
		boolean first = true;
		int yTolerance = 2;
		for (Vector block : blocks) {
			if (!first) {
				if (lastBlockY != block.getY()) {
					if (yTolerance-- <= 0) {
						return false;
					}
				} else {
					if (lastBlockX == block.getX()) {
						lockedOnX = true;
					} else if (lockedOnX) {
						return false;
					}
					if (lastBlockZ == block.getZ()) {
						lockedOnZ = true;
					} else if (lockedOnZ) {
						return false;
					}
				}
			}
			lastBlockX = block.getBlockX();
			lastBlockY = block.getBlockY();
			lastBlockZ = block.getBlockZ();
			first = false;
		}
		return lockedOnX || lockedOnZ;
	}

	private boolean blockAgainstWasPlaced(User user, Block blockAgainst) {
		Vector vector = blockAgainst.getLocation().toVector();
		for (Vector location : lastBlocksPlaced) {
			if (location.distance(vector) == 0) {
				return true;
			}
		}
		return false;
	}
}
