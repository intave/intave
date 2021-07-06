package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static de.jpx3.intave.event.packet.PacketId.Client.*;

public final class SprintResetHeuristic extends IntaveMetaCheckPart<Heuristics, SprintResetHeuristic.SprintResetHeuristicMeta> {
  /*
  What the check does:
  The player will receive a flag when he unsprints without releasing the W key while he doesn't sneak, attack, useItem or collide with a block.
   */
  public SprintResetHeuristic(Heuristics parentCheck) {
    super(parentCheck, SprintResetHeuristic.SprintResetHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ENTITY_ACTION
    }
  )
  public void receiveSprintPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintResetHeuristicMeta meta = metaOf(user);
    EnumWrappers.PlayerAction playerAction = event.getPacket().getPlayerActions().read(0);

    if(playerAction == EnumWrappers.PlayerAction.START_SPRINTING) {
      meta.startSprint = true;
    } else if(playerAction == EnumWrappers.PlayerAction.STOP_SPRINTING) {
      meta.stopSprint = true;
    } else if(playerAction == EnumWrappers.PlayerAction.START_SNEAKING) {
      meta.startSneak = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttackPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintResetHeuristicMeta meta = metaOf(user);
    EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);

    if(action == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING
    }
  )
  public void receiveMovePacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SprintResetHeuristicMeta meta = metaOf(user);

    if(meta.stopSprint) {
      if(!user.meta().abilityData().inGameMode(GameMode.CREATIVE)) {
        playerUnsprinted(player, meta);
      }
    }
    if(meta.startSprint) {
      playerStartSprinting(meta);
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(SprintResetHeuristicMeta meta) {
    meta.lastAttack++;
    meta.startSprint = false;
    meta.stopSprint = false;
    meta.startSneak = false;

    if (meta.sprintingTicksLeft > 0)  {
      --meta.sprintingTicksLeft;
    }
  }

  private void playerStartSprinting(SprintResetHeuristicMeta meta) {
    meta.sprintingTicksLeft = 600;
  }

  private void playerUnsprinted(Player player, SprintResetHeuristicMeta meta) {
    User user = userOf(player);
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItem = inventoryData.heldItem();
    boolean useItem = InventoryUseItemHelper.isUseItem(player, heldItem);
    UserMetaMovementData movementData = user.meta().movementData();

    // can false flag if a player collides with a wall some times

    boolean attacked = meta.lastAttack <= 1;
    UserMetaAbilityData abilityData = user.meta().abilityData();

    if(!attacked
      && movementData.pastInWeb > 2
      && player.getFoodLevel() > 6
      && abilityData.unsynchroniszedHealth > 0
      && meta.sprintingTicksLeft != 0
      && !useItem
      && movementData.lastTeleport != 0
      && movementData.keyForward == 1
      && !meta.startSneak
      && !movementData.recentlyEncounteredFlyingPacket(2)
      && movementData.pastFlyingPacketAccurate() > 2
      // could also use other values to activate the check (for example could a scaffold walk flag this check)
      && meta.lastAttack < 80
    ) {
      boolean collided = movementData.collidedHorizontally;
      if(!collided) {
        collided = canCollideHorizontally(user, movementData);
      }
      if(!collided) {
        UserMetaClientData clientData = user.meta().clientData();
        String details = "unsprinted and pressed W " + clientData.versionString() + " " + meta.lastAttack;
        Anomaly anomaly = Anomaly.anomalyOf("210",
          Confidence.NONE,
          Anomaly.Type.KILLAURA,
          details,
          Anomaly.AnomalyOption.DELAY_16s);
        parentCheck().saveAnomaly(player, anomaly);
      }
    }
  }

  private boolean canCollideHorizontally(User user, UserMetaMovementData movementData) {
    WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox().expand(0.031d, 0, 0.031d);
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(user.player(), entityBoundingBox);
    return !collisionBoxes.isEmpty();
  }

  public static class SprintResetHeuristicMeta extends UserCustomCheckMeta {
    private boolean startSneak;
    private boolean startSprint;
    private boolean stopSprint;
    private int lastAttack = 9999;
    private int sprintingTicksLeft;
  }
}