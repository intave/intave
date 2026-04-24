package de.jpx3.intave.check.combat;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ClickSpeedLimiter extends MetaCheck<ClickSpeedLimiter.ClickSpeedLimiterMeta> {
  private final IntavePlugin plugin;
  private final int maxCPS;

  public ClickSpeedLimiter(IntavePlugin plugin) {
    super("ClickSpeedLimiter", "clickspeedlimiter", ClickSpeedLimiterMeta.class);
    this.plugin = plugin;
    this.maxCPS = configuration().settings().intInBoundsBy("max-cps", 8, 40, 20);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void attackEntity(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ClickSpeedLimiterMeta meta = metaOf(user);

    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
        meta.attackCountArray[meta.attackArrayIndex]++;
      } else {
        meta.attacksDuringFlyingPackets.add(System.currentTimeMillis());
      }
    }

    double timeDiff = (System.currentTimeMillis() - meta.lastFlag) / 1000d;
    if (timeDiff < 1d) {
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void clientTickUpdate(ProtocolPacketEvent event) {
    // TODO: Check rod right click spam
    Player player = event.getPlayer();
    User user = userOf(player);
    ClickSpeedLimiterMeta meta = metaOf(user);
    PacketTypeCommon pt = event.getPacketType();

    if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
      // 1.8
      meta.countAccuratePositionPackets = 20;
    } else {
      // 1.9+
      MovementMetadata movementData = user.meta().movement();

      if (movementData.receivedFlyingPacketIn(0)
        || meta.lastMovePacketType == PacketType.Play.Client.PLAYER_FLYING
        || meta.lastMovePacketType == PacketType.Play.Client.PLAYER_ROTATION
      ) {
        meta.countAccuratePositionPackets = 0;

        long now = System.currentTimeMillis();
        long timeDiff = now - meta.lastTickTimeStamp;
        int ticks = (int) (timeDiff / 50f);

        int newIndex = meta.attackArrayIndex + ticks;
        while (newIndex > 19)
          newIndex -= 20;
        while (newIndex < 0)
          newIndex += 20;
        meta.attackArrayIndex = newIndex;

        // Delete all hits from the attackCountArray that were between the last packet move and now
        // Fill the attackCountArray with all entries from the attacksDuringFlyingPackets list

        //TODO: If you stand still and move on again, the cps on 1.9+ go up although they are not that high
        for (int i = 1; i <= ticks; i++) {
          int index = meta.attackArrayIndex - i;

          while (index > 19)
            index -= 20;
          while (index < 0)
            index += 20;

          meta.attackCountArray[index] = 0;
//          player.sendMessage("" + index + " " + meta.attackArrayIndex);
        }

        for (long timeStampFromAttack : meta.attacksDuringFlyingPackets) {
          timeDiff = now - timeStampFromAttack;
          ticks = (int) (timeDiff / 50f);

          if (ticks < 20) {
            int index = meta.attackArrayIndex - ticks;

            while (index > 19)
              index -= 20;
            while (index < 0)
              index += 20;

            meta.attackCountArray[index]++;
          }
        }
      } else {
        meta.attackCountArray[meta.attackArrayIndex] = meta.attacksDuringFlyingPackets.size();
        meta.countAccuratePositionPackets++;
      }
    }

    int sum = 0;
    for (int attacks : meta.attackCountArray) {
      sum += attacks;
    }
    if (sum > maxCPS) {
      int addedVL = 1;
      if (meta.countAccuratePositionPackets > 20) {
        // punishment can be 100% sure here
        addedVL = 3;
      }

      Violation violation = Violation.builderFor(ClickSpeedLimiter.class)
        .forPlayer(player)
        .withMessage("attacked too quickly")
        .withDetails(sum + " c/s")
        .withVL(addedVL)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        meta.lastFlag = System.currentTimeMillis();
      }
    }

//    player.sendMessage("" + sum);
    prepareNextTick(meta, pt);
  }

  private void prepareNextTick(ClickSpeedLimiterMeta meta, PacketTypeCommon pt) {
    meta.attacksDuringFlyingPackets.clear();
    meta.lastMovePacketType = pt;

    meta.attackArrayIndex++;
    if (meta.attackArrayIndex > 19)
      meta.attackArrayIndex = 0;

    meta.attackCountArray[meta.attackArrayIndex] = 0;
    meta.lastTickTimeStamp = System.currentTimeMillis();
  }

  public static final class ClickSpeedLimiterMeta extends CheckCustomMetadata {
    private long lastFlag;
    PacketTypeCommon lastMovePacketType;
    List<Long> attacksDuringFlyingPackets = new ArrayList<>();
    int[] attackCountArray = new int[20];
    int attackArrayIndex = 0;
    long lastTickTimeStamp = System.currentTimeMillis();
    int countAccuratePositionPackets;
  }
}
