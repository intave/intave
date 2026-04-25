package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;

import java.util.Optional;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityCollisionDisabler extends Module {
  private static final boolean DISABLE_ENTITY_COLLISIONS = MinecraftVersions.VER1_9_0.atOrAbove();
  private static final int COLLISION_RULE_FIELD = (MinecraftVersions.VER1_13_0.atOrAbove() ? (MinecraftVersions.VER1_17_0.atOrAbove() ? 1 : 2) : 5);
  private static final boolean INDIRECT_SCOREBOARD_ACCESS = MinecraftVersions.VER1_17_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(ProtocolPacketEvent event) {
    if (!DISABLE_ENTITY_COLLISIONS) {
      return;
    }
    PacketContainer packet = event.getPacket();
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
}