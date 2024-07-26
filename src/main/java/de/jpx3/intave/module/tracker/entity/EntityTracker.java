package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
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

import static de.jpx3.intave.module.feedback.FeedbackOptions.TRACER_ENTITY_FAR;
import static de.jpx3.intave.module.feedback.FeedbackOptions.TRACER_ENTITY_NEAR;
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
//    this.tickedEntitySelector = new PeriodicTickedEntitySelector(50);
  }

  @Override
  public void enable() {
    coverageSelector.enableTask();
//    tickedEntitySelector.enableTask();
  }

  @Override
  public void disable() {
    coverageSelector.disableTask();
//    tickedEntitySelector.disableTask();
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
    if (event.getPacketType() == PacketType.Play.Server.MOUNT) {
      //1.9+ servers
      int[] entityIDs = event.getPacket().getIntegerArrays().read(0);
      int vehicleEntityID = packet.getIntegers().read(0);

      for (int entityID : entityIDs) {
        processAttachEntity(player, entityID, vehicleEntityID);
      }
    } else if (event.getPacketType() == PacketType.Play.Server.ATTACH_ENTITY && !NEW_POSITION_PROCESSING_1_9) {
      // TODO: check if "&& !NEW_POSITION_PROCESSING_1_9" is useless
      // 1.8 servers
      int type = packet.getIntegers().read(0);
      if (type == 0) {
        int entityID = packet.getIntegers().read(1);
        int vehicleEntityID = packet.getIntegers().read(2);
        processAttachEntity(player, entityID, vehicleEntityID);
      }
    }
  }

  private void processAttachEntity(Player player, int entityID, int vehicleEntityID) {
    User user = UserRepository.userOf(player);
    MetadataBundle metadataBundle = user.meta();
    MovementMetadata movementData = metadataBundle.movement();
    ConnectionMetadata connection = metadataBundle.connection();
//    Map<Integer, WrappedEntity> entities = connection.entities();
    Entity sittingEntity = connection.entityBy(entityID);

    if (sittingEntity != null) {
      // Another entity
      if (vehicleEntityID == -1) {
        // when an entity dismounts
        user.tickFeedback(() -> {
          sittingEntity.unmountFromEntity();
          connection.noteDismount(entityID);
        });
      } else {
        // mounts on entity
        Entity sittingOnEntity = connection.entityBy(vehicleEntityID);
        if (sittingOnEntity != null) {
          user.tickFeedback(() -> {
            sittingEntity.mountToEntity(sittingOnEntity);
            connection.noteMount(entityID, vehicleEntityID);
          });
        } else {
          if (IntaveControl.DISABLE_LICENSE_CHECK) {
            IntaveLogger.logger().error(String.format("mounted On Entity with id %d could not be found", vehicleEntityID));
          }
        }
      }
    } else if (entityID == player.getEntityId()) {
      // The Player
      // ID -1 => undo attachment
      tryCreateVehicleEntity(user, vehicleEntityID);
      Entity target = connection.entityBy(vehicleEntityID);
      if (target == null) {
        target = Entity.destroyedEntity();
      }
      Entity finalTarget = target;
      user.tickFeedback(() -> {
        if (movementData.isInVehicle()) {
          movementData.dismountRidingEntity("Override", false);
        }
        if (finalTarget != null && !(finalTarget instanceof Entity.Destroyed)) {
          movementData.setVehicle(finalTarget);
        }
      });
    }
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

    int entityId = event.getPacket().getIntegers().read(0);
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
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntavePlugin.singletonInstance().logger().error("Cannot resolve entityType: " + entityId);
      }
      return null;
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
//    User user = UserRepository.userOf(player);
//    ConnectionMetadata synchronizeData = user.meta().connection();
//    EntityShade entityShade = synchronizeData.entityBy(entityID);

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
      Integer sitter = connection.sittingOn(entityId);
      if (sitter != null) {
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
      POSITION, POSITION_LOOK, LOOK, FLYING
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movementData = user.meta().movement();
    if (movementData.lastTeleport == 0) {
      return;
    }
    for (Entity entity : synchronizeData.entities()) {
      int ticksAfterPositionChange = entity.position.newPosRotationIncrements;
      entity.onUpdate();
      if (entity.tracingEnabled() && ticksAfterPositionChange > 0) {
        nayoroEntityPositionUpdate(player, entity);
      }
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

    entity.immediateEntityTeleport(user, packet);
    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityTeleport(user, packet);
        entity.clientSynchronized = true;
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver observer = entity.feedbackTracker();
////      if (entity.doubleVerification) {
////        FeedbackCallback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
////        Modules.feedback().tracedDoubleSynchronize(player, event, event, task, verificationTask, feedbackTracker, feedbackTracker);
////      } else {
//      Modules.feedback().tracedSingleSynchronize(player, event, task, observer);
      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_NEAR : TRACER_ENTITY_FAR;
      user.tracedPacketTickFeedback(event, task, observer, options);
////      }
    } else {
      entity.handleEntityTeleport(user, packet);
      entity.clientSynchronized = false;
    }
  }

  private Entity wrappedEntityByEntityTeleportPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
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
    int entityId = packet.getIntegers().read(0);
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

    entity.immediateEntityMovement(packet);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      EmptyFeedbackCallback task = () -> {
        entity.verifiedPosition = false;
        entity.handleEntityMovement(packet);
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackObserver tracker = entity.feedbackTracker();
////      if (entity.doubleVerification) {
////        FeedbackCallback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
////        Modules.feedback().tracedDoubleSynchronize(player, event, event, task, verificationTask, tracker, tracker);
////      } else {
//      Modules.feedback().tracedSingleSynchronize(player, event, task, tracker);

      int options = entity.distanceToPlayerCache < 6 ? TRACER_ENTITY_NEAR : TRACER_ENTITY_FAR;
      user.tracedPacketTickFeedback(event, task, tracker, options);
////      }
    } else {
      entity.handleEntityMovement(packet);
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
      double posX = packet.getDoubles().read(0);
      double posY = packet.getDoubles().read(1);
      double posZ = packet.getDoubles().read(2);

      processEntitySpawnNewVersion(
        user, entityTypeData, entityId,
        posX, posY, posZ, isPlayer
      );
    } else {
      // 1.8.x
      Integer serverPosX;
      Integer serverPosY;
      Integer serverPosZ;

      if (packet.getType() == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
        // dead or living entities
        serverPosX = packet.getIntegers().read(2);
        serverPosY = packet.getIntegers().read(3);
        serverPosZ = packet.getIntegers().read(4);
      } else {
        // players
        serverPosX = packet.getIntegers().read(1);
        serverPosY = packet.getIntegers().read(2);
        serverPosZ = packet.getIntegers().read(3);
      }

      return processEntitySpawn(
        user, entityId, entityTypeData,
        serverPosX, serverPosY, serverPosZ,
        isPlayer
      );

//      WrappedEntity wrappedEntity = entityByIdentifier(user, entityID);
//      if (wrappedEntity != null)
//        Bukkit.broadcastMessage("pt " + packetType.name() + " p " + user.isPlayer().getName() + " e " + wrappedEntity.position);
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
//    Map<Integer, WrappedEntity> entities = synchronizeData.entities();
    Entity entity = createEntityOf(entityId, entityTypeData, isPlayer);
    entity.serverPosX = ClientMath.positionLong(posX);
    entity.serverPosY = ClientMath.positionLong(posY);
    entity.serverPosZ = ClientMath.positionLong(posZ);
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
//    entities.put(entityId, entity);
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

    int entityId = packet.getIntegers().read(0);
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
    List<WrappedWatchableObject> watchableObjects = reader.metadataObjects();

    if (player.getEntityId() == entityId) {
      synchronizePlayerHealth(player, watchableObjects);
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
        for (WrappedWatchableObject watchableObject : watchableObjects) {
          if (watchableObject.getIndex() == 17) {
            user.tickFeedback(() -> {
              movement.lowestShulkerY = Math.min(movement.lowestShulkerY,(int) entity.position.posY - 1);
              movement.highestShulkerY = Math.max(movement.highestShulkerY,(int) entity.position.posY + 1);
              movement.shulkerXToleranceRemaining = 20;
              movement.shulkerYToleranceRemaining = 20;
              movement.shulkerZToleranceRemaining = 20;
            });
          }
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
      handleFirework(player, watchableObjects);
    } else if (isLivingEntity && watchableObjects != null) {
      // Health
      processHealthMetadata(player, entity, watchableObjects);

      // Entity Size
      EntityTypeData entityTypedata = entityTypeResolver.entityTypeDataOfEntityMetadata(event, entityTypeId, watchableObjects);
      if (entityTypedata != null) {
        entity.setTypeData(entityTypedata);
      } else {
//        IntaveLogger.logger().info("Unable to update entity metadata of entity " + entityId + " of type " + entityTypeId);
      }
    }
    reader.release();
  }

  private void handleFirework(
    Player player, List<? extends WrappedWatchableObject> watchableObjects
  ) {
    if (!MinecraftVersions.VER1_11_0.atOrAbove()) {
      return;
    }
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      if (watchableObject != null) {
        int index = watchableObject.getIndex();
        Object value = watchableObject.getValue();
        if (MinecraftVersions.VER1_14_0.atOrAbove()) {
          if (processFireworkModern(player, index, value)) {
            // ?
            return;
          }
        } else {
          if (processFireworkLegacy(player, index, value)) {
            // ?
            return;
          }
        }
      }
    }
  }

  private boolean processFireworkLegacy(Player player, int index, Object value) {
    User user = UserRepository.userOf(player);
    if (index == 7) {
      if (!(value instanceof Integer)) {
        return false;
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
      return true;
    }
    return false;
  }

  private static final int MODERN_ENTITY_ID_ACCESS_INDEX = MinecraftVersions.VER1_17_0.atOrAbove() ? 9 : 8;

  private boolean processFireworkModern(Player player, int index, Object value) {
    User user = UserRepository.userOf(player);
    if (index == MODERN_ENTITY_ID_ACCESS_INDEX && value instanceof OptionalInt) {
      OptionalInt optionalId = (OptionalInt) value;
      if (!optionalId.isPresent()) {
        return false;
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
      return true;
    }
    return false;
  }

  private static final String FIREWORK_IDENTIFIER = "FIREWORK";

  private void processHealthMetadata(
    Player player, Entity entity,
    List<? extends WrappedWatchableObject> watchableObjects
  ) {
    Float health = readHealthOf(watchableObjects);
    if (health != null) {
      boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
      if (synchronize) {
//        FeedbackObserver tracker = entity.feedbackTracker();
//        Modules.feedback().tracedSingleSynchronize(player, entity, (p, e) -> updateHealthState(e, health), tracker);
        User user = UserRepository.userOf(player);
        user.tracedTickFeedback(() -> updateHealthState(entity, health), entity.feedbackTracker());
      } else {
        updateHealthState(entity, health);
      }
    }
  }

  private void synchronizePlayerHealth(Player player, List<? extends WrappedWatchableObject> watchableObjects) {
    if (watchableObjects == null) {
      return;
    }
    Float health = readHealthOf(watchableObjects);
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

  private final boolean HEALTH_PROCESSING_1_10 = MinecraftVersions.VER1_10_0.atOrAbove();
  private final boolean HEALTH_PROCESSING_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();

  private Float readHealthOf(List<? extends WrappedWatchableObject> watchableObjects) {
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      int index = watchableObject.getIndex();
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
      if (index == requiredIndex) {
        return readHealthFromWatchableObject(watchableObject);
      }
    }
    return null;
  }

  private Float readHealthFromWatchableObject(WrappedWatchableObject watchableObject) {
    Object rawValue = watchableObject.getRawValue();
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