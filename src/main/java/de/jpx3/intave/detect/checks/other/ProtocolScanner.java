package de.jpx3.intave.detect.checks.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.detect.MetaCheck;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.violation.Violation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ProtocolScanner extends MetaCheck<ProtocolScanner.ProtocolScannerMeta> {
  private final IntavePlugin plugin;

  public ProtocolScanner(IntavePlugin plugin) {
    super("ProtocolScanner", "protocolscanner", ProtocolScannerMeta.class);
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveRotation(PacketEvent event) {
    Player player = event.getPlayer();
    float rotationPitch = event.getPacket().getFloat().read(1);
    if (Math.abs(rotationPitch) > 90.05f) {
      event.getPacket().getFloat().writeSafely(1, 0f);
      String message = "sent invalid rotation";
      String details = "pitch at " + MathHelper.formatDouble(rotationPitch, 4);
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(100)
        .build();
      plugin.violationProcessor().processViolation(violation);
    }
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = userOf(player);
    ProtocolScannerMeta meta = metaOf(user);
    int slot = packet.getIntegers().read(0);
    if (meta.lastSlot == slot && slot > 0) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage("sent slot twice").withDetails("slot " + slot)
        .withVL(meta.slotPacketsSent > 4 ? 100 : 0)
        .build();
      plugin.violationProcessor().processViolation(violation);
    }
    meta.lastSlot = slot;
    meta.slotPacketsSent++;
  }

  private final static boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  private static Class<?> enumMainHandClass;

  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      if (enumMainHandClass == null) {
        enumMainHandClass = Lookup.serverClass("EnumMainHand");
      }
      HandSlot sentHand = packet.getEnumModifier(HandSlot.class, enumMainHandClass).read(0);
      if (!equalHand(player.getMainHand(), sentHand)) {
        return;
      }
    }
    MovementMetadata movementData = user.meta().movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    double distanceMoved = Hypot.fast(movementData.motionX(), movementData.motionZ());
    if (movementData.inWeb || movementData.recentlyEncounteredFlyingPacket(2)) {
      return;
    }
    if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > 0.1) {
//      Violation violation = Violation.builderFor(ProtocolScanner.class)
//        .forPlayer(player)
//        .withMessage("updated client settings whilst walking")
//        .withDetails("version " + clientData.versionString())
//        .withVL(0)
//        .build();
//      plugin.violationProcessor().processViolation(violation);
      event.setCancelled(true);
    }
  }

  private boolean equalHand(Object bukkitHand, HandSlot hand) {
    return bukkitHand == MainHand.LEFT && hand == HandSlot.LEFT
      || bukkitHand == MainHand.RIGHT && hand == HandSlot.RIGHT;
  }

  public static class ProtocolScannerMeta extends CheckCustomMetadata {
    private int lastSlot = 0;
    private int slotPacketsSent = 0;
  }

  @KeepEnumInternalNames
  public enum HandSlot {
    LEFT,
    RIGHT
  }
}