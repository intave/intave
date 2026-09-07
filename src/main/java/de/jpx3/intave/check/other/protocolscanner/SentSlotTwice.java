package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
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
  public void receiveSlotSwitch(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    handleSlotSwitch(event.getPlayer(), packet.getIntegers().read(0));
  }

  /**
   * PacketEvents entry point. No engine neutral view exists for the held item change packet and
   * the check only reads the single slot field, so it is pulled from the wrapper here.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player)) {
      return;
    }
    handleSlotSwitch((Player) rawPlayer, new WrapperPlayClientHeldItemChange(event).getSlot());
  }

  /** Engine independent body; the subscription only ever needed the switched-to slot. */
  private void handleSlotSwitch(Player player, int slot) {
    User user = userOf(player);
    SentSlotTwiceMeta meta = metaOf(user);
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