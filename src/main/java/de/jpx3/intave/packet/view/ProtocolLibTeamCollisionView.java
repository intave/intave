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

package de.jpx3.intave.packet.view;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;

import java.util.Optional;

/**
 * {@link TeamCollisionView} backed by ProtocolLib.
 * <p>
 * Carries the version dependent field layout the collision disabler used before the view existed,
 * unchanged. 1.17 moved the team's parameters into a nested optional structure, so from there on
 * the rule has to be reached through that structure; before 1.17 it is a plain string field of the
 * packet, at index 2 from 1.13 and at index 5 on the older layout.
 * <p>
 * On the flat layouts the write is unconditional, as it was: a packet whose mode does not carry
 * team parameters never serialises the field, so writing it has no effect on the wire.
 */
public final class ProtocolLibTeamCollisionView implements TeamCollisionView {

  private static final int COLLISION_RULE_FIELD =
    MinecraftVersions.VER1_13_0.atOrAbove() ? (MinecraftVersions.VER1_17_0.atOrAbove() ? 1 : 2) : 5;
  private static final boolean INDIRECT_SCOREBOARD_ACCESS = MinecraftVersions.VER1_17_0.atOrAbove();

  private final PacketContainer packet;

  public ProtocolLibTeamCollisionView(PacketEvent event) {
    this.packet = event.getPacket();
  }

  @Override
  public void disableCollisions() {
    if (INDIRECT_SCOREBOARD_ACCESS) {
      //noinspection OptionalAssignedToNull
      if (packet.getSpecificModifier(Optional.class).read(0) != null) {
        Optional<InternalStructure> optionalStructure = packet.getOptionalStructures().read(0);
        if (optionalStructure.isPresent()) {
          InternalStructure structure = optionalStructure.get();
          StructureModifier<String> strings = structure.getStrings();
          applyNoCollisionRule(strings);
        }
      }
    } else {
      applyNoCollisionRule(packet.getStrings());
    }
  }

  private void applyNoCollisionRule(StructureModifier<String> strings) {
    strings.write(COLLISION_RULE_FIELD, "never");
  }

  @Override
  public void release() {
    // Nothing held: ProtocolLib packets are mutated in place, so the write is already visible.
  }
}
