package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.PacketSender;
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
  public void receiveAttack(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action != EnumWrappers.EntityUseAction.ATTACK) {
      return;
    }
    if (clientData.protocolVersion() <= VER_1_8) {
      checkGUIScreen(player);
    }
    checkDeadEntity(player, packet);
    checkBlocking(event);
  }

  private void checkBlocking(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    // Disable check on 1.9+ due to inconsistencies in mc source
    if (user.protocolVersion() > 47) {
      return;
    }
    // not checked yet
    AttackInInvalidStateMeta meta = metaOf(user);
    if (user.meta().inventory().handActive() && user.meta().movement().lastTeleport > 10) {
      Anomaly anomaly = Anomaly.anomalyOf("attack:item", Confidence.NONE, Anomaly.Type.KILLAURA, "attacked whilst using an item");
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
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    } else {
      PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Client.BLOCK_DIG);
      packet.getBlockPositionModifier().write(0, new BlockPosition(0, 0, 0));
      packet.getDirections().write(0, EnumWrappers.Direction.DOWN);
      packet.getPlayerDigTypes().write(0, EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);
      userOf(player).ignoreNextInboundPacket();
      PacketSender.receiveClientPacketFrom(player, packet);
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
      Anomaly anomaly = Anomaly.anomalyOf("attack:inv", confidence, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
      meta.lastGUIAttackTimestamps = now;
    }
  }

  private void checkDeadEntity(Player player, PacketContainer packet) {
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
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK && entity.dead) {
      String description = "attacked a dead entity " + entity.entityName();
      Anomaly anomaly = Anomaly.anomalyOf("attack:dead", Confidence.NONE, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  public static final class AttackInInvalidStateMeta extends CheckCustomMetadata {
    public long lastGUIAttackTimestamps;
    public int internalVl;
  }
}