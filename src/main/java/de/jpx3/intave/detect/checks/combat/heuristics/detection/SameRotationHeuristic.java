package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class SameRotationHeuristic extends IntaveMetaCheckPart<Heuristics, SameRotationHeuristic.SameRotationHeuristicMeta> {
  public SameRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, SameRotationHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION")
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0)) {
      return;
    }

    Player player = event.getPlayer();
    User user = userOf(player);
    SameRotationHeuristicMeta meta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();

    if (movementData.lastTeleport == 0) {
      return;
    }

    double rotationMotion = Math.hypot(movementData.lastRotationYaw - movementData.rotationYaw, movementData.lastRotationPitch - movementData.rotationPitch);

    if (meta.lastLastTick.rotationMotion < 10 && meta.lastTick.rotationMotion > 40 && rotationMotion < 10 && movementData.lastTeleport > 5) {

      boolean containedYaw = meta.yawRotations.contains(meta.lastLastTick.yaw) || meta.yawRotations.contains(meta.lastTick.yaw);
      boolean containedPitch = meta.pitchRotations.contains(meta.lastLastTick.pitch) || meta.pitchRotations.contains(meta.lastTick.pitch);
      if (containedYaw || containedPitch) {
        String description = "same rotation (" +
          MathHelper.formatDouble(meta.lastTick.rotationMotion, 2) + ", " +
          "yaw:" + containedYaw +
          ", pitch:" + containedPitch + ")";

        boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
        boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

        int options;
        if (IntaveControl.GOMME_MODE) {
          options = Anomaly.AnomalyOption.DELAY_32s;
        } else if (isPartner) {
          options = Anomaly.AnomalyOption.DELAY_64s;
        } else {
          options = Anomaly.AnomalyOption.DELAY_128s;
        }

        Anomaly anomaly = Anomaly.anomalyOf("103", Confidence.NONE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }

      boolean yawWholeNumber = meta.lastTick.yawMotion % 1 == 0;
      boolean pitchWholeNumber = meta.lastTick.pitchMotion % 1 == 0;

      if (yawWholeNumber || pitchWholeNumber) {
        String description = "whole rotation ("
          + "yaw: " + (yawWholeNumber)
          + ", pitch: " + (pitchWholeNumber) + ")";

        boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
        boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

        int options;
        if (IntaveControl.GOMME_MODE) {
          options = Anomaly.AnomalyOption.DELAY_32s;
        } else if (isPartner) {
          options = Anomaly.AnomalyOption.DELAY_64s;
        } else {
          options = Anomaly.AnomalyOption.DELAY_128s;
        }

        Anomaly anomaly = Anomaly.anomalyOf("104", Confidence.NONE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
      }

      meta.yawRotations.add(meta.lastLastTick.yaw);
      meta.yawRotations.add(meta.lastTick.yaw);

      meta.pitchRotations.add(meta.lastLastTick.pitch);
      meta.pitchRotations.add(meta.lastTick.pitch);
    }

    prepareNextTick(user, rotationMotion);
  }

  private void prepareNextTick(User user, double rotationMotion) {
    UserMetaMovementData movementData = user.meta().movementData();
    SameRotationHeuristicMeta meta = metaOf(user);

    float yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
    float pitchMotion = Math.abs(movementData.lastRotationPitch - movementData.rotationPitch);

    meta.lastLastTick = meta.lastTick;
    meta.lastTick = new Tick(movementData.rotationYaw, movementData.rotationPitch, rotationMotion, yawMotion, pitchMotion);

    if (meta.yawRotations.size() > 15)
      meta.yawRotations.remove(0);
    if (meta.pitchRotations.size() > 15)
      meta.pitchRotations.remove(0);
  }


  public static final class SameRotationHeuristicMeta extends UserCustomCheckMeta {
    private Set<Float> yawRotations = new HashSet<>();
    private Set<Float> pitchRotations = new HashSet<>();
    private Tick lastLastTick = new Tick();
    private Tick lastTick = new Tick();
  }
}

class Tick {
  float yaw, pitch;
  double rotationMotion;
  float yawMotion, pitchMotion;

  public Tick() {
  }

  public Tick(float yaw, float pitch, double rotationMotion, float yawMotion, float pitchMotion) {
    this.yaw = yaw;
    this.pitch = pitch;
    this.rotationMotion = rotationMotion;
    this.yawMotion = yawMotion;
    this.pitchMotion = pitchMotion;
  }
}