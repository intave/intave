/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.tracker.entity;

import ac.intave.samples.event.EntityMoveEvent;
import ac.intave.samples.event.EntityRemoveEvent;
import ac.intave.samples.event.EntitySpawnEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.collision.entity.StaticEntityCollisions;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.pe.PacketEventWrapper;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.SampleTypes;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityIterable;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.view.EntityAttachView;
import de.jpx3.intave.packet.view.EntityDestroyView;
import de.jpx3.intave.packet.view.EntityInteractIdView;
import de.jpx3.intave.packet.view.EntityMetadataView;
import de.jpx3.intave.packet.view.EntityRelativeMoveView;
import de.jpx3.intave.packet.view.EntityStatusView;
import de.jpx3.intave.packet.view.FeedbackHandle;
import de.jpx3.intave.packet.view.PacketEventsEntityAttachView;
import de.jpx3.intave.packet.view.PacketEventsEntityDestroyView;
import de.jpx3.intave.packet.view.PacketEventsEntityInteractIdView;
import de.jpx3.intave.packet.view.PacketEventsEntityMetadataView;
import de.jpx3.intave.packet.view.PacketEventsEntityRelativeMoveView;
import de.jpx3.intave.packet.view.PacketEventsEntityStatusView;
import de.jpx3.intave.packet.view.PacketEventsFeedbackHandle;
import de.jpx3.intave.packet.view.ProtocolLibEntityAttachView;
import de.jpx3.intave.packet.view.ProtocolLibEntityDestroyView;
import de.jpx3.intave.packet.view.ProtocolLibEntityInteractIdView;
import de.jpx3.intave.packet.view.ProtocolLibEntityMetadataView;
import de.jpx3.intave.packet.view.ProtocolLibEntityRelativeMoveView;
import de.jpx3.intave.packet.view.ProtocolLibEntityStatusView;
import de.jpx3.intave.packet.view.ProtocolLibFeedbackHandle;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.IdentifierReserve;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.Particles;
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

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.feedback.FeedbackOptions.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
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
  private final boolean NEW_POSITION_PROCESSING_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();

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
    handleAttachEntity(new ProtocolLibEntityAttachView(event));
  }

  /**
   * PacketEvents entry point for {@link #sendAttachEntityPacket(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      MOUNT, ATTACH_ENTITY
    },
    ignoreCancelled = false
  )
  public void sendAttachEntityPacket(PacketSendEvent event) {
    PacketEventsEntityAttachView view = PacketEventsEntityAttachView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleAttachEntity(view);
  }

  /** Engine independent vehicle and passenger tracking; see {@link EntityAttachView}. */
  private void handleAttachEntity(EntityAttachView view) {
    User user = UserRepository.userOf(view.player());
    if (view.isMount()) {
      //1.9+ servers
      handleMount(user, view);
    } else if (view.isLegacyAttach()) {
      // 1.8 servers
      handleLegacyAttach(user, view);
    }
    view.release();
  }

  private void handleMount(User user, EntityAttachView view) {
    int vehicleId = view.vehicleId();
    Entity vehicle = user.meta().connection().entityBy(vehicleId);
    if (vehicle == null) {
//        IntaveLogger.logger().error("Vehicle entity not found in mount request: " + vehicleId);
      detachEntity(user, vehicleId, -1);
      return;
    }
    int[] newPassengers = view.passengers();
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
  }

  private void handleLegacyAttach(User user, EntityAttachView view) {
    if (!view.isLeash()) {
      int passengerId = view.passengerId();
      int vehicleId = view.vehicleId();
      if (vehicleId == -1) {
        detachEntity(user, -1, passengerId);
      } else {
        attachEntity(user, vehicleId, passengerId);
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

  /**
   * ProtocolLib only; there is deliberately no PacketEvents twin.
   * <p>
   * Spawn packets are the one place where Intave has to name an entity type it has never seen as a
   * Bukkit entity, and {@link EntityTypeResolver} does that through server internals rather than
   * through the wire payload: {@code entityTypeDataOfLivingEntity} reads the packet's
   * {@code WrappedDataWatcher}, pulls the live NMS entity out of it by a version specific field
   * name and measures its hitbox off the NMS class, and {@code entityTypeDataOfDeadEntity} falls
   * back to ProtocolLib's {@code getEntityTypeModifier} and to {@code HitboxSizeAccess}
   * measurements of the resolved NMS class. PacketEvents decodes the metadata into its own
   * {@code EntityData} values and never exposes the server side data watcher or the entity behind
   * it, so that resolution has no equivalent - and a spawn whose type resolves wrong hands every
   * downstream reach and hitbox check a wrong bounding box.
   */
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
  public void receiveEntityDestroy(PacketEvent event, Player player, EntityIterable iterable) {
    handleEntityDestroy(
      new ProtocolLibEntityDestroyView(player, iterable),
      ProtocolLibFeedbackHandle.of(event)
    );
  }

  /**
   * PacketEvents entry point for {@link #receiveEntityDestroy(PacketEvent, Player, EntityIterable)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_DESTROY
    },
    ignoreCancelled = false
  )
  public void receiveEntityDestroy(PacketSendEvent event) {
    PacketEventsEntityDestroyView view = PacketEventsEntityDestroyView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleEntityDestroy(view, PacketEventsFeedbackHandle.of(event));
  }

  /** Engine independent entity despawn tracking; see {@link EntityDestroyView}. */
  private void handleEntityDestroy(EntityDestroyView view, FeedbackHandle handle) {
    Player player = view.player();
    view.forEachEntityId(entityId ->
      enterEntityDestroy(handle, player, entityId)
    );
    view.release();
  }

  private void enterEntityDestroy(FeedbackHandle handle, Player player, int entityID) {
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
    processEntityDestroy(handle, player, entityID);
  }

  private void processEntityDestroy(FeedbackHandle handle, Player player, int entityId) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ConnectionMetadata connection = user.meta().connection();
    MovementMetadata movementData = user.meta().movement();

    Entity entity = connection.entityBy(entityId);//synchronizedEntityMap.get(entityId);
    if (entity != null && movementData.ridingEntity() == entity) {
      movementData.dismountRidingEntity("Entity Destroy");
    }
    if (entity != null && isFireworkRocket(entity.typeData())
      && movementData.beginFireworkRocketDetachment(entityId)) {
      user.packetTickFeedback(handle, () ->
        movementData.confirmFireworkRocketDetachment(entityId)
      );
    }

    if (entity != null && entity.duplicationId != 0) {
      connection.duplicatedEntityIds.remove(entity.duplicationId);
      connection.shouldNotBeAttacked.remove(connection.duplicationOwners.remove(entity.duplicationId));
      PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
      packet.getIntegerArrays().write(0, new int[]{entity.duplicationId});
      PacketSender.sendServerPacket(player, packet);
    }

    connection.markForDeletion(entityId);

    Synchronizer.synchronize(user, () -> {
      user.tickFeedback(() -> {
        Synchronizer.synchronize(user, () -> {
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
      Synchronizer.synchronize(user, () -> {
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
      POSITION, POSITION_LOOK, LOOK, FLYING, STEER_VEHICLE, CLIENT_TICK_END
    }
  )
  public void receiveMovement(PacketEvent event) {
    handleMovement(event.getPlayer(), PacketTypes.isClientEndTick(event.getPacketType()));
  }

  /**
   * PacketEvents entry point for {@link #receiveMovement(PacketEvent)}.
   * <p>
   * This subscription reads no packet payload at all - only who sent it and whether it was the
   * client tick end marker - so it needs no packet view; the wrapper supplies both directly.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, STEER_VEHICLE, CLIENT_TICK_END
    }
  )
  public void receiveMovement(PacketEventWrapper event) {
    Player player = event.player();
    if (player == null) {
      return;
    }
    handleMovement(player, isClientEndTick(event.packetType()));
  }

  /**
   * PacketEvents counterpart of {@link PacketTypes#isClientEndTick}.
   * <p>
   * Resolved through {@link PacketEventsIdMapper} rather than by referencing the constant, because
   * CLIENT_TICK_END does not exist on older PacketEvents releases; an unresolved packet yields an
   * empty list here, which simply never matches.
   */
  private static boolean isClientEndTick(PacketTypeCommon packetType) {
    return packetType != null && CLIENT_TICK_END_TYPES.contains(packetType);
  }

  private static final List<PacketTypeCommon> CLIENT_TICK_END_TYPES =
    PacketEventsIdMapper.typesOf(CLIENT_TICK_END);

  /** Engine independent per packet entity tick. */
  private void handleMovement(Player player, boolean isClientTickEnd) {
    User user = UserRepository.userOf(player);
    if (user.meta().protocol().sendsClientTickEnd() && !isClientTickEnd) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movement = user.meta().movement();
    if (movement.ticksPast(TELEPORT) == 0) {
      return;
    }
    for (Entity entity : synchronizeData.entities()) {
      int ticksAfterPositionChange = entity.position.newPosRotationIncrements;
      entity.onUpdate();
      if (entity.tracingEnabled() && ticksAfterPositionChange > 0) {
        nayoroEntityPositionUpdate(player, entity);
      }

      if (user.receives(MessageChannel.DEBUG_HITBOXES)) {
        for (Position vertex : entity.boundingBox().vertices()) {
          Particles.spawnVillagerHappyParticleAt(user, vertex);
        }
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
        movement.positionX = movement.verifiedLastPositionX = movement.lastPositionX = originalX;
        movement.positionY = movement.verifiedLastPositionY = movement.lastPositionY = originalY;
        movement.positionZ = movement.verifiedLastPositionZ = movement.lastPositionZ = originalZ;
        movement.verifiedPositionOrigin = "Riding pos sync (1.8)";
        movement.setBaseMotion(Motion.newEmpty());
        movement.clearPostTickMotionCandidates();
      }
    }
  }

  /**
   * ProtocolLib only; there is deliberately no PacketEvents twin.
   * <p>
   * {@code ENTITY_POSITION_SYNC} is the 1.21.2 replacement for the absolute entity teleport and
   * PacketEvents 2.4.0 predates it: the bundled API declares no
   * {@code PacketType.Play.Server.ENTITY_POSITION_SYNC} and ships no wrapper for it, so
   * {@link de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper} resolves the id through its
   * fallback name and would hand a twin plain {@code ENTITY_TELEPORT} packets instead. On top of
   * that, {@link Entity#immediateEntityPositionSync} and
   * {@link Entity#handleEntityPositionSync(User, com.comphenix.protocol.events.PacketContainer)}
   * decode through {@code PositionMoveRotation.firstFrom}, which converts the NMS
   * {@code PositionMoveRotation} record out of a {@code PacketContainer} and has no PacketEvents
   * counterpart. Feeding entity positions from the wrong packet is how a tracked entity ends up
   * somewhere the client never saw it, so this one stays on the engine that can read it.
   */
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

  /**
   * ProtocolLib only; there is deliberately no PacketEvents twin.
   * <p>
   * From 1.21.3 the teleport payload is a {@code PositionMoveRotation} plus a set of relative
   * flags, and {@link Entity#immediateEntityTeleport} and {@link Entity#handleEntityTeleport} read
   * it through {@code PositionMoveRotation.firstFrom} and {@code Relative.flagsFrom}, both of which
   * convert NMS values straight out of a {@code PacketContainer}. PacketEvents 2.4.0's
   * {@code WrapperPlayServerEntityTeleport} predates that change: it decodes an absolute
   * {@code Vector3d} and carries no relative flag set at all. A twin could therefore only be
   * correct below 1.21.2, and on newer servers it would read every relative teleport as an absolute
   * one - which silently moves the tracked entity to the delta itself. Since a wrong entity
   * position feeds the reach and hitbox checks directly, this stays ProtocolLib only rather than
   * being ported with a version hole in it.
   */
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
    handleEntityMovement(
      new ProtocolLibEntityRelativeMoveView(event),
      ProtocolLibFeedbackHandle.of(event),
      event
    );
  }

  /**
   * PacketEvents entry point for {@link #receiveEntityMovement(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK, ENTITY_LOOK
    },
    ignoreCancelled = false
  )
  public void receiveEntityMovement(PacketSendEvent event) {
    PacketEventsEntityRelativeMoveView view = PacketEventsEntityRelativeMoveView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleEntityMovement(view, PacketEventsFeedbackHandle.of(event), null);
  }

  /**
   * Engine independent relative entity movement tracking; see {@link EntityRelativeMoveView}.
   *
   * @param decoySource the ProtocolLib event whose packet a decoy copy is cloned from, or null on a
   * backend that cannot clone and re-send an encoded packet. Only the decoy branch below needs it,
   * and that branch is unreachable unless {@link Entity#duplicationId} is non zero, which is
   * assigned in exactly one place - the currently disabled duplication block of
   * {@link #sendEntitySpawn(PacketEvent)}, a ProtocolLib only subscription. A backend that passes
   * null therefore skips a branch it could never have entered.
   */
  private void handleEntityMovement(
    EntityRelativeMoveView view, FeedbackHandle handle, @Nullable PacketEvent decoySource
  ) {
    Player player = view.player();
    User user = UserRepository.userOf(player);
    Integer entityIdBoxed = view.entityId();
    if (entityIdBoxed == null) {
      view.release();
      return;
    }
    /* NOTE: An entity can't be created by the entityID when the entity doesn't
     gets teleported afterwards because the Bukkit location isn't specific enough */

    Entity entity = entityByIdentifier(user, entityIdBoxed);
    if (entity == null) {
      view.release();
      return;
    }

    if (entity.duplicationId != 0 && decoySource != null) {
      PacketContainer newPacket = decoySource.getPacket().deepClone();
      newPacket.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, newPacket);
    }

    MovementMetadata movement = user.meta().movement();
    double distanceBefore = entity.distanceToPlayerCache > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);
    long dx = view.deltaX();
    long dy = view.deltaY();
    long dz = view.deltaZ();
    double divisor = view.divisor();
    entity.applyImmediateRelativeMove(dx, dy, dz, divisor);
    double distanceAfter = distanceBefore > 8 ? 10 : entity.immediateServerPosition.distance(movement.positionX, movement.positionY, movement.positionZ);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.applyRelativeMove(dx, dy, dz, divisor);
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver tracker = entity.feedbackTracker();
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_IS_NEAR : TRACER_ENTITY_IS_FAR;
      if (distanceBefore < 8 && distanceAfter < 8 && distanceBefore != distanceAfter) {
        options |= distanceAfter < distanceBefore ? TRACER_ENTITY_MOVED_CLOSER : TRACER_ENTITY_MOVED_FARTHER;
      }
      user.tracedPacketTickFeedback(handle, task, tracker, options);
    } else {
      entity.applyRelativeMove(dx, dy, dz, divisor);
      entity.clientSynchronized = false;
    }
    view.release();
  }

  private void nayoroEntitySpawn(User user, Entity entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(user)) {
      return;
    }
    EntitySpawnEvent event = new EntitySpawnEvent(
      entity.entityId(),
      entity.entityName(),
      SampleTypes.hitboxSize(entity.typeData().size()),
      SampleTypes.position(entity.position.toPosition())
    );
    nayoro.emit(user, event);
  }

  private void nayoroEntityDespawn(User user, Entity entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(user)) {
      return;
    }
    EntityRemoveEvent event = new EntityRemoveEvent(entity.entityId());
    nayoro.emit(user, event);
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
      SampleTypes.position(position.toPosition()), SampleTypes.position(lastPosition.toPosition()),
      new ac.intave.samples.share.Rotation(0, 0),
      new ac.intave.samples.share.Rotation(0, 0)
    );
    nayoro.emit(UserRepository.userOf(player), event);
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
      Synchronizer.synchronize(user, () -> {
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
      ATTACK_ENTITY, USE_ENTITY
    },
    priority = ListenerPriority.LOWEST
  )
  public void receiveUseEntity(PacketEvent event) {
    handleUseEntity(new ProtocolLibEntityInteractIdView(event));
  }

  /**
   * PacketEvents entry point for {@link #receiveUseEntity(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    },
    priority = ListenerPriority.LOWEST
  )
  public void receiveUseEntity(PacketReceiveEvent event) {
    PacketEventsEntityInteractIdView view = PacketEventsEntityInteractIdView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleUseEntity(view);
  }

  /** Engine independent decoy redirect; see {@link EntityInteractIdView}. */
  private void handleUseEntity(EntityInteractIdView view) {
    User user = UserRepository.userOf(view.player());
    ConnectionMetadata connection = user.meta().connection();

    Integer entityIdBoxed = view.entityId();
    if (entityIdBoxed == null) {
      return;
    }
    int entityId = entityIdBoxed;
    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Set<Integer> shouldNotBeAttacked = connection.shouldNotBeAttacked;

    if (duplicationOwners.containsKey(entityId)) {
      int owner = duplicationOwners.get(entityId);
      view.setEntityId(owner);
    }

    if (shouldNotBeAttacked.contains(entityId)) {
      connection.markAttackInvalid = true;
    }
    view.release();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_STATUS
    },
    ignoreCancelled = false
  )
  public void receiveEntityStatus(PacketEvent event) {
    handleEntityStatus(new ProtocolLibEntityStatusView(event));
  }

  /**
   * PacketEvents entry point for {@link #receiveEntityStatus(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_STATUS
    },
    ignoreCancelled = false
  )
  public void receiveEntityStatus(PacketSendEvent event) {
    PacketEventsEntityStatusView view = PacketEventsEntityStatusView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleEntityStatus(view);
  }

  /** Engine independent death animation tracking; see {@link EntityStatusView}. */
  private void handleEntityStatus(EntityStatusView view) {
    User user = UserRepository.userOf(view.player());
    if (!user.hasPlayer()) {
      return;
    }
    Integer entityID = view.entityId();
    if (entityID == null) {
      return;
    }
    Byte type = view.status();
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
    view.release();
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
    handleEntityMetadata(
      new ProtocolLibEntityMetadataView(event),
      ProtocolLibFeedbackHandle.of(event),
      event
    );
  }

  /**
   * PacketEvents entry point for {@link #receiveEntityMetadata(PacketEvent)}.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_METADATA
    },
    ignoreCancelled = false
  )
  public void receiveEntityMetadata(PacketSendEvent event) {
    PacketEventsEntityMetadataView view = PacketEventsEntityMetadataView.of(event);
    if (view == null) {
      return;
    }
    if (view.player() == null) {
      return;
    }
    handleEntityMetadata(view, PacketEventsFeedbackHandle.of(event), null);
  }

  /**
   * Engine independent entity metadata tracking; see {@link EntityMetadataView}.
   *
   * @param decoySource the ProtocolLib event whose packet the decoy branch replaces and clones, or
   * null on a backend that cannot clone and re-send an encoded packet. See
   * {@link #handleEntityMovement(EntityRelativeMoveView, FeedbackHandle, PacketEvent)} for why
   * skipping that branch cannot change behaviour: {@link Entity#duplicationId} is only ever
   * assigned by the disabled duplication block of the ProtocolLib only
   * {@link #sendEntitySpawn(PacketEvent)}.
   */
  private void handleEntityMetadata(
    EntityMetadataView view, FeedbackHandle handle, @Nullable PacketEvent decoySource
  ) {
    Player player = view.player();
    User user = UserRepository.userOf(player);

    int entityId = view.entityId();

    if (player.getEntityId() == entityId) {
      synchronizePlayerHealth(player, view);
      view.release();
      return;
    }

    Entity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      view.release();
      return;
    }

    if (entity.typeData().isShulker()) {
      MovementMetadata movement = user.meta().movement();
      double distance = entity.position.toPosition().distance(player.getLocation());
      if (distance < 2) {
        Object raw = view.fetchRaw(17);
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
      view.release();
      return;
    }

//    Map<Integer, Integer> duplicationOwners = connection.duplicationOwners;
    Map<Integer, ConnectionMetadata.DecoySide> decoySides = connection.decoySides;
//    int targetId = duplicationOwners.get(entityId);

//    if (duplicationOwners.containsKey(entityId)) {
    if (entity.duplicationId != 0 && decoySource != null) {
      // Rule #3151235: When editing metadata, do a deepClone().
      view.release();
      PacketContainer packet = decoySource.getPacket().deepClone();
      decoySource.setPacket(packet);
      view = new ProtocolLibEntityMetadataView(decoySource);

      PacketContainer packetCopy = packet.deepClone();
      ConnectionMetadata.DecoySide decoySide = decoySides.get(entityId);
      modifyWatchablesOf((decoySide == SECOND_IS_DECOY ? packet : packetCopy));
      packetCopy.getIntegers().write(0, entity.duplicationId);
      PacketSender.sendServerPacket(player, packetCopy);
    }
//    }

    EntityTypeData type = entity.typeData();
    if (type == null) {
      view.release();
      return;
    }

    boolean isLivingEntity = entity.typeData().isLivingEntity();
    boolean isFireworkRocket = isFireworkRocket(type);
    int entityTypeId = type.typeId();

    // Firework
    if (isFireworkRocket) {
      handleFirework(handle, player, entityId, view);
    } else if (isLivingEntity) {
      // Health
      processHealthMetadata(player, entity, view);

      // Entity Size
      EntityTypeData entityTypedata = entityTypeResolver.entityTypeDataOfEntityMetadata(view, entityTypeId);
      if (entityTypedata != null) {
        entity.setTypeData(entityTypedata);
      }
    }
    view.release();
  }

  private static boolean isFireworkRocket(EntityTypeData type) {
    return type != null && type.name() != null && type.name().contains("Firework");
  }

  private void handleFirework(FeedbackHandle handle, Player player, int fireworkEntityId, EntityMetadataView view) {
    if (!MinecraftVersions.VER1_11_0.atOrAbove()) {
      return;
    }
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      processFireworkModern(handle, player, fireworkEntityId, view);
    } else {
      processFireworkLegacy(handle, player, fireworkEntityId, view);
    }
  }

  private void processFireworkLegacy(
    FeedbackHandle handle, Player player,
    int fireworkEntityId, EntityMetadataView view
  ) {
    User user = UserRepository.userOf(player);
    Object value = view.fetchRaw(7);
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
      movement.fireworkRocketsPower = power;
      synchronizeFireworkAttachment(handle, user, movement, fireworkEntityId);
    }
  }

  private static final int MODERN_ENTITY_ID_ACCESS_INDEX = MinecraftVersions.VER1_17_0.atOrAbove() ? 9 : 8;

  private void processFireworkModern(
    FeedbackHandle handle, Player player,
    int fireworkEntityId, EntityMetadataView view
  ) {
    User user = UserRepository.userOf(player);
    Object value = view.fetchRaw(MODERN_ENTITY_ID_ACCESS_INDEX);
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
    if ((movement.pose() == Pose.FALL_FLYING || movement.gliding) && entityId == player.getEntityId()) {
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
      movement.fireworkRocketsPower = power;
      synchronizeFireworkAttachment(handle, user, movement, fireworkEntityId);
    }
  }

  private void synchronizeFireworkAttachment(
    FeedbackHandle handle, User user,
    MovementMetadata movement, int fireworkEntityId
  ) {
    if (movement.beginFireworkRocketAttachment(fireworkEntityId)) {
      user.packetTickFeedback(handle, () ->
        movement.confirmFireworkRocketAttachment(fireworkEntityId)
      );
    }
  }

  private static final String FIREWORK_IDENTIFIER = "FIREWORK";

  private void processHealthMetadata(
    Player player, Entity entity,
    EntityMetadataView view
  ) {
    Object raw = view.fetchRaw(HEALTH_INDEX);
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
    Player player, EntityMetadataView view
  ) {
    Object raw = view.fetchRaw(HEALTH_INDEX);
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

  @Nullable
  public static Entity entityByIdentifier(User user, Integer entityID) {
    return user.meta().connection().entityBy(entityID);
  }
}
