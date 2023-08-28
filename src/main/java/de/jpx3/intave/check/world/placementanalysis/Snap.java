package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

@Reserved
public final class Snap extends MetaCheckPart<PlacementAnalysis, Snap.SnapMeta> {
  public Snap(PlacementAnalysis parentCheck) {
    super(parentCheck, SnapMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    if (movementData.lastTeleport == 0) {
      return;
    }

    SnapMeta meta = metaOf(user);
    double yawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
    double pitchMotion = Math.abs(movementData.lastRotationPitch - movementData.rotationPitch);

    boolean alphaCondition = meta.pitchAt(1) > 70;
    int pitchLimit = alphaCondition ? 20 : 40;

    /*
      Are all of these values arbitrary? Yes.
      Are they based on just brute force testing? Absolutely.
      Will I ever change them? Probably not.
     */

    // 1st degree snap
    if (yawMotion < 15 && pitchMotion < 15 &&
      meta.yawMotion(1) > 70 && meta.pitchMotion(1) > pitchLimit &&
      meta.yawMotion(2) < 10 && meta.pitchMotion(2) < 10
    ) {
      meta.snapYaw = meta.yawAt(1);
      meta.snapPitch = meta.pitchAt(1);
      meta.snapAge = 0;
      meta.degree = 1;
//      player.sendMessage(ChatColor.RED + "1st degree snap");
    }

    // 2nd degree snap
    if (yawMotion < 8 && pitchMotion < 8 &&
      (meta.yawMotion(1) > 10 || meta.pitchMotion(1) > 10) &&
      (meta.yawMotion(2) > 10 || meta.pitchMotion(2) > 10) &&
      absYawDiff(meta.yawAt(1), meta.yawAt(3)) > 70 && absPitchDiff(meta.pitchAt(1), meta.pitchAt(3)) > pitchLimit &&
      meta.yawMotion(3) < 8 && meta.pitchMotion(3) < 8
    ) {
      meta.snapYaw = meta.yawAt(1);
      meta.snapPitch = meta.pitchAt(1);
      meta.snapAge = 0;
      meta.degree = 2;
//      player.sendMessage(ChatColor.RED + "2nd degree snap " + absYawDiff(meta.yawAt(1), meta.yawAt(3)) + " " + absPitchDiff(meta.pitchAt(1), meta.pitchAt(3)));
    }

    // 3rd degree snap
//    if (yawMotion < 2 && pitchMotion < 2 &&
//      (meta.yawMotion(1) > 10 || meta.pitchMotion(1) > 10) &&
//      (meta.yawMotion(2) > 10 || meta.pitchMotion(2) > 10) &&
//      (meta.yawMotion(3) > 10 || meta.pitchMotion(3) > 10) &&
//      absYawDiff(meta.yawAt(1), meta.yawAt(4)) > 110 && absPitchDiff(meta.pitchAt(1), meta.pitchAt(4)) > pitchLimit &&
//      meta.yawMotion(4) < 2 && meta.pitchMotion(4) < 2
//    ) {
//      meta.snapYaw = meta.yawAt(1);
//      meta.snapPitch = meta.pitchAt(1);
//      meta.snapAge = 0;
//      meta.degree = 3;
////      player.sendMessage(ChatColor.RED + "3rd degree snap");
//    }

    if (meta.yawMotion(1) > 30 && meta.pitchMotion(1) > 30) {
      if (Math.abs(meta.yawMotion(1) - meta.yawMotion(2)) < 5 && Math.abs(meta.yawMotion(1) - meta.yawMotion(2)) < 5) {
        if (absYawDiff(meta.yawAt(1), meta.yawAt(3)) < 3 && absPitchDiff(meta.pitchAt(1), meta.pitchAt(3)) < 3) {
          Violation violation = Violation.builderFor(ProtocolScanner.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE).withDetails("back snap")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withVL(0).build();
          Modules.violationProcessor().processViolation(violation);
        }
      }
    }

//    System.out.println("yawMotion: " + yawMotion);
//    System.out.println("pitchMotion: " + pitchMotion);
//    player.sendMessage(yawMotion + " " + pitchMotion);

    float yaw = movementData.rotationYaw;
    float pitch = movementData.rotationPitch;

    float[] yawHistory = meta.yawHistory;
    float[] pitchHistory = meta.pitchHistory;

    for (int i = 0; i < yawHistory.length - 1; i++) {
      yawHistory[i] = yawHistory[i + 1];
      pitchHistory[i] = pitchHistory[i + 1];
    }

    yawHistory[yawHistory.length - 1] = yaw;
    pitchHistory[pitchHistory.length - 1] = pitch;
    meta.snapAge++;
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    SnapMeta meta = metaOf(player);
    Location location = player.getLocation();
    float yaw = location.getYaw();
    float pitch = location.getPitch();

    if (System.currentTimeMillis() - meta.detectionTime < 2_500) {
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player)
        .withDefaultThreshold()
        .withMessage(COMMON_FLAG_MESSAGE)
        .withDetails(asWord(meta.degree) + "-tick block-aligned snap")
        .withVL(5)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        place.setCancelled(true);
      }
      return;
    }

    if (meta.snapAge > 20) {
      return;
    }

    float yawDiff = absYawDiff(meta.snapYaw, yaw);
    float pitchDiff = absPitchDiff(meta.snapPitch, pitch);
    boolean inCircle = yawDiff > (360f / 10f) || pitchDiff > (180f / 10f);

    if (inCircle) {
      return;
    }

    meta.detectionTime = System.currentTimeMillis();
  }

  private static String asWord(int degree) {
    switch (degree) {
      case 0:
        return "none";
      case 1:
        return "one";
      case 2:
        return "two";
      case 3:
        return "three";
      default:
        return "unknown";
    }
  }

  public static class SnapMeta extends CheckCustomMetadata {
    private static final int HISTORY_LENGTH = 6;
    private float snapYaw, snapPitch;
    private int snapAge;
    private int degree;
    private long detectionTime;
    private final float[] yawHistory = new float[HISTORY_LENGTH];
    private final float[] pitchHistory = new float[HISTORY_LENGTH];

    public float yawAt(int age) {
      int index = HISTORY_LENGTH - age;
      if (index < 0) {
        return 0;
      }
      return yawHistory[index];
    }

    public float pitchAt(int age) {
      int index = HISTORY_LENGTH - age;
      if (index < 0) {
        return 0;
      }
      return pitchHistory[index];
    }

    public float yawMotion(int age) {
      int index = HISTORY_LENGTH - age;
      if (index < 0) {
        return 0;
      }
      return absYawDiff(yawHistory[index], yawHistory[index - 1]);
    }

    public float pitchMotion(int age) {
      int index = HISTORY_LENGTH - age;
      if (index < 0) {
        return 0;
      }
      return absPitchDiff(pitchHistory[index], pitchHistory[index - 1]);
    }
  }

  private static float absYawDiff(float a, float b) {
    return Math.abs(yawDiff(a, b));
  }

  private static float absPitchDiff(float a, float b) {
    return Math.abs(pitchDiff(a, b));
  }

  private static float yawDiff(float alpha, float beta) {
    float phi = Math.abs(beta - alpha) % 360;
    return phi > 180 ? 360 - phi : phi;
  }

  private static float pitchDiff(float a, float b) {
    return Math.abs(a - b);
  }
}
