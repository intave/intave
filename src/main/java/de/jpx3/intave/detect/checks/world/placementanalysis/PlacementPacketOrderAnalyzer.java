package de.jpx3.intave.detect.checks.world.placementanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.world.PlacementAnalysis;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.event.violation.ViolationContext;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.RotationMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.detect.checks.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.event.packet.PacketId.Client.*;

public final class PlacementPacketOrderAnalyzer extends IntaveMetaCheckPart<PlacementAnalysis, PlacementPacketOrderAnalyzer.PlacementOrderMeta> {
  private final IntavePlugin plugin;

  public PlacementPacketOrderAnalyzer(PlacementAnalysis parentCheck) {
    super(parentCheck, PlacementOrderMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, BLOCK_PLACE
    }
  )
  public void checkPlacementPacketOrder(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    PlacementOrderMeta meta = metaOf(player);

    PacketType packetType = event.getPacketType();
    long now = AccessHelper.now();
    if (packetType == PacketType.Play.Client.BLOCK_PLACE) {
      if (blockingPlacementPacket(packet)) {
        return;
      }

      long timeDiff = now - meta.lastMovePacket;
      meta.placementDifferences.add(timeDiff);

      if (meta.placementDifferences.size() == 4) {
        double average = RotationMathHelper.averageOf(meta.placementDifferences);

        if (average < 20) {
          long permutePacketIncrementDiff = now - meta.lastIncrement;

          if (permutePacketIncrementDiff > 20) {
            if (meta.packetOrderBalance++ >= 2) {
              Violation violation = Violation.builderFor(PlacementAnalysis.class)
                .forPlayer(player)
                .withMessage(COMMON_FLAG_MESSAGE)
                .withVL(2)
                .build();
              ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);
              if (violationContext.violationLevelAfter() > 5) {
                //dmc2
                parentCheck().applyPlacementAnalysisDamageCancel(user, "2");
              }
            }
            meta.lastIncrement = now;
          }

        } else if (meta.packetOrderBalance >= 0) {
          meta.packetOrderBalance--;
        }

        meta.placementDifferences.clear();
      }
    } else {
      meta.lastMovePacket = now;
    }
  }

  private boolean blockingPlacementPacket(PacketContainer packet) {
    Integer integer = packet.getIntegers().readSafely(0);
    return integer != null && integer == 255;
  }

  public static class PlacementOrderMeta extends UserCustomCheckMeta {
    public double packetOrderBalance;
    public long lastIncrement;
    public List<Long> placementDifferences = new ArrayList<>();

    public long lastMovePacket;
  }
}
