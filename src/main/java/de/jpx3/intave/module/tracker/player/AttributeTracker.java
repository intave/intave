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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.EntityAttributesPacket;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;

import java.util.List;

import static de.jpx3.intave.packet.ListenerPriority.LATER;

public final class AttributeTracker extends Module {
  @OnSend(priority = LATER)
  public void sentAttributes(User user, EntityAttributesPacket packet, PacketSendEvent event) {
    if (event.isCancelled() || packet.entityId() != user.player().getEntityId()) {
      return;
    }
    List<Attribute> updates = packet.attributes();
    Modules.feedback().synchronize(user, event, () -> {
      AbilityMetadata abilities = user.meta().abilities();
      for (Attribute update : updates) {
        Attribute tracked = abilities.findWireAttribute(update.attributeKey());
        if (tracked == null) {
          continue;
        }
        if (tracked == abilities.findAttribute("generic.movementSpeed")) {
          user.meta().movement().hasSprintSpeed = AbilityMetadata.hasSprintModifier(update.modifiers());
        }
        abilities.updateAttribute(tracked, update.baseValue(), update.modifiers());
      }
    });
  }
}
