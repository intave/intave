package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.collision.entity.StaticEntityCollisions;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.event.EntityMoveEvent;
import de.jpx3.intave.module.nayoro.event.EntityRemoveEvent;
import de.jpx3.intave.module.nayoro.event.EntitySpawnEvent;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.reader.EntityIterable;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.IdentifierReserve;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static de.jpx3.intave.module.feedback.FeedbackOptions.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.user.meta.ConnectionMetadata.DecoySide.FIRST_IS_DECOY;
import static de.jpx3.intave.user.meta.ConnectionMetadata.DecoySide.SECOND_IS_DECOY;

public final class EntityTracker extends Module {
  /*
  TODO: when a entity gets spawned and the spawn packet gets send to the client and the entity gets teleported right after,
   the check will try to create the entity by the teleport packet bevor the entity spawn packet can be executed
   TODO: maybe remove entities when their live gets below 0 for 20 ticks. Or debug if entities gets really removed in some kind of root command
   */
  private final EntityTypeResolver entityTypeResolver;
  private final PeriodicEntityCoverageSelector coverageSelector;
//  private final PeriodicTickedEntitySelector tickedEntitySelector;

  private final boolean NEW_POSITION_PROCESSING_1_9 = MinecraftVersions.VER1_9_0.atOrAbove();

  public EntityTracker(IntavePlugin plugin) {
    this.plugin = plugin;
    this.entityTypeResolver = new EntityTypeResolver(plugin);
    this.coverageSelector = PeriodicEntityCoverageSelector.builder()
      .withRefreshIntervalInSeconds(1)
      .withDistanceRequirement(16)
      .withMaxTracedEntities(4)
      .withMaxDoubleTracedEntities(1)
      .withEntityAdditionListener(this::nayoroEntitySpawn)
      .withEntityRemovalListener(this::nayoroEntityDespawn)
      .build();
  }

  @Override
  public void enable() {
    coverageSelector.enableTask();
  }

  @Override
  public void disable() {
    coverageSelector.disableTask();
  }

  @PacketSubscription(
    packetsOut = {
      MOUNT, ATTACH_ENTITY
    },
    ignoreCancelled = false
  )
  public void sendAttachEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (event.getPacketType() == PacketType.Play.Server.MOUNT) {
      //1.9+ servers
      int vehicleId = packet.getIntegers().read(0);
      Entity vehicle = UserRepository.userOf(player).meta().connection().entityBy(vehicleId);
      if (vehicle == null) {
        IntaveLogger.logger().error("Vehicle entity not found in mount request: " + vehicleId);
        detachEntity(user, vehicleId, -1);
        return;
      }
      int[] newPassengers = event.getPacket().getIntegerArrays().read(0);
      List<Entity> oldPassengers = vehicle.passengers();
      List<Integer> toAdd = new ArrayList<>();
      List<Integer> toRemove = new ArrayList<>();
      for (int passengerId : newPassengers) {
        boolean b = true;
        for (Entity entity : oldPassengers) {
          if (entity.entityId() == passengerId) {
            b = false;
            break;
          }
        }
        if (b) {
          toAdd.add(passengerId);
        }
      }
      for (Entity passenger : oldPassengers) {
        boolean b = true;
        for (int id : newPassengers) {
          if (id == passenger.entityId()) {
            b = false;
            break;
          }
        }
        if (b) {
          toRemove.add(passenger.entityId());
        }
      }
      for (Integer passengerRemoval : toRemove) {
        detachEntity(user, vehicleId, passengerRemoval);
      }
      for (Integer passengerAddition : toAdd) {
        attachEntity(user, vehicleId, passengerAddition);
      }
    } else if (event.getPacketType() == PacketType.Play.Server.ATTACH_ENTITY) {
      // 1.8 servers
      int isLeash = packet.getIntegers().read(0);
      if (isLeash == 0) {
        int passengerId = packet.getIntegers().read(1);
        int vehicleId = packet.getIntegers().read(2);
        if (vehicleId == -1) {
          detachEntity(user, -1, passengerId);
        } else {
          attachEntity(user, vehicleId, passengerId);
        }
      }
    }
  }

  private void attachEntity(User observer, int vehicleId, int passengerId) {
    ConnectionMetadata connection = observer.meta().connection();
    tryCreateVehicleEntity(observer, vehicleId);
    Entity vehicle = connection.entityBy(vehicleId);
    Entity passenger = connection.entityBy(passengerId);
    boolean passengerIsObserver = passenger == null && passengerId == observer.player().getEntityId();
    if (vehicle == null || vehicle == Entity.destroyedEntity()) {
      return;
    }
    if (IntaveControl.DEBUG_MOUNTING) {
      Bukkit.broadcastMessage("ATTACH " + passengerId + " to " + vehicleId);
    }
    observer.tickFeedback(() -> {
      if (passenger != null) {
        vehicle.addPassenger(passenger);
        passenger.mountToEntity(vehicle);
      }
      connection.noteMount(passengerId, vehicleId);
      if (passengerIsObserver) {
        MovementMetadata movement = observer.meta().movement();
        if (movement.isInVehicle()) {
          movement.dismountRidingEntity("Override");
        }
        movement.setVehicle(vehicle);
      }
    });
  }

  private void detachEntity(User observer, int vehicleId, int passengerId) {
    ConnectionMetadata connection = observer.meta().connection();
    Entity passenger = connection.entityBy(passengerId);
    boolean passengerIsObserver = passengerId == observer.player().getEntityId();
    if (passenger == null && !passengerIsObserver) {
      return;
    }
    if (IntaveControl.DEBUG_MOUNTING) {
      Bukkit.broadcastMessage("DETACH " + passengerId + " from " + vehicleId);
    }
    Entity vehicle = passengerIsObserver ? observer.meta().movement().vehicle() : passenger.vehicle();
    observer.tickFeedback(() -> {
      if (passenger == null) {
        return;
      }
      if (!passengerIsObserver) {
        if (vehicle != null) {
          vehicle.removePassenger(passenger);
        }
        passenger.unmountFromEntity();
      }
      connection.noteDismount(passengerId);
      if (passengerIsObserver) {
        MovementMetadata movement = observer.meta().movement();
        movement.dismountRidingEntity("Dismount");
      }
    });
  }

  private void tryCreateVehicleEntity(User user, int entityID) {
    org.bukkit.entity.Entity entity = serverEntityByIdentifier(user.player(), entityID);
    if (entity != null && user.meta().connection().entityBy(entityID) == null) {
      spawnMobByBukkitEntity(user, entity);
    }
  }

  @PacketSubscription(
    packetsOut = {
      SPAWN_ENTITY_LIVING, SPAWN_ENTITY, NAMED_ENTITY_SPAWN
    },
    ignoreCancelled = false
  )
  public void sendEntitySpawn(PacketEvent event) {
    /* IMPORTANT: If the entity spawn packet gets synchronized the player could be spammed with transaction packets
     *   which could cause a too many packets kick
     *
     * Also: When this packet gets synchronized (via appending the event on the next transaction packet) the entity_teleport and other entity move packets needs
     *  to be verified too because these packets could come in the wrong order.
     */
//    plugin.eventService().transactionFeedbackService().requestPong(event.getPlayer(), event, this::processEntitySpawn);
//    Thread.dumpStack();


    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    Set<Integer> duplicatedEntityIds = connection.duplicatedEntityIds;
    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;

    Integer entityIdBoxed = event.getPacket().getIntegers().readSafely(0);
    if (entityIdBoxed == null) {
      return;
    }
    int entityId = entityIdBoxed;
    if (duplicatedEntityIds.contains(entityId)) {
      return;
    }
    Entity entity = processEntitySpawn(player, event);
    if (entity == null) {
      return;
    }
    boolean isLivingEntity = (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY_LIVING ||
      event.getPacketType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN) && entity.typeData().isLivingEntity();
    boolean isPlayer = event.getPacketType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN;
    boolean hasRedTrustfactor = !user.trustFactor().atLeast(TrustFactor.ORANGE);
    boolean oneInFourChance = ThreadLocalRandom.current().nextInt(4) == 0;

    if (/*isLivingEntity && isPlayer *//*&& hasRedTrustfactor*//* && oneInFourChance*/ false) {
      int newId = IdentifierReserve.acquireNew();
      duplicatedEntityIds.add(newId);
      duplicationOwners.put(newId, entityId);

      boolean makeOwnerInvisible = ThreadLocalRandom.current().nextBoolean();
      PacketContainer oldPacket = event.getPacket();
      PacketContainer newPacket = oldPacket.deepClone();
      modifyWatchablesOf((makeOwnerInvisible ? oldPacket : newPacket));
      //is this correct? - yes it is
      connection.shouldNotBeAttacked.add(entityId);
      connection.decoySides.put(entityId, makeOwnerInvisible ? SECOND_IS_DECOY : FIRST_IS_DECOY);
      entity.duplicationId = newId;
      newPacket.getIntegers().write(0, newId);
      PacketSender.sendServerPacket(player, newPacket);
    }
//    Modules.feedback().singleSynchronize(event.getPlayer(), event, this::processEntitySpawn, APPEND_ON_OVERFLOW);
  }

//  @PacketSubscription(
//    packetsOut = {
//      ANIMATION, ENTITY_EFFECT, ENTITY_VELOCITY, ENTITY_EQUIPMENT, ENTITY_HEAD_ROTATION, ENTITY_STATUS,
//      REMOVE_ENTITY_EFFECT, UPDATE_ATTRIBUTES, USE_BED
//    }
//  )
//  public void on(PacketEvent event) {
//    Player player = event.getPlayer();
//    User user = UserRepository.userOf(player);
//    PacketContainer packet = event.getPacket();
//    EntityIterable reader = PacketReaders.readerOf(packet);
//
//    for (Integer integer : reader) {
//      Entity entity = user.meta().connection().entityBy(integer);
//      if (entity == null) {
//        continue;
//      }
//      if (entity.duplicationId != 0) {
//        PacketContainer newPacket;
//        try {
//          newPacket = packet.deepClone();
//        } catch (Exception exception) {
//          System.out.println(exception.getClass().getSimpleName() + " while cloning packet " + packet.getType() + ": " + exception.getMessage());
//          newPacket = packet.shallowClone();
//        }
//        newPacket.getIntegers().write(0, entity.duplicationId);
//        PacketSender.sendServerPacket(event.getPlayer(), newPacket);
//      }
//    }
//
//    reader.release();
//  }

  private void modifyWatchablesOf(PacketContainer packet) {
    List<WrappedWatchableObject> watchables = packet.getWatchableCollectionModifier().readSafely(0);
    if (watchables != null) {
      WrappedWatchableObject theObject = null;
      for (WrappedWatchableObject watchableObject : watchables) {
        if (watchableObject.getIndex() == 0) {
          theObject = watchableObject;
          break;
        }
      }
      if (theObject != null) {
        theObject.setDirtyState(false);
        watchables = new ArrayList<>(watchables);
        watchables.remove(theObject);
        theObject = new WrappedWatchableObject(theObject.getIndex(), theObject.getValue());
        byte original = (byte) theObject.getValue();
        byte value = (byte) (original | 0x20);
        theObject.setValue(value);
        watchables.add(theObject);
//        System.out.println("Modified watchable object new value: " + Integer.toBinaryString(value));
      } /*else {
        theObject = new WrappedWatchableObject(0, (byte) 0);
        byte value = (byte) (0x20 | 0x01);
        theObject.setValue(value);
        watchables.add(theObject);
//        System.out.println("Added watchable object new value: " + Integer.toBinaryString(value));
      }*/
      packet.getWatchableCollectionModifier().write(0, watchables);
    }
  }

  private Entity processEntitySpawn(Player player, PacketEvent event) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    EntityTypeData typeData;
    boolean entityIsPlayer = false;
    Integer entityId = packet.getIntegers().read(0);
    if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
      // dead entities
      typeData = entityTypeResolver.entityTypeDataOfDeadEntity(event);
    } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
      // entities
      typeData = entityTypeResolver.entityTypeDataOfLivingEntity(event);
    } else {
      // player
      FakePlayer fakePlayer = attackData.fakePlayer();
      String entityName;
      if (fakePlayer != null && fakePlayer.identifier() == entityId) {
        entityName = "Intave-Bot";
      } else {
        entityName = "Player";
      }

      HitboxSize hitBoxSize = HitboxSize.playerDefault();
      entityIsPlayer = true;
      typeData = new EntityTypeData(entityName, hitBoxSize, 105, true, 1);
    }
    if (typeData == null) {
      if (IntaveControl.DEBUG) {
        IntavePlugin.singletonInstance().logger().error("Cannot resolve entityType: " + entityId);
      }
      return null;
    }
    if ("ServerPlayer".equalsIgnoreCase(typeData.name())) {
      entityIsPlayer = true;
    }
    return processPacketSpawnMob(user, packet, typeData, entityId, entityIsPlayer);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_DESTROY
    },
    ignoreCancelled = false
  )
  public void receiveEntityDestroy(Player player, EntityIterable iterable) {
    iterable.forEach(entityId ->
      enterEntityDestroy(player, entityId)
    );
  }

  private void enterEntityDestroy(Player player, int entityID) {
    // Entity destroy packets are NEVER to be synchronized
    /*
    Important: When the destroy entity packet is synchronised the spawn entity packet needs also be synchronized because:
    When you respawn the server sends a destroy entity packet and a spawn entity packet pretty fast one after another and if the
    destroy entity packet gets executed after the spawn packet the entity will be destroyed right after it gets spawned
     */
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    if (connection.duplicatedEntityIds.contains(entityID)) {
      return;
    }
    processEntityDestroy(player, entityID);
  }

  private void processEntityDestroy(Player player, int entityId) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ConnectionMetadata connection = user.meta().connection();
    MovementMetadata movementData = user.meta().movement();

    Entity entity = connection.entityBy(entityId);//synchronizedEntityMap.get(entityId);
    if (entity != null && movementData.ridingEntity() == entity) {
      movementData.dismountRidingEntity("Entity Destroy");
    }

    if (entity != null && entity.duplicationId != 0) {
      connection.duplicatedEntityIds.remove(entity.duplicationId);
      connection.shouldNotBeAttacked.remove(connection.duplicationOwners.remove(entity.duplicationId));
      PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
      packet.getIntegerArrays().write(0, new int[]{entity.duplicationId});
      PacketSender.sendServerPacket(player, packet);
    }

    connection.markForDeletion(entityId);

    Synchronizer.synchronize(() -> {
      user.tickFeedback(() -> {
        Synchronizer.synchronize(() -> {
          user.tickFeedback(() -> {
            connection.removeEntityIfMarked(entityId);
          }/*, APPEND_ON_OVERFLOW*/);
        });
      }/*, APPEND_ON_OVERFLOW*/);
    });

    if (entity != null) {
      StaticEntityCollisions.enterEntityDespawn(user, entity);
    }

    if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntityID() == entityId) {
      attackData.nullifyLastAttackedEntity();
    }

    if (NEW_POSITION_PROCESSING_1_9) {
      List<Integer> sitters = connection.sittingOn(entityId);
      for (Integer sitter : sitters) {
        Entity sitterEntity = connection.entityBy(sitter);
        if (sitterEntity != null) {
          sitterEntity.unmountFromEntity();
        }
      }
    }

    if (IntaveControl.DEBUG_ENTITY_TRACKING) {
      Synchronizer.synchronize(() -> {
        Player target = user.player();
        if (target == null || entity == null) {
          return;
        }
        EntityTypeData typeData = entity.typeData();
        target.sendMessage(ChatColor.RED + typeData.name() + "/" + typeData.typeId() + " as " + entity.entityId());
      });
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, STEER_VEHICLE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user.meta().protocol().sendsClientTickEnd()) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movement = user.meta().movement();
    if (movement.lastTeleport == 0) {
      return;
    }
    for (Entity entity : synchronizeData.entities()) {
      int ticksAfterPositionChange = entity.position.newPosRotationIncrements;
      entity.onUpdate();
      if (entity.tracingEnabled() && ticksAfterPositionChange > 0) {
        nayoroEntityPositionUpdate(player, entity);
      }
      if (movement.isRiding(entity.entityId()) && !MinecraftVersions.VER1_9_0.atOrAbove()) {
        double originalX = entity.position.newPosX;
        double originalY = entity.position.newPosY;
        double originalZ = entity.position.newPosZ;
        if (Math.abs(originalX) < 0.1 && Math.abs(originalY) < 0.1 && Math.abs(originalZ) < 0.1) {
          originalX = entity.position.posX;
          originalY = entity.position.posY;
          originalZ = entity.position.posZ;
        }
        movement.positionX = movement.verifiedPositionX = movement.lastPositionX = originalX;
        movement.positionY = movement.verifiedPositionY = movement.lastPositionY = originalY;
        movement.positionZ = movement.verifiedPositionZ = movement.lastPositionZ = originalZ;
        movement.verifiedPositionOrigin = "Riding pos sync (1.8)";
        movement.setBaseMotionX(0);
        movement.setBaseMotionY(0);
        movement.setBaseMotionZ(0);
      }
    }
  }

  @PacketSubscription(
    packetsIn = {
      CLIENT_TICK_END
    },
    debug = true
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (!user.meta().protocol().sendsClientTickEnd()) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movement = user.meta().movement();
    if (movement.lastTeleport == 0) {
      return;
    }
    for (Entity entity : synchronizeData.entities()) {
      int ticksAfterPositionChange = entity.position.newPosRotationIncrements;
      entity.onUpdate();
      if (entity.tracingEnabled() && ticksAfterPositionChange > 0) {
        nayoroEntityPositionUpdate(player, entity);
      }
      if (movement.isRiding(entity.entityId()) && !MinecraftVersions.VER1_9_0.atOrAbove()) {
        double originalX = entity.position.newPosX;
        double originalY = entity.position.newPosY;
        double originalZ = entity.position.newPosZ;
        if (Math.abs(originalX) < 0.1 && Math.abs(originalY) < 0.1 && Math.abs(originalZ) < 0.1) {
          originalX = entity.position.posX;
          originalY = entity.position.posY;
          originalZ = entity.position.posZ;
        }
        movement.positionX = movement.verifiedPositionX = movement.lastPositionX = originalX;
        movement.positionY = movement.verifiedPositionY = movement.lastPositionY = originalY;
        movement.positionZ = movement.verifiedPositionZ = movement.lastPositionZ = originalZ;
        movement.verifiedPositionOrigin = "Riding pos sync (1.8)";
        movement.setBaseMotionX(0);
        movement.setBaseMotionY(0);
        movement.setBaseMotionZ(0);
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_POSITION_SYNC
    }
  )
  public void receivePositionSync(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Entity entity = wrappedEntityByEntityTeleportPacket(event);
    if (entity == null) {
      return;
    }

    if (entity.duplicationId != 0) {
      PacketContainer newPacket = packet.deepClone();
      newPacket.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, newPacket);
    }

    MovementMetadata movement = user.meta().movement();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);
    entity.immediateEntityPositionSync(packet);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityPositionSync(user, packet);
        entity.clientSynchronized = true;
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver observer = entity.feedbackTracker();
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_IS_NEAR : TRACER_ENTITY_IS_FAR;
      if (distanceBefore < 8 && distanceAfter < 8 && distanceBefore != distanceAfter) {
        options |= distanceAfter < distanceBefore ? TRACER_ENTITY_MOVED_CLOSER : TRACER_ENTITY_MOVED_FARTHER;
      }
      user.tracedPacketTickFeedback(event, task, observer, options);
    } else {
      entity.handleEntityPositionSync(user, packet);
      entity.clientSynchronized = false;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_TELEPORT
    },
    ignoreCancelled = false
  )
  public void receiveEntityTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Entity entity = wrappedEntityByEntityTeleportPacket(event);
    if (entity == null) {
      return;
    }

    if (entity.duplicationId != 0) {
      PacketContainer newPacket = packet.deepClone();
      newPacket.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, newPacket);
    }

    MovementMetadata movement = user.meta().movement();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    entity.immediateEntityTeleport(user, packet);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityTeleport(user, packet);
        entity.clientSynchronized = true;
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver observer = entity.feedbackTracker();
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_IS_NEAR : TRACER_ENTITY_IS_FAR;
      if (distanceBefore < 8 && distanceAfter < 8 && distanceBefore != distanceAfter) {
        options |= distanceAfter < distanceBefore ? TRACER_ENTITY_MOVED_CLOSER : TRACER_ENTITY_MOVED_FARTHER;
      }
      user.tracedPacketTickFeedback(event, task, observer, options);
    } else {
//      if (newTeleports) {
//        entity.handleEntityTeleportModern(packet);
//      } else {
//      }
      entity.handleEntityTeleport(user, packet);
      entity.clientSynchronized = false;
    }
  }

  private Entity wrappedEntityByEntityTeleportPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Integer entityIdBoxed = packet.getIntegers().readSafely(0);
    if (entityIdBoxed == null) {
      return null;
    }
    int entityId = entityIdBoxed;
    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      org.bukkit.entity.Entity bukkitEntity = serverEntityByIdentifier(player, entityId);
      if (bukkitEntity != null) {
        return spawnMobByBukkitEntity(user, bukkitEntity);
      } else {
//      IntaveLogger.logger().info("Unable to create entity (id " + entityId + ")");
//      throw new NullPointerException("entity could not be created");
      }
    }
    return entity;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK, ENTITY_LOOK
    },
    ignoreCancelled = false
  )
  public void receiveEntityMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Integer entityIdBoxed = packet.getIntegers().read(0);
    if (entityIdBoxed == null) {
      return;
    }
    int entityId = entityIdBoxed;
    /* NOTE: An entity can't be created by the entityID when the entity doesn't
     gets teleported afterwards because the Bukkit location isn't specific enough */

    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      return;
    }

    if (entity.duplicationId != 0) {
      PacketContainer newPacket = packet.deepClone();
      newPacket.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, newPacket);
    }

    MovementMetadata movement = user.meta().movement();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);
    entity.immediateEntityMovement(packet);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityMovement(user, packet, true);
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver tracker = entity.feedbackTracker();
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_IS_NEAR : TRACER_ENTITY_IS_FAR;
      if (distanceBefore < 8 && distanceAfter < 8 && distanceBefore != distanceAfter) {
        options |= distanceAfter < distanceBefore ? TRACER_ENTITY_MOVED_CLOSER : TRACER_ENTITY_MOVED_FARTHER;
      }
      user.tracedPacketTickFeedback(event, task, tracker, options);
    } else {
      entity.handleEntityMovement(user, packet, false);
      entity.clientSynchronized = false;
    }
  }

  private final BiConsumer<User, Consumer<EventSink>> sinkCallback = Modules.nayoro().sinkCallback();

  private void nayoroEntitySpawn(User user, Entity entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(user)) {
      return;
    }
    EntitySpawnEvent event = new EntitySpawnEvent(
      entity.entityId(),
      entity.entityName(),
      entity.typeData().size(),
      entity.position.toPosition()
    );
    sinkCallback.accept(user, event::accept);
  }

  private void nayoroEntityDespawn(User user, Entity entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(user)) {
      return;
    }
    EntityRemoveEvent event = new EntityRemoveEvent(entity.entityId());
    sinkCallback.accept(user, event::accept);
  }

  private void nayoroEntityPositionUpdate(Player player, Entity entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(UserRepository.userOf(player))) {
      return;
    }
    Entity.EntityPositionContext position = entity.position;
    Entity.EntityPositionContext lastPosition = entity.lastPosition;
    EntityMoveEvent event = new EntityMoveEvent(
      entity.entityId(),
      position.posX, position.posY, position.posZ,
      lastPosition.posX, lastPosition.posY, lastPosition.posZ,
      0, 0, 0, 0
    );
    sinkCallback.accept(UserRepository.userOf(player), event::accept);
  }

  private Entity spawnMobByBukkitEntity(User user, org.bukkit.entity.Entity bukkitEntity) {
    Location location = bukkitEntity.getLocation();
    int entityID = bukkitEntity.getEntityId();

    long serverPosX;
    long serverPosY;
    long serverPosZ;

    if (NEW_POSITION_PROCESSING_1_9) {
      serverPosX = ClientMath.positionLong(location.getX());
      serverPosY = ClientMath.positionLong(location.getY());
      serverPosZ = ClientMath.positionLong(location.getZ());
    } else {
      serverPosX = ClientMath.floor(location.getX() * 32d);
      serverPosY = ClientMath.floor(location.getY() * 32d);
      serverPosZ = ClientMath.floor(location.getZ() * 32d);
    }

    EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfBukkitEntity(bukkitEntity);

    Entity entity = processEntitySpawn(
      user,
      entityID, entityTypeData,
      serverPosX, serverPosY, serverPosZ,
      bukkitEntity.getType() == EntityType.PLAYER
    );

    if (bukkitEntity instanceof LivingEntity) {
      LivingEntity livingEntity = (LivingEntity) bukkitEntity;
      entity.health = (float) livingEntity.getHealth();
    }

    return entity;
  }

  private Entity processPacketSpawnMob(
    User user, PacketContainer packet,
    EntityTypeData entityTypeData,
    int entityId, boolean isPlayer
  ) {
    if (NEW_POSITION_PROCESSING_1_9) {
      StructureModifier<Double> doubles = packet.getDoubles();
      Double posXBoxed = doubles.readSafely(0);
      Double posYBoxed = doubles.read(1);
      Double posZBoxed = doubles.read(2);

      if (posXBoxed == null || posYBoxed == null || posZBoxed == null) {
        return null;
      }

      double posX = posXBoxed;
      double posY = posYBoxed;
      double posZ = posZBoxed;

      processEntitySpawnNewVersion(
        user, entityTypeData, entityId,
        posX, posY, posZ, isPlayer
      );
    } else {
      // 1.8.x
      Integer serverPosX;
      Integer serverPosY;
      Integer serverPosZ;

      StructureModifier<Integer> integers = packet.getIntegers();
      if (packet.getType() == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
        // dead or living entities
        serverPosX = integers.readSafely(2);
        serverPosY = integers.readSafely(3);
        serverPosZ = integers.readSafely(4);
      } else {
        // players
        serverPosX = integers.readSafely(1);
        serverPosY = integers.readSafely(2);
        serverPosZ = integers.readSafely(3);
      }
      if (serverPosX == null || serverPosY == null || serverPosZ == null) {
        return null;
      }
      return processEntitySpawn(
        user, entityId, entityTypeData,
        serverPosX, serverPosY, serverPosZ,
        isPlayer
      );
    }

    if (IntaveControl.DEBUG_ENTITY_TRACKING) {
      Synchronizer.synchronize(() -> {
        Player target = user.player();
        if (target == null) {
          return;
        }
        HitboxSize size = entityTypeData.size();
        String sizeToString = size == null ? "null" : "w:" + size.width() + " h:" + size.height();
        target.sendMessage(ChatColor.GREEN + entityTypeData.name() + "/" + entityTypeData.typeId() + " as " + entityId + " with " + sizeToString);
      });
    }

    return null;
  }

  private void processEntitySpawnNewVersion(
    User user, EntityTypeData entityTypeData, int entityId,
    double posX, double posY, double posZ,
    boolean isPlayer
  ) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Entity entity = createEntityOf(entityId, entityTypeData, isPlayer);
    entity.serverPosX = ClientMath.positionLong(posX);
    entity.serverPosY = ClientMath.positionLong(posY);
    entity.serverPosZ = ClientMath.positionLong(posZ);
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizeData.enterEntity(entity);
    StaticEntityCollisions.enterEntitySpawn(user, entity);
  }

  private Entity processEntitySpawn(
    User user, int entityId, EntityTypeData entityTypeData,
    long serverPosX, long serverPosY, long serverPosZ,
    boolean player
  ) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    double posX = serverPosX / 32d;
    double posY = serverPosY / 32d;
    double posZ = serverPosZ / 32d;
    Entity entity = createEntityOf(entityId, entityTypeData, player);
    entity.serverPosX = serverPosX;
    entity.serverPosY = serverPosY;
    entity.serverPosZ = serverPosZ;
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizeData.enterEntity(entity);
    StaticEntityCollisions.enterEntitySpawn(user, entity);
    return entity;
  }

  private Entity createEntityOf(
    int entityId,
    EntityTypeData entityTypeData,
    boolean isPlayer
  ) {
    return new Entity(entityId, entityTypeData, isPlayer);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    },
    priority = ListenerPriority.LOWEST
  )
  public void receiveUseEntity(PacketEvent event) {
    User user = UserRepository.userOf(event.getPlayer());
    PacketContainer packet = event.getPacket();
    ConnectionMetadata connection = user.meta().connection();

    Integer entityIdBoxed = packet.getIntegers().readSafely(0);
    if (entityIdBoxed == null) {
      return;
    }
    int entityId = entityIdBoxed;
    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Set<Integer> shouldNotBeAttacked = connection.shouldNotBeAttacked;

    if (duplicationOwners.containsKey(entityId)) {
      int owner = duplicationOwners.get(entityId);
      packet.getIntegers().write(0, owner);
    }

    if (shouldNotBeAttacked.contains(entityId)) {
      connection.markAttackInvalid = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_STATUS
    },
    ignoreCancelled = false
  )
  public void receiveEntityStatus(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    if (entityID == null) {
      return;
    }
    Byte type = packet.getBytes().read(0);
    Entity entity = entityByIdentifier(user, entityID);
    if (entity == null || type != 3) {
      return;
    }
    boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
    if (synchronize) {
      user.tracedTickFeedback(() -> updateDeadState(entity), entity.feedbackTracker());
    } else {
      updateDeadState(entity);
    }
  }

  private void updateDeadState(Entity entity) {
    entity.fakeDead = true;
    entity.health = 0f;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_METADATA
    },
    ignoreCancelled = false
  )
  public void receiveEntityMetadata(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();

    EntityMetadataReader reader = PacketReaders.readerOf(packet);
    int entityId = reader.entityId();

    if (player.getEntityId() == entityId) {
      synchronizePlayerHealth(player, reader);
      reader.release();
      return;
    }

    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      reader.release();
      return;
    }

    if (entity.typeData().isShulker()) {
      MovementMetadata movement = user.meta().movement();
      double distance = entity.position.toPosition().distance(player.getLocation());
      if (distance < 2) {
        Object raw = reader.fetchRaw(17);
        if (raw != null) {
          user.tickFeedback(() -> {
            movement.lowestShulkerY = Math.min(movement.lowestShulkerY, (int) entity.position.posY - 1);
            movement.highestShulkerY = Math.max(movement.highestShulkerY, (int) entity.position.posY + 1);
            movement.shulkerXToleranceRemaining = 20;
            movement.shulkerYToleranceRemaining = 20;
            movement.shulkerZToleranceRemaining = 20;
          });
        }
      }
    }

    ConnectionMetadata connection = user.meta().connection();

    if (connection.duplicatedEntityIds.contains(entityId)) {
      reader.release();
      return;
    }

//    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Map<Integer, ConnectionMetadata.DecoySide> decoySides = connection.decoySides;
//    int targetId = duplicationOwners.get(entityId);

//    if (duplicationOwners.containsKey(entityId)) {
    if (entity.duplicationId != 0) {
      // Rule #3151235: When editing metadata, do a deepClone().
      reader.release();
      event.setPacket(packet = event.getPacket().deepClone());
      reader = PacketReaders.readerOf(packet);

      PacketContainer packetCopy = packet.deepClone();
      ConnectionMetadata.DecoySide decoySide = decoySides.get(entityId);
      modifyWatchablesOf((decoySide == SECOND_IS_DECOY ? packet : packetCopy));
      packetCopy.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, packetCopy);
    }
//    }

    EntityTypeData type = entity.typeData();
    if (type == null) {
      reader.release();
      return;
    }

    boolean isLivingEntity = entity.typeData().isLivingEntity();
    boolean isFireworkRocket = type.name() != null && type.name().contains("Firework");
    int entityTypeId = type.typeId();

    // Firework
    if (isFireworkRocket) {
      handleFirework(player, reader);
    } else if (isLivingEntity) {
      // Health
      processHealthMetadata(player, entity, reader);

      // Entity Size
      EntityTypeData entityTypedata = entityTypeResolver.entityTypeDataOfEntityMetadata(event, entityTypeId, reader);
      if (entityTypedata != null) {
        entity.setTypeData(entityTypedata);
      }
    }
    reader.release();
  }

  private void handleFirework(Player player, EntityMetadataReader reader) {
    if (!MinecraftVersions.VER1_11_0.atOrAbove()) {
      return;
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      processFireworkModern(player, reader);
    } else {
      processFireworkLegacy(player, reader);
    }
  }

  private void processFireworkLegacy(Player player, EntityMetadataReader reader) {
    User user = UserRepository.userOf(player);
    Object value = reader.fetchRaw(7);
    if (!(value instanceof Integer)) {
      return;
    }
    int entityId = (int) value;
    MovementMetadata movement = user.meta().movement();
    InventoryMetadata inventory = user.meta().inventory();
    if (movement.pose() == Pose.FALL_FLYING && entityId == player.getEntityId()) {
      int power = 1;
      ItemStack firework = null;
      // Choose firework item
      if (inventory.heldItemType().name().contains(FIREWORK_IDENTIFIER)) {
        firework = inventory.heldItem();
      } else if (inventory.offhandItemType().name().contains(FIREWORK_IDENTIFIER)) {
        firework = inventory.offhandItem();
      }
      // Only process if firework exists
      if (firework != null) {
        ItemMeta itemMeta = firework.getItemMeta();
        if (itemMeta instanceof FireworkMeta) {
          FireworkMeta fireworkMeta = (FireworkMeta) itemMeta;
          power = Math.max(fireworkMeta.getPower(), 1);
        }
      }
      movement.fireworkRocketsTicks = 0;
      movement.fireworkRocketsPower = power;
    }
  }

  private static final int MODERN_ENTITY_ID_ACCESS_INDEX = MinecraftVersions.VER1_17_0.atOrAbove() ? 9 : 8;

  private void processFireworkModern(Player player, EntityMetadataReader reader) {
    User user = UserRepository.userOf(player);
    Object value = reader.fetchRaw(MODERN_ENTITY_ID_ACCESS_INDEX);
    if (!(value instanceof OptionalInt)) {
      return;
    }
    OptionalInt optionalId = (OptionalInt) value;
    if (!optionalId.isPresent()) {
      return;
    }
    int entityId = optionalId.getAsInt();
    MovementMetadata movement = user.meta().movement();
    InventoryMetadata inventory = user.meta().inventory();
    if ((movement.pose() == Pose.FALL_FLYING || movement.elytraFlying) && entityId == player.getEntityId()) {
      int power = 1;
      ItemStack firework = null;
      // Choose firework item
      if (inventory.heldItemType().name().contains(FIREWORK_IDENTIFIER)) {
        firework = inventory.heldItem();
      } else if (inventory.offhandItemType().name().contains(FIREWORK_IDENTIFIER)) {
        firework = inventory.offhandItem();
      }
      // Only process if firework exists
      if (firework != null) {
        ItemMeta itemMeta = firework.getItemMeta();
        if (itemMeta instanceof FireworkMeta) {
          FireworkMeta fireworkMeta = (FireworkMeta) itemMeta;
          power = Math.max(fireworkMeta.getPower(), 1);
        }
      }
      movement.fireworkRocketsTicks = 0;
      movement.fireworkRocketsPower = power;
    }
  }

  private static final String FIREWORK_IDENTIFIER = "FIREWORK";

  private void processHealthMetadata(
    Player player, Entity entity,
    EntityMetadataReader reader
  ) {
    Object raw = reader.fetchRaw(HEALTH_INDEX);
    if (raw == null) {
      return;
    }
    Float health = readHealthFromRaw(raw);
    if (health != null) {
      boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
      if (synchronize) {
        User user = UserRepository.userOf(player);
        user.tracedTickFeedback(() -> updateHealthState(entity, health), entity.feedbackTracker());
      } else {
        updateHealthState(entity, health);
      }
    }
  }

  private void synchronizePlayerHealth(
    Player player, EntityMetadataReader reader
  ) {
    Object raw = reader.fetchRaw(HEALTH_INDEX);
    if (raw == null) {
      return;
    }
    Float health = readHealthFromRaw(raw);
    if (health != null) {
      User user = UserRepository.userOf(player);
      AbilityMetadata abilityData = user.meta().abilities();
      abilityData.unsynchronizedHealth = health;
      user.tickFeedback(() -> {
        abilityData.health = health;
        abilityData.ticksToLastHealthUpdate = 0;
      });
    }
  }

  private static final boolean HEALTH_PROCESSING_1_10 = MinecraftVersions.VER1_10_0.atOrAbove();
  private static final boolean HEALTH_PROCESSING_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();

  private static final int HEALTH_INDEX = resolveRequiredIndex();

  private static int resolveRequiredIndex() {
    int requiredIndex;
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      requiredIndex = 9;
    } else if (HEALTH_PROCESSING_1_14) {
      requiredIndex = 8;
    } else if (HEALTH_PROCESSING_1_10) {
      requiredIndex = 7;
    } else {
      requiredIndex = 6;
    }
    return requiredIndex;
  }

  private Float readHealthFromRaw(Object rawValue) {
    if (rawValue instanceof OptionalInt) {
      OptionalInt optionalInt = (OptionalInt) rawValue;
      if (!optionalInt.isPresent()) {
        return null;
      }
      rawValue = optionalInt.getAsInt();
    }
    return ((Number) rawValue).floatValue();
  }

  private void updateHealthState(Entity entity, float health) {
    entity.health = health;
  }

//  private final static Map<World, EquivalentConverter<Entity>> ENTITY_CONVERTER = GarbageCollector.watch(new HashMap<>());

  @Nullable
  public static org.bukkit.entity.Entity serverEntityByIdentifier(Player player, int entityID) {
    if (entityID < 0) {
      return null;
    }
    return EntityLookup.findEntity(player.getWorld(), entityID);
  }

  @Nullable
  public static Entity entityByIdentifier(User user, int entityID) {
    return user.meta().connection().entityBy(entityID);
  }
}