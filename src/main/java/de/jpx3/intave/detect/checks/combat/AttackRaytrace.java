package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.event.service.entity.ClientSideEntityService.entityByIdentifier;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COMBAT_UPDATE;
import static de.jpx3.intave.world.raytrace.Raytracer.distanceOf;

public class AttackRaytrace extends IntaveMetaCheck<AttackRaytrace.AttackRaytraceMeta> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;

  public AttackRaytrace(IntavePlugin plugin) {
    super("AttackRaytrace", "attackraytrace", AttackRaytraceMeta.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 1);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(player);
    EnumWrappers.EntityUseAction useAction = packet.getEntityUseActions().readSafely(0);
    if (useAction == EnumWrappers.EntityUseAction.ATTACK) {
      PacketContainer packetClone = packet.deepClone();
      int entityId =  packet.getIntegers().read(0);
      Attack attack = new Attack(packetClone, entityId);
      if(attackRaytraceMeta.remainingAttacks.size() < 4) {
        attackRaytraceMeta.remainingAttacks.add(attack);
      }
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    PacketContainer packet = event.getPacket();
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    List<Attack> remainingAttacks = attackRaytraceMeta.remainingAttacks;
    if(!remainingAttacks.isEmpty()) {
      for (Attack remainingAttack : remainingAttacks) {
        WrappedEntity entity = entityByIdentifier(user, remainingAttack.entityId());
        boolean invalid = false;
        if (entity != null && entity.checkable() && !player.isDead()) {
          if (entity.clientSynchronized && clientData.protocolVersion() >= PROTOCOL_VERSION_COMBAT_UPDATE
            && !movementData.recentlyEncounteredFlyingPacket(2)
            && attackRaytraceMeta.lastFlyPacketCounterReach > 1
          ) {
            invalid = processReachCheck(player, entity);
          } else if (entity.clientSynchronized && clientData.protocolVersion() <= PROTOCOL_VERSION_BOUNTIFUL_UPDATE && attackRaytraceMeta.lastFlyPacketCounterReach > 1) {
            invalid = processReachCheck(player, entity);
          } else {
            invalid = processIterativeReachCheck(player, entity);
          }
        }
        if(!invalid && !violationLevelData.isInActiveTeleportBundle) {
          receiveExcludedPacket(player, remainingAttack.packet);
        }
      }
      remainingAttacks.clear();
    }

    boolean hasMovement = packet.getBooleans().read(1);
    if (!hasMovement) {
      attackRaytraceMeta.lastFlyPacketCounterReach = 0;
    } else {
      attackRaytraceMeta.lastFlyPacketCounterReach++;
    }
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    try {
      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  private boolean processReachCheck(Player player, WrappedEntity entity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    AttackRaytraceMeta attackRaytraceMeta = metaOf(user);
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;

    // mouse delay fix
    double reach = distanceOf(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch
    );

    if (!hasAlwaysMouseDelayFix && reach > blockReachDistance) {
      // normal
      reach = distanceOf(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch
      );
    }

    attackData.setLastReach(reach);
    String message;
    String details;
    String thresholdKey;
    int vl;


    AttackRaytraceResult.ResultType attackRaytraceResult = AttackRaytraceResult.raytraceResultOf(blockReachDistance, reach);
    String entityName = entity.entityName();
    switch (attackRaytraceResult) {
      case MISS: {
        message = "attacked " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase() + " out of sight";
        details = "";
        thresholdKey = "applicable-thresholds.hitbox";
        vl = 2;
        Synchronizer.synchronize(() -> {
          String sibylMessage = ChatColor.RED + "[R] " + player.getName() + " missed " + entityName.toLowerCase();
          for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
            if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
              authenticatedPlayer.sendMessage(sibylMessage);
            }
          }
        });

        break;
      }
      case REACH: {
        if (reach < 3.6 && attackRaytraceMeta.confidence++ == 0) {
          return false;
        }
        String displayReach = MathHelper.formatDouble(reach, 4);
        message = "attacked " + resolveIndefArticle(entityName) + " " + entityName.toLowerCase() + " from too far away";
        details = displayReach + " blocks";
        thresholdKey = "applicable-thresholds.reach";
        vl = 20;

        Synchronizer.synchronize(() -> {
          String sibylMessage = ChatColor.RED + "[R] " + player.getName() + " attacked " + entityName.toLowerCase() + " from " + displayReach + " blocks";
          for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
            if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
              authenticatedPlayer.sendMessage(sibylMessage);
            }
          }
        });

        break;
      }
      default: {
        decrementer.decrement(user, 0.05);
        return false;
      }
    }

    if (movementData.inVehicle()) {
      vl = 0;
      message += " (vehicle)";
    }

   plugin.violationProcessor().processViolation(player, vl, "AttackRaytrace", message, details, thresholdKey);
//    player.sendMessage("§6s:" + reach);
    return true;
  }

  private boolean processIterativeReachCheck(Player player, WrappedEntity attackedEntity) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();

    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;

    double minReach = 10;

    WrappedEntity clonedEntity = attackedEntity.clone();

    int index = 0;

    for (WrappedEntity.EntityPositionContext possiblePosition : clonedEntity.possiblePositions) {
      clonedEntity.position = possiblePosition.clone();
      // TODO: 01/07/21 add trust-factor based length tolerance
      clonedEntity.newPosRotationIncrements = 3;

      double minReachInItr = 10;

      for (int loopRotationIncrement = 0; loopRotationIncrement < 4; loopRotationIncrement++) {
        // mouse delay fix
        double reach = distanceOf(
          player,
          clonedEntity.entityBoundingBox().grow(0.13),
          clonedEntity.position, null,
          false,
          movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
          rotationYaw, movementData.rotationPitch
        );

        if (!hasAlwaysMouseDelayFix && reach > blockReachDistance) {
          // normal
          reach = distanceOf(
            player,
            clonedEntity.entityBoundingBox().grow(0.13),
            clonedEntity.position, null,
            false,
            movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
            lastRotationYaw, movementData.rotationPitch
          );
        }

        minReachInItr = Math.min(minReachInItr, reach);

        clonedEntity.onLivingUpdate();
      }
//      player.sendMessage(MathHelper.formatMotion(new Vector(possiblePosition.posX, possiblePosition.posY, possiblePosition.posZ)) + " " + minReachInItr + " " + maxReachInItr);

      minReach = Math.min(minReach, minReachInItr);

      index++;
    }

    // TODO: 01/07/21 clear after last possible position

    if(minReach > blockReachDistance) {
      String entityName = attackedEntity.entityName();
      String targetDescriptor = resolveIndefArticle(entityName) + " " + entityName.toLowerCase();
      String thresholdKey = "";

      String message, details;
      if(minReach == 10) {
        message = "attacked " + targetDescriptor + " out of sight";
        details = "estimated";
        thresholdKey = "applicable-thresholds.hitbox";
      } else {
        String minReachDisplay = MathHelper.formatDouble(minReach, 4) + " blocks";
//        String maxReachDisplay = maxReach == 10 ? "miss" : MathHelper.formatDouble(maxReach, 4) + " blocks";
//        message = "attacked "+targetDescriptor+" too far away (estimated) (" + minReachDisplay + " at best)";
        message = "attacked " + targetDescriptor + " from too far away";
        details = minReachDisplay + " at best, estimated";
        thresholdKey = "applicable-thresholds.reach";
      }

      if (movementData.inVehicle()) {
        message += " (vehicle)";
      }

      plugin.violationProcessor().processViolation(player, 0, "AttackRaytrace", message, details, thresholdKey);
      return true;
    }
    decrementer.decrement(user, 0.05);
    return false;
  }

  private final static char[] vocals = "aeiou".toCharArray();

  private String resolveIndefArticle(String exceptionName) {
    char c = exceptionName.toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "an" : "a";
  }

  public static float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) + 0.001f;
  }

  public static class AttackRaytraceMeta extends UserCustomCheckMeta {
    public int lastFlyPacketCounterReach = 0;
    public List<Attack> remainingAttacks = new ArrayList<>();
    public int confidence;
  }

  public static class Attack {
    private final PacketContainer packet;
    private final int entityId;


    public Attack(PacketContainer packet, int entityId) {
      this.packet = packet;
      this.entityId = entityId;
    }

    public PacketContainer packet() {
      return packet;
    }

    public int entityId() {
      return entityId;
    }
  }

  public static final class AttackRaytraceResult {
    public static AttackRaytraceResult.ResultType raytraceResultOf(double allowedReach, double value) {
      if (value == 10.0) {
        return AttackRaytraceResult.ResultType.MISS;
      }
      return value > allowedReach ? AttackRaytraceResult.ResultType.REACH : AttackRaytraceResult.ResultType.NORMAL;
    }

    public enum ResultType {
      NORMAL,
      REACH,
      MISS
    }
  }
}