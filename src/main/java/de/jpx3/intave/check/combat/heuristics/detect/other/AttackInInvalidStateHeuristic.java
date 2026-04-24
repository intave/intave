package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.PacketEventsAdapter;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BLOCKING;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class AttackInInvalidStateHeuristic extends MetaCheckPart<Heuristics, AttackInInvalidStateHeuristic.AttackInInvalidStateMeta> {
  public AttackInInvalidStateHeuristic(Heuristics heuristics) {
    super(heuristics, AttackInInvalidStateMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttack(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      return;
    }
    if (clientData.protocolVersion() <= VER_1_8) {
      checkGUIScreen(player);
    }
    checkDeadEntity(player, packet);
    checkBlocking(event);
  }

  private void checkBlocking(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    // Disable check on 1.9+ due to inconsistencies in mc source
    if (user.protocolVersion() > 47) {
      return;
    }
    // not checked yet
    AttackInInvalidStateMeta meta = metaOf(user);
    if (user.meta().inventory().handActive() && user.meta().movement().lastTeleport > 10) {
      Anomaly anomaly = Anomaly.anomalyOf("162", Confidence.NONE, Anomaly.Type.KILLAURA, "attacked whilst using an item");
      parentCheck().saveAnomaly(player, anomaly);
      //dmc28
      user.nerf(BLOCKING, "28");
//      user.nerf(CRITICALS, "28");
      // This will never happen to a legit player
      if (meta.internalVl++ > 20) {
        user.nerf(AttackNerfStrategy.DMG_ARMOR_INEFFECTIVE, "28");
        user.nerf(AttackNerfStrategy.BURN_LONGER, "28");
        meta.internalVl = 0;
      }
      sendStopUseItemPacketToServer(user);
    }
  }

  private void sendStopUseItemPacketToServer(User user) {
    Player player = user.player();
    if (PacketEventsAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    } else {
      WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(
        DiggingAction.RELEASE_USE_ITEM,
        new Vector3i(0, 0, 0),
        BlockFace.DOWN,
        0
      );
      PacketEvents.getAPI().getPlayerManager().receivePacketSilently(player, packet);
      updatePlayerHandItem(player);
      if (IntaveControl.DEBUG_ITEM_USAGE) {
        player.sendMessage(ChatColor.RED + "Manual stop use item packet sent");
      }
    }
    Synchronizer.synchronize(player::updateInventory);
  }

  private void updatePlayerHandItem(Player player) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    inventoryData.deactivateHand();
  }

  private void checkGUIScreen(Player player) {
    User user = userOf(player);
    AttackInInvalidStateMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();
    AbilityMetadata abilityData = user.meta().abilities();
    float health = abilityData.health;
    if (health <= 0f) {
      long now = System.currentTimeMillis();
      long lastFlag = now - meta.lastGUIAttackTimestamps;
      int ticksAgo = abilityData.ticksToLastHealthUpdate;
      Confidence confidence = lastFlag > 1000 ? Confidence.PROBABLE : Confidence.NONE;
      String description = "attacked in gui screen (version " + clientData.versionString() + ") | ";
      description += "lastHealthUpdate: " + ticksAgo + ", ";
      description += "lastFlag " + lastFlag + " ms ago, ";
      description += "confidence " + confidence.level();
      Anomaly anomaly = Anomaly.anomalyOf("161", confidence, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
      meta.lastGUIAttackTimestamps = now;
    }
  }

  private void checkDeadEntity(Player player, WrapperPlayClientInteractEntity packet) {
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ProtocolMetadata clientData = user.meta().protocol();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || !entity.typeData().isLivingEntity()) {
      return;
    }
    if (clientData.protocolVersion() != VER_1_8) {
      return;
    }
    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK && entity.dead) {
      String description = "attacked a dead entity " + entity.entityName();
      Anomaly anomaly = Anomaly.anomalyOf("161", Confidence.NONE, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  public static final class AttackInInvalidStateMeta extends CheckCustomMetadata {
    public long lastGUIAttackTimestamps;
    public int internalVl;
  }
}
