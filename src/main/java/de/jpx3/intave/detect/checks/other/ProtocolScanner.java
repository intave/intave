package de.jpx3.intave.detect.checks.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.entity.Player;

public final class ProtocolScanner extends IntaveMetaCheck<ProtocolScanner.ProtocolScannerMeta> {
  private final IntavePlugin plugin;

  public ProtocolScanner(IntavePlugin plugin) {
    super("ProtocolScanner", "protocolscanner", ProtocolScannerMeta.class);
    this.plugin = plugin;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    float rotationPitch = event.getPacket().getFloat().read(1);
    if (Math.abs(rotationPitch) > 90.05f) {
      event.getPacket().getFloat().writeSafely(1, 0f);
      String message = "sent invalid rotation";
      String details = "pitch at " + MathHelper.formatDouble(rotationPitch, 4);
      Violation violation = Violation.fromType(ProtocolScanner.class)
        .withPlayer(player).withMessage(message).withDetails(details)
        .withDefaultThreshold().withVL(100)
        .build();
      plugin.violationProcessor().processViolation(violation);
    }
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "HELD_ITEM_SLOT"),
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = userOf(player);
    ProtocolScannerMeta meta = metaOf(user);
    int slot = packet.getIntegers().read(0);
    if (meta.lastSlot == slot && slot > 0) {
      Violation violation = Violation.fromType(ProtocolScanner.class)
        .withPlayer(player).withMessage("sent slot twice").withDetails("slot " + slot)
        .withDefaultThreshold().withVL(meta.slotPacketsSent > 4 ? 100 : 0)
        .build();
      plugin.violationProcessor().processViolation(violation);
    }
    meta.lastSlot = slot;
    meta.slotPacketsSent++;
  }

  public static class ProtocolScannerMeta extends UserCustomCheckMeta {
    private int lastSlot = 0;
    private int slotPacketsSent = 0;
  }
}