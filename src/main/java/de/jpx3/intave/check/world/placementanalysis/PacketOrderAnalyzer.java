package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
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
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketOrderAnalyzer extends MetaCheckPart<PlacementAnalysis, PacketOrderAnalyzer.PlacementOrderMeta> {
  private final IntavePlugin plugin;

  public PacketOrderAnalyzer(PlacementAnalysis parentCheck) {
    super(parentCheck, PlacementOrderMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastMovePacket = System.currentTimeMillis();
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void checkPlacementPacketOrder(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    PlacementOrderMeta meta = metaOf(player);

    long now = System.currentTimeMillis();
    if (blockingPlacementPacket(packet) || user.meta().protocol().combatUpdate()) {
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

  private double averageOf(List<? extends Number> data) {
    double sum = 0;
    for (Number element : data) {
      sum += element.doubleValue();
    }
    if (sum == 0) {
      return 0;
    }
    return sum / data.size();
  }

  private boolean blockingPlacementPacket(PacketContainer packet) {
    Integer integer = packet.getIntegers().readSafely(0);
    return integer != null && integer == 255;
  }

  public static class PlacementOrderMeta extends CheckCustomMetadata {
    public double packetOrderBalance;
    public long lastIncrement;
    public List<Long> placementDifferences = new ArrayList<>();

    public long lastMovePacket;
  }
}
