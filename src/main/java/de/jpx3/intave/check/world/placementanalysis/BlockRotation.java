package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class BlockRotation extends MetaCheckPart<PlacementAnalysis, BlockRotation.BlockRotationMeta> {
  private final IntavePlugin plugin;

  public BlockRotation(PlacementAnalysis parentCheck) {
    super(parentCheck, BlockRotationMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receivePlacementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    MovementMetadata movement = user.meta().movement();
    AbilityMetadata abilities = user.meta().abilities();

    BlockRotationMeta meta = metaOf(user);
    BlockInteractionReader reader = PacketReaders.readerOf(packet);
    BlockPosition blockPosition = reader.blockPosition();

    if (blockPosition == null || event.isCancelled() || movement.isInVehicle()) {
      reader.release();
      return;
    }

    int enumDirection = reader.enumDirection();
    if (enumDirection == 255) {
      reader.release();
      return;
    }

    Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
    boolean clickableInteraction = BlockInteractionAccess.isClickable(clickedType);
    Material heldItemType = user.meta().inventory().heldItemType();
    boolean interactionIsPlacement = heldItemType != Material.AIR && heldItemType.isBlock() && !clickableInteraction && !abilities.inGameMode(GameMode.ADVENTURE);

    if (!interactionIsPlacement || enumDirection < 2) {
      reader.release();
      return;
    }

    meta.placementSpeedHistory.add(Math.min(1000, System.currentTimeMillis() - meta.lastPlacement));
    meta.lastPlacement = System.currentTimeMillis();
    double average = 500;

    if (meta.placementSpeedHistory.size() >= 8) {
      average = meta.placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
      meta.placementSpeedHistory.remove(0);
    }

    if (movement.rotationPitch > 85 && average < 400) {
      if (meta.vl++ > 3) {
        int pitch = (int) movement.rotationPitch;
        int ticksPerBlock = (int) (average / 50d);
        String details = "pitch of " + pitch + " placing blocks in " + ticksPerBlock + " t/b";
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE).withDetails(details)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDefaultThreshold().withVL(10).build();
        Modules.violationProcessor().processViolation(violation);
      }
    } else if (meta.vl > 0) {
      meta.vl *= 0.98;
      meta.vl -= 0.002;
    }
    reader.release();
  }

  public static class BlockRotationMeta extends CheckCustomMetadata {
    private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
    private long lastPlacement;
    private double vl;
  }
}
