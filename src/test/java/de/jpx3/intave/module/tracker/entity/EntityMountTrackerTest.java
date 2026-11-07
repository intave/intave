package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityMountTrackerTest {
  private static final int PLAYER = 7;
  private final Queue<EmptyFeedbackCallback> feedback = new ArrayDeque<>();
  private final EntityMountTracker tracker = new EntityMountTracker();
  private User user;
  private Entity vehicle;

  @BeforeEach
  void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    BlockPhysics.setup(MinecraftVersions.VER26_3);
    Entity.setup();
    var world = FakeWorldFactory.createWorld((name, args) -> switch (name) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> new Location(world, 0, 64, 0);
      case "getUniqueId" -> id;
      case "getEntityId" -> PLAYER;
      case "teleport" -> throw new AssertionError("Mount acknowledgement must not teleport the player");
      default -> null;
    });
    User delegate = UserFactory.createTestUserFor(player, (ignored, key) -> switch (key) {
      case "protocolVersion" -> ProtocolMetadata.VER_26_3;
      case "blockCache" -> MockFullBlockStaticPlane.createWithHorizontalPlaneAt(63);
      default -> null;
    });
    user = (User) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
      if (method.getName().equals("tickFeedback")) {
        feedback.add((EmptyFeedbackCallback)args[0]);
        return null;
      }
      return method.invoke(delegate, args);
    });
    user.meta().movement().setLastPosition(new Position(0, 64, 0));
    vehicle = entity(10);
  }

  @Test
  void mountingWaitsForAcknowledgementAndIncludesTheUntrackedLocalPlayer() {
    assertNull(user.meta().connection().entityBy(PLAYER));
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER});
    assertFalse(user.meta().movement().isInVehicle());
    acknowledge();
    assertSame(vehicle, user.meta().movement().vehicle());
    assertEquals(List.of(PLAYER), user.meta().connection().sittingOn(vehicle.entityId()));
  }

  @Test
  void repeatedUpdatesAndAnotherPassengerDoNotResetTheRider() {
    mountLocalPlayer();
    assertEquals(vehicle.typeData().size().width(), user.meta().movement().width());
    user.meta().movement().invalidVehiclePositionTicks = 17;
    Entity passenger = entity(11);
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER});
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER, passenger.entityId()});
    acknowledge();
    assertEquals(17, user.meta().movement().invalidVehiclePositionTicks);
    acknowledge();
    assertEquals(17, user.meta().movement().invalidVehiclePositionTicks);
    assertSame(vehicle, passenger.vehicle());
    assertEquals(List.of(passenger), vehicle.passengers());
  }

  @Test
  void emptyUpdateClearsLocalVehicleAndBothMountIndexes() {
    mountLocalPlayer();
    tracker.passengers(user, vehicle.entityId(), new int[0]);
    acknowledge();
    assertFalse(user.meta().movement().isInVehicle());
    assertNull(user.meta().connection().vehicleOf(PLAYER));
    assertTrue(user.meta().connection().sittingOn(vehicle.entityId()).isEmpty());
  }

  @Test
  void firstMovementAfterDismountUsesThePlayerCollisionBox() {
    for (String type : new String[]{"Horse", "Boat"}) {
      vehicle.setTypeData(new EntityTypeData(type, HitboxSize.of(1.4F, 1.6F), 1, true, 0));
      var movement = user.meta().movement();
      Position start = new Position(0.5, 64, 0.25);
      movement.setPosition(start);
      movement.setVerifiedLastPosition(start, "dismount test");
      mountLocalPlayer();
      movement.setSimulator(Simulators.selectFor(movement));
      movement.tickComplete(false, false, true);
      assertEquals(type.equals("Boat") ? 1.375F : 1.4F, movement.width());

      tracker.passengers(user, vehicle.entityId(), new int[0]);
      acknowledge();
      assertEquals(0.6F, movement.width());
      assertEquals(BoundingBox.fromPosition(user, movement, start), movement.boundingBox());
      movement.setSimulator(Simulators.selectFor(movement));
      var simulation = Simulators.PLAYER.simulateTick(
        user, new Motion(0, 0, 0.1), movement.immutableView(), MovementConfiguration.blank()
      );

      assertEquals(0.1, simulation.offsetMotion().motionZ, 1.0E-9);
      assertEquals(1.8F, movement.height());
      BoundingBox after = BoundingBox.fromPosition(user, movement, simulation.postTickPosition());
      assertFalse(after.intersectsWith(BoundingBox.fromBounds(0, 64, 1, 1, 66, 2)));
    }
  }

  @Test
  void queuedMountThenDismountUsesAcknowledgedState() {
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER});
    tracker.passengers(user, vehicle.entityId(), new int[0]);
    acknowledge();
    assertTrue(user.meta().movement().isInVehicle());
    acknowledge();
    assertFalse(user.meta().movement().isInVehicle());
    assertNull(user.meta().connection().vehicleOf(PLAYER));
  }

  @Test
  void switchingVehiclesRemovesOldMembershipAndIgnoresOldVehicleRemoval() {
    mountLocalPlayer();
    Entity next = entity(12);
    tracker.passengers(user, next.entityId(), new int[]{PLAYER});
    tracker.passengers(user, vehicle.entityId(), new int[0]);
    acknowledge();
    assertSame(next, user.meta().movement().vehicle());
    assertTrue(user.meta().connection().sittingOn(vehicle.entityId()).isEmpty());
    acknowledge();
    assertSame(next, user.meta().movement().vehicle());
    assertEquals(next.entityId(), user.meta().connection().vehicleOf(PLAYER));
  }

  @Test
  void packetSnapshotCannotChangeWhileWaitingForFeedback() {
    int[] ids = {PLAYER};
    tracker.passengers(user, vehicle.entityId(), ids);
    ids[0] = 9999;
    acknowledge();
    assertSame(vehicle, user.meta().movement().vehicle());
  }

  @Test
  void trackedPassengersFollowOrderRemovalAndVehicleSwitches() {
    Entity first = entity(11);
    Entity second = entity(12);
    Entity otherVehicle = entity(13);
    tracker.passengers(user, vehicle.entityId(), new int[]{11, 12});
    tracker.passengers(user, vehicle.entityId(), new int[]{12, 11});
    acknowledge();
    acknowledge();
    assertEquals(List.of(second, first), vehicle.passengers());
    tracker.passengers(user, otherVehicle.entityId(), new int[]{11});
    acknowledge();
    assertEquals(List.of(second), vehicle.passengers());
    assertSame(otherVehicle, first.vehicle());
    assertEquals(List.of(12), user.meta().connection().sittingOn(vehicle.entityId()));
    tracker.passengers(user, otherVehicle.entityId(), new int[0]);
    acknowledge();
    assertNull(first.vehicle());
    assertNull(user.meta().connection().vehicleOf(11));
  }

  @Test
  void unknownAndDestroyedVehiclesAndPassengersAreIgnored() {
    tracker.passengers(user, 9999, new int[]{PLAYER});
    acknowledge();
    assertFalse(user.meta().movement().isInVehicle());
    user.meta().connection().markForDeletion(vehicle.entityId());
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER});
    acknowledge();
    assertFalse(user.meta().movement().isInVehicle());
    Entity present = entity(12);
    tracker.passengers(user, present.entityId(), new int[]{9999});
    acknowledge();
    assertTrue(present.passengers().isEmpty());
    assertNull(user.meta().connection().vehicleOf(9999));
  }

  @Test
  void batchedRemovalOnlyDropsEntitiesThatAreStillMarked() {
    Entity destroyed = entity(11);
    entity(12);
    var connection = user.meta().connection();
    connection.markForDeletion(destroyed.entityId());
    connection.markForDeletion(12);
    Entity respawned = entity(12);
    connection.removeEntitiesIfMarked(new int[]{destroyed.entityId(), 12});
    assertNull(connection.entityBy(destroyed.entityId()));
    assertSame(respawned, connection.entityBy(12));
  }

  @Test
  void legacyAttachmentAlsoClearsTheLocalPlayerWithoutATrackedEntity() {
    tracker.attachment(user, PLAYER, vehicle.entityId());
    tracker.attachment(user, PLAYER, -1);
    acknowledge();
    assertSame(vehicle, user.meta().movement().vehicle());
    acknowledge();
    assertFalse(user.meta().movement().isInVehicle());
    assertNull(user.meta().connection().vehicleOf(PLAYER));
  }

  private void mountLocalPlayer() {
    tracker.passengers(user, vehicle.entityId(), new int[]{PLAYER});
    acknowledge();
  }

  private void acknowledge() {
    assertNotNull(feedback.peek());
    feedback.remove().success();
  }

  private Entity entity(int id) {
    Entity entity = new Entity(id, new EntityTypeData("Horse", HitboxSize.of(1.4F, 1.6F), 1, true, 0), false);
    entity.setPosition(0, 64, 0);
    user.meta().connection().enterEntity(entity);
    return entity;
  }
}
