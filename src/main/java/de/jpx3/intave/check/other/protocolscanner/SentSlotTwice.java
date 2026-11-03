/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.plib.reader.HeldItemSlotReader;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.packet.contract.PacketType.Client.HELD_ITEM_SLOT_IN;

public final class SentSlotTwice extends PlayerCheckPart<ProtocolScanner> {
  private final int vl;
  private int lastSlot;
  private int slotPacketsSent;

  public SentSlotTwice(User user, ProtocolScanner parentCheck) {
    super(user, parentCheck);
    this.vl = parentCheck.configuration().settings().intBy("sst-vl", parentCheck.configuration().settings().intBy("check_sent_slot_twice_vl", 100));
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(HeldItemSlotReader reader) {
    if (vl == 0) {
      return;
    }

    Player player = owningUser().player();
    int slot = reader.slot();
    if (lastSlot == slot && slot > 0) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage("sent slot twice").withDetails("slot " + slot)
        .withVL(slotPacketsSent > 4 ? vl : 0)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
    lastSlot = slot;
    slotPacketsSent++;
  }
}
