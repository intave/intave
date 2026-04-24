package de.jpx3.intave.module.tracker.entity;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
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
    this.entityTypeResolver = new EntityTypeResolver();
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
  public void sendAttachEntityPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
      WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers((PacketSendEvent) event);
      //1.9+ servers
      int vehicleId = packet.getEntityId();
      Entity vehicle = UserRepository.userOf(player).meta().connection().entityBy(vehicleId);
      if (vehicle == null) {
        IntaveLogger.logger().error("Vehicle entity not found in mount request: " + vehicleId);
        detachEntity(user, vehicleId, -1);
        return;
      }
      int[] newPassengers = packet.getPassengers();
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
      WrapperPlayServerAttachEntity packet = new WrapperPlayServerAttachEntity((PacketSendEvent) event);
      // 1.8 servers
      if (!packet.isLeash()) {
        int passengerId = packet.getAttachedId();
        int vehicleId = packet.getHoldingId();
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
  public void sendEntitySpawn(ProtocolPacketEvent event) {
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

    Integer entityIdBoxed = spawnedEntityId(event);
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
    boolean isLivingEntity = (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY ||
      event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) && entity.typeData().isLivingEntity();
    boolean isPlayer = event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER;
    boolean hasRedTrustfactor = !user.trustFactor().atLeast(TrustFactor.ORANGE);
    boolean oneInFourChance = ThreadLocalRandom.current().nextInt(4) == 0;

    if (/*isLivingEntity && isPlayer *//*&& hasRedTrustfactor*//* && oneInFourChance*/ false) {
      int newId = IdentifierReserve.acquireNew();
      duplicatedEntityIds.add(newId);
      duplicationOwners.put(newId, entityId);

      boolean makeOwnerInvisible = ThreadLocalRandom.current().nextBoolean();
      //is this correct? - yes it is
      connection.shouldNotBeAttacked.add(entityId);
      connection.decoySides.put(entityId, makeOwnerInvisible ? SECOND_IS_DECOY : FIRST_IS_DECOY);
      entity.duplicationId = newId;
    }
//    Modules.feedback().singleSynchronize(event.getPlayer(), event, this::processEntitySpawn, APPEND_ON_OVERFLOW);
  }

  private Integer spawnedEntityId(ProtocolPacketEvent event) {
    PacketSendEvent sendEvent = (PacketSendEvent) event;
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
      return new WrapperPlayServerSpawnEntity(sendEvent).getEntityId();
    } else if (packetType == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
      return new WrapperPlayServerSpawnLivingEntity(sendEvent).getEntityId();
    } else if (packetType == PacketType.Play.Server.SPAWN_PLAYER) {
      return new WrapperPlayServerSpawnPlayer(sendEvent).getEntityId();
    }
    return null;
  }

  private Entity processEntitySpawn(Player player, ProtocolPacketEvent event) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    PacketTypeCommon packetType = event.getPacketType();
    EntityTypeData typeData;
    boolean entityIsPlayer = false;
    PacketSendEvent sendEvent = (PacketSendEvent) event;
    int entityId;
    Vector3d position;
    if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
      WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(sendEvent);
      entityId = packet.getEntityId();
      position = packet.getPosition();
      typeData = entityTypeResolver.entityTypeDataOfSpawnEntity(player, packet);
    } else if (packetType == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
      WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(sendEvent);
      entityId = packet.getEntityId();
      position = packet.getPosition();
      typeData = entityTypeResolver.entityTypeDataOfLivingEntity(player, packet);
    } else {
      WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(sendEvent);
      entityId = packet.getEntityId();
      position = packet.getPosition();
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
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntavePlugin.singletonInstance().logger().error("Cannot resolve entityType: " + entityId);
      }
      return null;
    }
    if ("ServerPlayer".equalsIgnoreCase(typeData.name())) {
      entityIsPlayer = true;
    }
    return processPacketSpawnMob(user, typeData, entityId, entityIsPlayer, position);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_DESTROY
    },
    ignoreCancelled = false
  )
  public void receiveEntityDestroy(Player player, WrapperPlayServerDestroyEntities packet) {
    for (int entityId : packet.getEntityIds()) {
      enterEntityDestroy(player, entityId);
    }
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
  public void receiveMovement(ProtocolPacketEvent event) {
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
  public void on(ProtocolPacketEvent event) {
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
  public void receivePositionSync(ProtocolPacketEvent event, WrapperPlayServerEntityPositionSync packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    Entity entity = wrappedEntityByEntityId(user, player, packet.getId());
    if (entity == null) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    Vector3d position = packet.getValues().getPosition();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);
    entity.immediateEntityPositionSync(position);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityPositionSync(user, position);
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
      entity.handleEntityPositionSync(user, position);
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
  public void receiveEntityTeleport(ProtocolPacketEvent event, WrapperPlayServerEntityTeleport packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    Entity entity = wrappedEntityByEntityId(user, player, packet.getEntityId());
    if (entity == null) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    Vector3d position = packet.getPosition();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    entity.immediateEntityTeleport(user, position);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityTeleport(user, position);
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
      entity.handleEntityTeleport(user, position);
      entity.clientSynchronized = false;
    }
  }

  private Entity wrappedEntityByEntityId(User user, Player player, int entityId) {
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
  public void receiveEntityMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    EntityMovePacket movePacket = entityMovePacket(event);
    int entityId = movePacket.entityId;
    /* NOTE: An entity can't be created by the entityID when the entity doesn't
     gets teleported afterwards because the Bukkit location isn't specific enough */

    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);
    entity.immediateEntityMovement(movePacket.deltaX, movePacket.deltaY, movePacket.deltaZ);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityMovement(user, movePacket.deltaX, movePacket.deltaY, movePacket.deltaZ, true);
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver tracker = entity.feedbackTracker();
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_IS_NEAR : TRACER_ENTITY_IS_FAR;
      if (distanceBefore < 8 && distanceAfter < 8 && distanceBefore != distanceAfter) {
        options |= distanceAfter < distanceBefore ? TRACER_ENTITY_MOVED_CLOSER : TRACER_ENTITY_MOVED_FARTHER;
      }
      user.tracedPacketTickFeedback(event, task, tracker, options);
    } else {
      entity.handleEntityMovement(user, movePacket.deltaX, movePacket.deltaY, movePacket.deltaZ, false);
      entity.clientSynchronized = false;
    }
  }

  private EntityMovePacket entityMovePacket(ProtocolPacketEvent event) {
    PacketSendEvent sendEvent = (PacketSendEvent) event;
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
      WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(sendEvent);
      return new EntityMovePacket(packet.getEntityId(), packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
    } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
      WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(sendEvent);
      return new EntityMovePacket(packet.getEntityId(), packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
    } else {
      WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(sendEvent);
      return new EntityMovePacket(packet.getEntityId(), 0.0, 0.0, 0.0);
    }
  }

  private static final class EntityMovePacket {
    private final int entityId;
    private final double deltaX;
    private final double deltaY;
    private final double deltaZ;

    private EntityMovePacket(int entityId, double deltaX, double deltaY, double deltaZ) {
      this.entityId = entityId;
      this.deltaX = deltaX;
      this.deltaY = deltaY;
      this.deltaZ = deltaZ;
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
    User user,
    EntityTypeData entityTypeData,
    int entityId,
    boolean isPlayer,
    Vector3d position
  ) {
    Entity entity;
    if (NEW_POSITION_PROCESSING_1_9) {
      processEntitySpawnNewVersion(
        user, entityTypeData, entityId,
        position.getX(), position.getY(), position.getZ(), isPlayer
      );
      entity = user.meta().connection().entityBy(entityId);
    } else {
      entity = processEntitySpawn(
        user, entityId, entityTypeData,
        ClientMath.floor(position.getX() * 32d),
        ClientMath.floor(position.getY() * 32d),
        ClientMath.floor(position.getZ() * 32d),
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
    return entity;
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
  public void receiveUseEntity(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();

    int entityId = packet.getEntityId();
    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Set<Integer> shouldNotBeAttacked = connection.shouldNotBeAttacked;

    if (duplicationOwners.containsKey(entityId)) {
      int owner = duplicationOwners.get(entityId);
      packet.setEntityId(owner);
      event.markForReEncode(true);
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
  public void receiveEntityStatus(ProtocolPacketEvent event, WrapperPlayServerEntityStatus packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    int entityID = packet.getEntityId();
    int type = packet.getStatus();
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
  public void receiveEntityMetadata(ProtocolPacketEvent event, WrapperPlayServerEntityMetadata packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    List<EntityData<?>> metadata = packet.getEntityMetadata();
    int entityId = packet.getEntityId();

    if (player.getEntityId() == entityId) {
      synchronizePlayerHealth(player, metadata);
      return;
    }

    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      return;
    }

    if (entity.typeData().isShulker()) {
      MovementMetadata movement = user.meta().movement();
      double distance = entity.position.toPosition().distance(player.getLocation());
      if (distance < 2) {
        Object raw = fetchRaw(metadata, 17);
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
      return;
    }

//    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Map<Integer, ConnectionMetadata.DecoySide> decoySides = connection.decoySides;
//    int targetId = duplicationOwners.get(entityId);

//    if (duplicationOwners.containsKey(entityId)) {
    if (entity.duplicationId != 0) {
    }
//    }

    EntityTypeData type = entity.typeData();
    if (type == null) {
      return;
    }

    boolean isLivingEntity = entity.typeData().isLivingEntity();
    boolean isFireworkRocket = type.name() != null && type.name().contains("Firework");
    int entityTypeId = type.typeId();

    // Firework
    if (isFireworkRocket) {
      handleFirework(player, metadata);
    } else if (isLivingEntity) {
      // Health
      processHealthMetadata(player, entity, metadata);

      // Entity Size
      EntityTypeData entityTypedata = entityTypeResolver.entityTypeDataOfEntityMetadata(player, entityTypeId, metadata);
      if (entityTypedata != null) {
        entity.setTypeData(entityTypedata);
      }
    }
  }

  private void handleFirework(Player player, List<EntityData<?>> metadata) {
    if (!MinecraftVersions.VER1_11_0.atOrAbove()) {
      return;
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      processFireworkModern(player, metadata);
    } else {
      processFireworkLegacy(player, metadata);
    }
  }

  private void processFireworkLegacy(Player player, List<EntityData<?>> metadata) {
    User user = UserRepository.userOf(player);
    Object value = fetchRaw(metadata, 7);
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

  private void processFireworkModern(Player player, List<EntityData<?>> metadata) {
    User user = UserRepository.userOf(player);
    Object value = fetchRaw(metadata, MODERN_ENTITY_ID_ACCESS_INDEX);
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
    List<EntityData<?>> metadata
  ) {
    Object raw = fetchRaw(metadata, HEALTH_INDEX);
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
    Player player, List<EntityData<?>> metadata
  ) {
    Object raw = fetchRaw(metadata, HEALTH_INDEX);
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

  private Object fetchRaw(List<EntityData<?>> metadata, int index) {
    if (metadata == null) {
      return null;
    }
    for (EntityData<?> entry : metadata) {
      if (entry.getIndex() == index) {
        return entry.getValue();
      }
    }
    return null;
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
