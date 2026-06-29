package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class SmartSpeed extends PlayerCheckPart<PlacementAnalysis> {
	private final List<Rotation> pastRotations = new LinkedList<>();
	private final List<Integer> placementSpeedHistory = new LinkedList<>();
	private final List<Long> preplacementSneakDelay = new LinkedList<>();
	private final List<Long> postplacementSneakDelay = new LinkedList<>();
	private final List<Placement> placementHistory = new LinkedList<>();
	private long lastPlacementTick;
	private int ticksSinceHardFaultClick = 100;
	private int ticksSinceBlockPlacement = 100;

	// sneak-related
	private boolean startSneakInThisTick;
	private boolean stopSneakInThisTick;
	private boolean sneakChangedInThisTick;
	private boolean placedInThisTick;
	private long lastSneakStart;
	private long lastPlace;
	private long lastSneakDuration = 10;
	private long sneakDuration;
	private double sneakVL;
	private long tickCount;

	public SmartSpeed(User user, PlacementAnalysis parent) {
		super(user, parent);
	}

	@PacketSubscription(
		packetsIn = {
			BLOCK_PLACE, USE_ITEM
		}
	)
	public void receivePlacementPacket(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		PacketContainer packet = event.getPacket();

		BlockInteractionReader reader = PacketReaders.readerOf(packet);
		try {
			if (event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE) {
				int facing = reader.enumDirection();
				if (facing == 255) {
					ticksSinceHardFaultClick = 0;
				} else {
					Material material = user.meta().inventory().heldItemType();
					boolean hasPlaceable = material.isBlock() && material.isSolid();
					if (!hasPlaceable) {
						return;
					}

					// placement logic
					List<Integer> placementSpeedHistory = this.placementSpeedHistory;
					if (placementSpeedHistory.size() > 100) {
						placementSpeedHistory.remove(0);
					}
					placementSpeedHistory.add(ticksSinceBlockPlacement);

					List<Placement> placementHistory = this.placementHistory;
					if (placementHistory.size() > 100) {
						placementHistory.remove(0);
					}
					BlockPosition blockPosition = reader.nativeBlockPosition();
					Direction direction = reader.direction();

					double diff = blockPosition.getBlockY() - user.meta().movement().positionY;
					boolean under = diff < 0 && diff > -2.5;
					boolean highRotationSinceLastPlacement = false;

					boolean near = placementHistory.stream().anyMatch(placement -> placement.position.distanceTo(blockPosition) < 1.1);

					long currentTick = tickCount;
					long lastPlacement = lastPlacementTick;
					int duration = (int) (currentTick - lastPlacement);

					float rotationSinceLastPlacement = 0;
					float highestPitch = 0;
					if (lastPlacement != -1) {
						List<Rotation> pastRotations = this.pastRotations;
						if (pastRotations.size() > 2) {
							for (int i = pastRotations.size() - 1; i >= Math.max(1, pastRotations.size() - duration); i--) {
								Rotation rotation = pastRotations.get(i);
								rotationSinceLastPlacement += rotation.distanceTo(pastRotations.get(i - 1));
								highestPitch = Math.max(highestPitch, rotation.pitch());
							}
						}
					}
					if (rotationSinceLastPlacement > 180) {
						highRotationSinceLastPlacement = true;
					}

					placementHistory.add(new Placement(blockPosition, direction, ticksSinceBlockPlacement, tickCount, near));

					double average = 0;
					int size = 3;
					if (placementSpeedHistory.size() >= size) {
						int requiredElements = size;
						for (int i = placementSpeedHistory.size() - 1; i >= Math.max(0, placementSpeedHistory.size() - requiredElements); i--) {
							Direction placementDirection = placementHistory.get(i) == null ? Direction.UP : placementHistory.get(i).direction();
							if (placementDirection != null && placementDirection.axis().isVertical()) {
								//              System.out.println("Skipping placement because it is on the y axis: " + placementDirection);
								requiredElements++;
								continue;
							}
							average += placementSpeedHistory.get(i);
						}
						average /= size;
					}

					int minimumTicks = 50;


					double finalSpeedAverageOfLastTwo = average;
					//        boolean finalHighRotationSinceLastPlacement = highRotationSinceLastPlacement;
					float finalRotationSinceLastPlacement = rotationSinceLastPlacement;
					float finalLowestPitch = highestPitch;
					//        Synchronizer.synchronize(() -> {
					//          player.sendMessage((under && near ? ChatColor.GRAY : ChatColor.DARK_GRAY) + MathHelper.formatDouble(finalSpeedAverageOfLastTwo, 2) + "b/t, rot:" + finalRotationSinceLastPlacement + ", pitch:"+ finalLowestPitch);
					//        });

					ticksSinceBlockPlacement = 0;
					lastPlacementTick = tickCount;
					placedInThisTick = true;
				}
			}
		} finally {
			reader.release();
		}
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			FLYING, POSITION_LOOK, LOOK, POSITION
		}
	)
	public void on(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();

		float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);
		boolean recentBlockPlacement = ticksSinceBlockPlacement < 10;

//    boolean hit = Math.abs(rotationMovement - 180) < 10;
//    if (hit && recentBlockPlacement) {
//
//    }

		List<Rotation> pastRotations = this.pastRotations;

		if (pastRotations.size() > 100) {
			pastRotations.remove(0);
		}

		if (movementData.rotationYaw != movementData.lastRotationYaw ||
			movementData.rotationPitch != movementData.lastRotationPitch) {
			pastRotations.add(movementData.rotation());
		} else {
			pastRotations.add(pastRotations.size() > 1 ? pastRotations.get(pastRotations.size() - 1) : Rotation.zero());
		}

		// sneaking logic
		if (placedInThisTick) {
			// difference to last sneak start
			long diff = startSneakInThisTick ? 0 : tickCount - lastSneakStart;

			List<Long> preplacementSneakDelay = this.preplacementSneakDelay;
			if (preplacementSneakDelay.size() > 100) {
				preplacementSneakDelay.remove(0);
			}
			preplacementSneakDelay.add(diff);

			boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;
			if (!suspiciousSneaking && sneakVL > 0) {
				sneakVL -= 0.05;
			} else if (suspiciousSneaking) {
				sneakVL += diff > 1 ? 0.1 : 1;
			}
		}
		if (sneakChangedInThisTick) {
			// difference to last place
			long diff = placedInThisTick ? 0 : tickCount - lastPlace;
			boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;

			List<Long> postplacementSneakDelay = this.postplacementSneakDelay;
			if (postplacementSneakDelay.size() > 100) {
				postplacementSneakDelay.remove(0);
			}
			postplacementSneakDelay.add(diff);

			if (!suspiciousSneaking && sneakVL > 0) {
				sneakVL -= 0.05;
			} else if (suspiciousSneaking) {
				sneakVL += diff > 1 ? 0.1 : 1;
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

		// movement logic

		// tick couting
		ticksSinceHardFaultClick++;
		ticksSinceBlockPlacement++;
		tickCount++;
		sneakDuration++;
	}

//  private int requiredPlacementTicks(User user) {
//
//
//    return 0;
//  }

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			ENTITY_ACTION_IN
		}
	)
	public void receiveEntityActionPacket(PacketEvent event) {
		PacketContainer packet = event.getPacket();
		PlayerActionReader reader = PacketReaders.readerOf(packet);
		PlayerAction action = reader.playerAction();
		if (action.isStartSneak()) {
			startSneakInThisTick = true;
			sneakChangedInThisTick = true;
			sneakDuration = 0;
		} else if (action.isStopSneak()) {
			stopSneakInThisTick = true;
			sneakChangedInThisTick = true;
			lastSneakDuration = sneakDuration;
			sneakDuration = 0;
		}
		reader.release();
	}

	private static class Placement {
		private final BlockPosition position;
		private final Direction direction;
		private final int ticksSinceLast;
		private final long tickCount;
		private final boolean connected;
		private boolean wasSneakingSinceLast;

		public Placement(BlockPosition position, Direction direction, int ticksSinceLast, long tickCount, boolean connected) {
			this.position = position;
			this.direction = direction;
			this.ticksSinceLast = ticksSinceLast;
			this.connected = connected;
			this.tickCount = tickCount;
		}

		public BlockPosition position() {
			return position;
		}

		public Direction direction() {
			return direction;
		}

		public int ticksSince() {
			return ticksSinceLast;
		}

		public boolean wasSneakingSince() {
			return wasSneakingSinceLast;
		}

		public void registerSneak() {
			wasSneakingSinceLast = true;
		}

		public long tickCountAt() {
			return tickCount;
		}
	}

	private boolean isOneLine(List<? extends BlockPosition> blocks) {
		int lastBlockX = 0,
			lastBlockY = 0,
			lastBlockZ = 0;
		boolean lockedOnX = false,
			lockedOnZ = false;
		boolean first = true;
		int yTolerance = 2;
		for (BlockPosition block : blocks) {
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
		for (Placement placement : placementHistory) {
			if (placement.position().distanceTo(vector) == 0) {
				return true;
			}
		}
		return false;
	}

  /*
  public static class Action {
  }

  public static class Place extends Action {
    private Position blockPosition;
    private EnumDirection direction;
    private Vector facing;
  }

  public static class Click extends Action {

  }

  public static class Move extends Action {
    private Position position;
    private Rotation rotation;
  }

  public static class Sneak extends Action {
    private boolean started;
  }
   */
}
