package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.math.MathHelper.averageOf;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketOrder extends MetaCheckPart<PlacementAnalysis, PacketOrder.PlacementOrderMeta> {
  public PacketOrder(PlacementAnalysis parentCheck) {
    super(parentCheck, PlacementOrderMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastMovePacket = System.currentTimeMillis();
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void checkPlacementPacketOrder(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
    PlacementOrderMeta meta = metaOf(player);

    long now = System.currentTimeMillis();
    if (packet.getFaceId() == 255 || user.meta().protocol().combatUpdate()) {
      return;
    }

    long timeDiff = now - meta.lastMovePacket;
    meta.placementDifferences.add(timeDiff);

    if (meta.placementDifferences.size() == 4) {
      double average = averageOf(meta.placementDifferences);

      if (average < 20) {
        long permutePacketIncrementDiff = now - meta.lastIncrement;

        if (permutePacketIncrementDiff > 20) {
          if (meta.packetOrderBalance++ >= 2) {
            Violation violation = Violation.builderFor(PlacementAnalysis.class)
              .forPlayer(player)
              .withMessage(COMMON_FLAG_MESSAGE)
              .withDetails("invalid packet order")
              .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "analysis-thresholds.on-premise")
              .withVL(2)
              .build();
            ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
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
  }

  public static class PlacementOrderMeta extends CheckCustomMetadata {
    public double packetOrderBalance;
    public long lastIncrement;
    public List<Long> placementDifferences = new ArrayList<>();

    public long lastMovePacket;
  }
}
