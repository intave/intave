package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.MetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.event.packet.PacketId.Client.*;

public final class SameRotationHeuristic extends MetaCheckPart<Heuristics, SameRotationHeuristic.SameRotationHeuristicMeta> {
  public SameRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, SameRotationHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    if (ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0) && player.isInsideVehicle()) {
      return;
    }
    User user = userOf(player);
    SameRotationHeuristicMeta meta = metaOf(user);
    UserMeta userMeta = user.meta();
    UserMetaMovementData movementData = userMeta.movementData();
    UserMetaViolationLevelData violationLevelData = userMeta.violationLevelData();
    UserMetaAbilityData userMetaAbilityData = userMeta.abilityData();

    if(userMetaAbilityData.health <= 0 || userMetaAbilityData.unsynchronizedHealth <= 0) {
      meta.ticksSinceRespawn = 0;
    }

    if (movementData.lastTeleport == 0 || violationLevelData.isInActiveTeleportBundle) {
      meta.rotationsSinceTeleport = 0;
      return;
    }

    Tick currentTick = new Tick(
      movementData.rotationYaw, movementData.rotationPitch,
      Math.abs(movementData.lastRotationYaw - movementData.rotationYaw),
      Math.abs(movementData.lastRotationPitch - movementData.rotationPitch)
    );

    if (movementData.lastTeleport > 5 && isPartner() && meta.rotationsSinceTeleport > 6 && meta.ticksSinceRespawn > 5) {
      if (meta.lastLastTick.yawMotion < 10 && meta.lastTick.yawMotion > 45 && currentTick.yawMotion < 10) {
        checkSameRotationYaw(meta, player);
        checkExactRotationMotionYaw(meta, player);
        checkExactRotationYaw(meta, player);
      }
      if (meta.lastLastTick.pitchMotion < 10 && meta.lastTick.pitchMotion > 35 && currentTick.yawMotion < 10) {
        checkSameRotationPitch(meta, player);
        checkExactRotationMotionPitch(meta, player);
        checkExactRotationPitch(meta, player);
      }
    }

    if (meta.lastTick.yawMotion != 0) {
      float yaw = meta.lastLastTick.yaw;
      if (!meta.yawRotations.contains(yaw)) {
        meta.yawRotations.add(yaw);
      }
    }
    if (meta.lastTick.pitchMotion != 0) {
      float pitch = meta.lastLastTick.pitch;
      if (!meta.pitchRotations.contains(pitch)) {
        meta.pitchRotations.add(pitch);
      }
    }

    prepareNextTick(user, currentTick, event.getPacketType());
  }

  @Native
  public boolean isPartner() {
    return (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
  }

  private void checkExactRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die alte Rotation Yaw oder Pitch eine ganze Zahl ist
    // Wird genutzt um false flaggs zu vermeiden wenn die alte Rotation eine Ganzezahl war und man sich mit einer ganzen Zahl rotiert hat.
    boolean lastYawMotionExactNumber = meta.lastLastTick.yawMotion % 1 == 0;

    // Guckt ob die rotation Yaw oder Pitch eine ganze Zahl ist
    boolean yawExactNumber = meta.lastTick.yaw % 1 == 0;

    if (yawExactNumber && !lastYawMotionExactNumber) {
      meta.violationLevel += transformViolation(20);
      String description = "exact Yaw Rotation:" + meta.lastTick.yaw;
      Anomaly anomaly = anomalyOf("183", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkExactRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die alte Rotation Yaw oder Pitch eine ganze Zahl ist
    // Wird genutzt um false flaggs zu vermeiden wenn die alte Rotation eine Ganzezahl war und man sich mit einer ganzen Zahl rotiert hat.
    boolean lastPitchMotionExactNumber = meta.lastLastTick.pitchMotion % 1 == 0;
    // Guckt ob die rotation Yaw oder Pitch eine ganze Zahl ist
    boolean pitchExactNumber = meta.lastTick.pitch % 1 == 0;

    if (pitchExactNumber && Math.abs(meta.lastTick.pitch) != 90 && !lastPitchMotionExactNumber) {
      meta.violationLevel += transformViolation(20);
      String description = "exact pitch rotation:" + meta.lastTick.pitch;
      Anomaly anomaly = anomalyOf("183", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkSameRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die rotation die ein Spieler hat schon mal zuvor gesendet wurde wärend der Spieler sich schnell gedreht hat
    boolean containedYaw = meta.yawRotations.contains(meta.lastTick.yaw);

    if (containedYaw) {
      meta.violationLevel += transformViolation(50);
      String description = "same rotation (Yaw:" + meta.lastTick.yaw + ", YawMotion:" + MathHelper.formatDouble(meta.lastTick.yawMotion, 2) + ")";
      Anomaly anomaly = anomalyOf("181", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkSameRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die rotation die ein Spieler hat schon mal zuvor gesendet wurde wärend der Spieler sich schnell gedreht hat
    boolean containedPitch = meta.pitchRotations.contains(meta.lastTick.pitch);

    if(Math.abs(meta.lastTick.pitchMotion) > 35 && Math.abs(meta.lastTick.pitchMotion) < 38) {
      // This is a fix for the Labymod bug where the rotation Pitch gets send to the server when selecting the Emote with the "x" key ingame
      return;
    }

    if (containedPitch && Math.abs(meta.lastTick.pitch) != 90) {
      meta.violationLevel += transformViolation(50);
      String description = "same rotation (Pitch:" + meta.lastTick.pitch + ", PitchMotion:" + MathHelper.formatDouble(meta.lastTick.pitchMotion, 2) + ")";
      Anomaly anomaly = anomalyOf("181", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkExactRotationMotionYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die Rotation Bewegung des Spielers eine ganze Zahl war wenn er sich schnell rotiert hat.
    boolean yawMotionExactNumber = meta.lastTick.yawMotion % 1 == 0;

    if (yawMotionExactNumber) {
      meta.violationLevel += transformViolation(30);
      String description = "exact yaw motion:" + meta.lastTick.yawMotion;
      Anomaly anomaly = anomalyOf("182", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkExactRotationMotionPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die Rotation Bewegung des Spielers eine ganze Zahl war wenn er sich schnell rotiert hat.
    boolean pitchMotionExactNumber = meta.lastTick.pitchMotion % 1 == 0;

    if (pitchMotionExactNumber) {
      meta.violationLevel += transformViolation(20);
      String description = "exact pitch motion:" + meta.lastTick.pitchMotion;
      Anomaly anomaly = anomalyOf("182", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private int transformViolation(int violation) {
    if (!IntaveControl.GOMME_MODE) {
      violation /= 2;
    }

    return violation;
  }

  private Anomaly anomalyOf(String key, String description, SameRotationHeuristicMeta meta) {
    Confidence confidence = confidence(meta);
    return Anomaly.anomalyOf(key, confidence, Anomaly.Type.KILLAURA, description + " conf:" + confidence.level(), options());
  }

  private Confidence confidence(SameRotationHeuristicMeta meta) {
    Confidence confidence = Confidence.confidenceFrom(meta.violationLevel);
    meta.violationLevel -= confidence.level();
    return confidence;
  }

  @Native
  private int options() {
    int options;
    if (IntaveControl.GOMME_MODE) {
      options = Anomaly.AnomalyOption.DELAY_32s;
    } else if (isPartner()) {
      options = Anomaly.AnomalyOption.DELAY_64s;
    } else {
      options = Anomaly.AnomalyOption.DELAY_128s;
    }

    return options;
  }

  private void prepareNextTick(User user, Tick currentTick, PacketType packetType) {
    SameRotationHeuristicMeta meta = metaOf(user);

    meta.ticksSinceRespawn++;

    meta.lastLastTick = meta.lastTick;
    meta.lastTick = currentTick;

    if (meta.yawRotations.size() > 50) {
      meta.yawRotations.remove(0);
    }

    if (meta.pitchRotations.size() > 50) {
      meta.pitchRotations.remove(0);
    }

    if (packetType == PacketType.Play.Client.LOOK || packetType == PacketType.Play.Client.POSITION_LOOK) {
      meta.rotationsSinceTeleport++;
    }
  }


  public static final class SameRotationHeuristicMeta extends UserCustomCheckMeta {
    private int violationLevel;
    private final List<Float> yawRotations = new ArrayList<>();
    private final List<Float> pitchRotations = new ArrayList<>();
    private int rotationsSinceTeleport;
    private Tick lastLastTick = new Tick();
    private Tick lastTick = new Tick();
    private int ticksSinceRespawn;
  }
}

class Tick {
  float yaw, pitch;
  float yawMotion, pitchMotion;

  public Tick() {
  }

  public Tick(float yaw, float pitch, float yawMotion, float pitchMotion) {
    this.yaw = yaw;
    this.pitch = pitch;
    this.yawMotion = yawMotion;
    this.pitchMotion = pitchMotion;
  }
}