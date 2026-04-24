package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.HELD_ITEM_SLOT_IN;

public final class SentSlotTwice extends MetaCheckPart<ProtocolScanner, SentSlotTwice.SentSlotTwiceMeta> {
  private final int vl;

  public SentSlotTwice(ProtocolScanner parentCheck) {
    super(parentCheck, SentSlotTwiceMeta.class);
    this.vl = parentCheck.configuration().settings().intBy("sst-vl", parentCheck.configuration().settings().intBy("check_sent_slot_twice_vl", 100));
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(ProtocolPacketEvent event, WrapperPlayClientHeldItemChange packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    SentSlotTwiceMeta meta = metaOf(user);
    int slot = packet.getSlot();
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
