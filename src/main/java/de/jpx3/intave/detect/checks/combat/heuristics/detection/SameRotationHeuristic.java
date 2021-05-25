package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.PacketType;
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
      meta.rotationsSinceTeleport = 0;
      return;
    }

    Tick currentTick = new Tick(
      movementData.rotationYaw, movementData.rotationPitch,
      Math.abs(movementData.lastRotationYaw - movementData.rotationYaw),
      Math.abs(movementData.lastRotationPitch - movementData.rotationPitch)
    );

    boolean isPartner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
//    boolean isEnterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;

    if(movementData.lastTeleport > 5 && isPartner && meta.rotationsSinceTeleport > 5) {
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

    if(meta.lastTick.yawMotion != 0) {
      meta.yawRotations.add(meta.lastLastTick.yaw);
    }
    if(meta.lastTick.pitchMotion != 0) {
      meta.pitchRotations.add(meta.lastLastTick.pitch);
    }

    prepareNextTick(user, currentTick, event.getPacketType());
  }

  private void checkExactRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die alte Rotation Yaw oder Pitch eine ganze Zahl ist
    // Wird genutzt um false flaggs zu vermeiden wenn die alte Rotation eine Ganzezahl war und man sich mit einer ganzen Zahl rotiert hat.
    boolean lastYawMotionExactNumber = meta.lastLastTick.yawMotion % 1 == 0;

    // Guckt ob die rotation Yaw oder Pitch eine ganze Zahl ist
    boolean yawExactNumber = meta.lastTick.yaw % 1 == 0;

    if(yawExactNumber && !lastYawMotionExactNumber) {
      String description = "exact Yaw Rotation:" + meta.lastTick.yaw;
      Anomaly anomaly = Anomaly.anomalyOf("183", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }
  private void checkExactRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die alte Rotation Yaw oder Pitch eine ganze Zahl ist
    // Wird genutzt um false flaggs zu vermeiden wenn die alte Rotation eine Ganzezahl war und man sich mit einer ganzen Zahl rotiert hat.
    boolean lastPitchMotionExactNumber = meta.lastLastTick.pitchMotion % 1 == 0;
    // Guckt ob die rotation Yaw oder Pitch eine ganze Zahl ist
    boolean pitchExactNumber = meta.lastTick.pitch % 1 == 0;

    if(pitchExactNumber && Math.abs(meta.lastTick.pitch) != 90 && !lastPitchMotionExactNumber) {
      String description = "exact Pitch Rotation:" + meta.lastTick.pitch;
      Anomaly anomaly = Anomaly.anomalyOf("183", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkSameRotationYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die rotation die ein Spieler hat schon mal zuvor gesendet wurde wärend der Spieler sich schnell gedreht hat
    boolean containedYaw = meta.yawRotations.contains(meta.lastTick.yaw);

    if (containedYaw) {
      String description = "same Rotation (Yaw:" + meta.lastTick.yaw + ", YawMotion:" + MathHelper.formatDouble(meta.lastTick.yawMotion, 2) + ")";
      Anomaly anomaly = Anomaly.anomalyOf("181", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }
  private void checkSameRotationPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die rotation die ein Spieler hat schon mal zuvor gesendet wurde wärend der Spieler sich schnell gedreht hat
    boolean containedPitch = meta.pitchRotations.contains(meta.lastTick.pitch);

    if(containedPitch) {
      String description = "same Rotation (Pitch:" + meta.lastTick.pitch + ", PitchMotion:" + MathHelper.formatDouble(meta.lastTick.pitchMotion, 2) + ")";
      Anomaly anomaly = Anomaly.anomalyOf("181", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }

  private void checkExactRotationMotionYaw(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die Rotation Bewegung des Spielers eine ganze Zahl war wenn er sich schnell rotiert hat.
    boolean yawMotionExactNumber = meta.lastTick.yawMotion % 1 == 0;

    if(yawMotionExactNumber) {
      String description = "exact Yaw Motion:" + meta.lastTick.yawMotion;
      Anomaly anomaly = Anomaly.anomalyOf("182", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }
  private void checkExactRotationMotionPitch(SameRotationHeuristicMeta meta, Player player) {
    // Guckt ob die Rotation Bewegung des Spielers eine ganze Zahl war wenn er sich schnell rotiert hat.
    boolean pitchMotionExactNumber = meta.lastTick.pitchMotion % 1 == 0;

    if(pitchMotionExactNumber) {
      String description = "exact Pitch Motion:" + meta.lastTick.pitchMotion;
      Anomaly anomaly = Anomaly.anomalyOf("182", Confidence.NONE, Anomaly.Type.KILLAURA, description, getOptions(true));
      parentCheck().saveAnomaly(player, anomaly);
    }
  }


  private int getOptions(boolean isPartner) {
    int options;
    if (IntaveControl.GOMME_MODE) {
      options = Anomaly.AnomalyOption.DELAY_32s;
    } else if (isPartner) {
      options = Anomaly.AnomalyOption.DELAY_64s;
    } else {
      options = Anomaly.AnomalyOption.DELAY_128s;
    }

    return options;
  }

  private void prepareNextTick(User user, Tick currentTick, PacketType packetType) {
    SameRotationHeuristicMeta meta = metaOf(user);

    meta.lastLastTick = meta.lastTick;
    meta.lastTick = currentTick;

    if (meta.yawRotations.size() > 40)
      meta.yawRotations.remove(0);
    if (meta.pitchRotations.size() > 40)
      meta.pitchRotations.remove(0);

    if(packetType == PacketType.Play.Client.LOOK || packetType == PacketType.Play.Client.POSITION_LOOK) {
      meta.rotationsSinceTeleport++;
    }
  }


  public static final class SameRotationHeuristicMeta extends UserCustomCheckMeta {
    private int rotationsSinceTeleport;
    private Set<Float> yawRotations = new HashSet<>();
    private Set<Float> pitchRotations = new HashSet<>();
    private Tick lastLastTick = new Tick();
    private Tick lastTick = new Tick();
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