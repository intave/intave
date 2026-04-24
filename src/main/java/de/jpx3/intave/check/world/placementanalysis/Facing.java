package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Facing extends CheckPart<PlacementAnalysis> {
  private final IntavePlugin plugin;

  public Facing(PlacementAnalysis parentCheck) {
    super(parentCheck);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void checkPlacementVector(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
    if (packet.getFaceId() == 255) {
      return;
    }
    if (packet.getCursorPosition() == null) {
      return;
    }
    float f1 = packet.getCursorPosition().x;
    float f2 = packet.getCursorPosition().y;
    float f3 = packet.getCursorPosition().z;
    if (f1 < 0 || f2 < 0 || f3 < 0 || f1 > 1 || f2 > 1 || f3 > 1) {
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE)
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "analysis-thresholds.on-premise")
        .withVL(5).build();
      Modules.violationProcessor().processViolation(violation);
      //dmc14
//      user.nerf(AttackNerfStrategy.CANCEL_FIRST_HIT, "14");
//      user.nerf(AttackNerfStrategy.DMG_LIGHT, "14");
    }
  }

  @BukkitEventSubscription(
    ignoreCancelled = true
  )
  public void onPlace(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationMetadata = meta.violationLevel();
    if (place.isCancelled()) {
      violationMetadata.facingFailedCounter = -10;
    }
    int facingFailedCounter = violationMetadata.facingFailedCounter;
    if (facingFailedCounter > 3) {
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player)
        .withMessage(COMMON_FLAG_MESSAGE)
        .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
        .withDetails("repeated placement faults")
        .withVL(0)
        .build();
//      ViolationContext context = Modules.violationProcessor().processViolation(violation);
//      if (context.shouldCounterThreat()) {
//        place.setCancelled(true);
//      }
    }
  }
}
