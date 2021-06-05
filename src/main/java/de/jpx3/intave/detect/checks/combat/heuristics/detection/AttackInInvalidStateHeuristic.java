package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BOUNTIFUL_UPDATE;

public final class AttackInInvalidStateHeuristic extends IntaveMetaCheckPart<Heuristics, AttackInInvalidStateHeuristic.AttackInInvalidStateMeta> {
  public AttackInInvalidStateHeuristic(Heuristics heuristics) {
    super(heuristics, AttackInInvalidStateMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttack(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    checkGUIScreen(player);
    checkDeadEntity(player, packet);
    checkBlocking(event);
  }

  private void checkBlocking(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    // not checked yet
    if (user.meta().inventoryData().handActive()) {
      Anomaly anomaly = Anomaly.anomalyOf("162", Confidence.NONE, Anomaly.Type.KILLAURA, "attacked whilst using an item");
      parentCheck().saveAnomaly(player, anomaly);
      //dmc28
      user.applyAttackNerfer(AttackNerfStrategy.BLOCKING, "28");
    }
  }

  private void checkGUIScreen(Player player) {
    User user = userOf(player);
    AttackInInvalidStateMeta meta = metaOf(user);
    UserMetaClientData clientData = user.meta().clientData();
    UserMetaAbilityData abilityData = user.meta().abilityData();
    float health = abilityData.health;
    if (health <= 0f) {
      long now = AccessHelper.now();
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

  private void checkDeadEntity(Player player, PacketContainer packet) {
    User user = userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    UserMetaClientData clientData = user.meta().clientData();
    WrappedEntity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || !entity.isEntityLiving || !entity.entityTypeData.isLivingEntity()) {
      return;
    }
    if (clientData.protocolVersion() != PROTOCOL_VERSION_BOUNTIFUL_UPDATE) {
      return;
    }
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
    if (action == EnumWrappers.EntityUseAction.ATTACK && entity.dead) {
      String description = "attacked a dead entity " + entity.entityName();
      Anomaly anomaly = Anomaly.anomalyOf("161", Confidence.NONE, Anomaly.Type.KILLAURA, description);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  public static final class AttackInInvalidStateMeta extends UserCustomCheckMeta {
    public long lastGUIAttackTimestamps;
  }
}