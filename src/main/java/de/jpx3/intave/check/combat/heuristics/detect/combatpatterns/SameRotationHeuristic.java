package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.diagnostic.natives.NativeCheck;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

@Reserved
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
    // Exclude 1.9+ due to falses
    // TODO: This is a really temporary and shit fix so players on gomme don't get false kicked
    if (user.protocolVersion() >= VER_1_9) {
      return;
    }
    SameRotationHeuristicMeta meta = metaOf(user);
    MetadataBundle metadata = user.meta();
    MovementMetadata movementData = metadata.movement();
    ViolationMetadata violationLevelData = metadata.violationLevel();
    AbilityMetadata abilityMetadata = metadata.abilities();

    if (abilityMetadata.health <= 0 || abilityMetadata.unsynchronizedHealth <= 0) {
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

  {
    NativeCheck.registerNative(this::isPartner);
  }

  @Native
  public boolean isPartner() {
    if (NativeCheck.checkActive()) {
      return false;
    }
    return (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
  }

  private void checkExactRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Checks whether the last rotation yaw or pitch is an integer
    // Used to avoid false positives if the old rotation was an integer and the rotation is done with an integer.
    boolean lastYawMotionExactNumber = meta.lastLastTick.yawMotion % 1 == 0;

    // Checks whether the rotation yaw or pitch is an integer
    boolean yawExactNumber = meta.lastTick.yaw % 1 == 0;

    if (yawExactNumber && !lastYawMotionExactNumber) {
      meta.violationLevel += transformViolation(30);
      String description = "exact yaw rotation:" + meta.lastTick.yaw;
      Anomaly anomaly = anomalyOf("183", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkExactRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Checks whether the last rotation yaw or pitch is an integer
    // Used to avoid false positives if the old rotation was an integer and the rotation is done with a integer.
    boolean lastPitchMotionExactNumber = meta.lastLastTick.pitchMotion % 1 == 0;
    // Checks whether the rotation yaw or pitch is an integer
    boolean pitchExactNumber = meta.lastTick.pitch % 1 == 0;

    if (pitchExactNumber && Math.abs(meta.lastTick.pitch) != 90 && !lastPitchMotionExactNumber) {
      meta.violationLevel += transformViolation(30);
      String description = "exact pitch rotation:" + meta.lastTick.pitch;
      Anomaly anomaly = anomalyOf("183", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkSameRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Checks if the rotation a player maintains has been sent before while the player was turning fast
    boolean containedYaw = meta.yawRotations.contains(meta.lastTick.yaw);

    if (containedYaw) {
      meta.violationLevel += transformViolation(60);
      String description = "same rotation (Yaw:" + meta.lastTick.yaw + ", YawMotion:" + MathHelper.formatDouble(meta.lastTick.yawMotion, 2) + ")";
      Anomaly anomaly = anomalyOf("181", description, meta);
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkSameRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Checks if the rotation a player maintains has been sent before while the player was turning fast
    // INFO: This check has been disabled due to false positives with LabyMod
    // The false positives are caused by using the emote wheel (X Key) and moving your mouse up, if a player is not moving the mouse and the x key gets released it's causing a flag.
//    boolean containedPitch = meta.pitchRotations.contains(meta.lastTick.pitch);


//    if (containedPitch && Math.abs(meta.lastTick.pitch) != 90) {
//      meta.violationLevel += transformViolation(60);
//      String description = "same rotation (Pitch:" + meta.lastTick.pitch + ", PitchMotion:" + MathHelper.formatDouble(meta.lastTick.pitchMotion, 2) + ")";
//      Anomaly anomaly = anomalyOf("181", description, meta);
//      parentCheck().saveAnomaly(player, anomaly);
//    }
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
      options = DELAY_32s;
    } else if (isPartner()) {
      options = DELAY_64s;
    } else {
      options = DELAY_128s;
    }
    options |= LIMIT_4;
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

  @Reserved
  public static final class SameRotationHeuristicMeta extends CheckCustomMetadata {
    private int violationLevel;
    private final List<Float> yawRotations = new ArrayList<>();
    private final List<Float> pitchRotations = new ArrayList<>();
    private int rotationsSinceTeleport;
    private Tick lastLastTick = new Tick();
    private Tick lastTick = new Tick();
    private int ticksSinceRespawn;
  }

  public static class Tick {
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
}
