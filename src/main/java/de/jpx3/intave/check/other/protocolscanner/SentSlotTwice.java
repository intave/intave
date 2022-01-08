package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.HELD_ITEM_SLOT;

public final class SentSlotTwice extends MetaCheckPart<ProtocolScanner, SentSlotTwice.SentSlotTwiceMeta> {
  private final int vl;

  public SentSlotTwice(ProtocolScanner parentCheck) {
    super(parentCheck, SentSlotTwiceMeta.class);

    this.vl = parentCheck.configuration().settings().intBy("check_sent_slot_twice_vl");
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
    SentSlotTwiceMeta meta = metaOf(user);
    int slot = packet.getIntegers().read(0);
    if (meta.lastSlot == slot && slot > 0) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage("sent slot twice").withDetails("slot " + slot)
        .withVL(meta.slotPacketsSent > 4 ? vl : 0)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
    meta.lastSlot = slot;
    meta.slotPacketsSent++;
  }

  @Override
  public boolean enabled() {
    return super.enabled() && vl != 0;
  }

  public static final class SentSlotTwiceMeta extends CheckCustomMetadata {
    public int lastSlot = 0;
    public int slotPacketsSent = 0;
  }
}