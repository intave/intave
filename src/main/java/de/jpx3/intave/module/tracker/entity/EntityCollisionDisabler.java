package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;

import java.util.Optional;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityCollisionDisabler extends Module {
  private final static boolean DISABLE_ENTITY_COLLISIONS = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
  private final static int COLLISION_RULE_FIELD = (MinecraftVersions.VER1_13_0.atOrAbove() ? (MinecraftVersions.VER1_17_0.atOrAbove() ? 1 : 2) : 5);
  private static final boolean INDIRECT_SCOREBOARD_ACCESS = MinecraftVersions.VER1_17_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(PacketEvent event) {
    if (!DISABLE_ENTITY_COLLISIONS) {
      return;
    }
    PacketContainer packet = event.getPacket();
    if (INDIRECT_SCOREBOARD_ACCESS) {
      Optional<InternalStructure> optionalStructure = packet.getOptionalStructures().read(0);
      optionalStructure.ifPresent(internalStructure -> applyNoCollisionRule(internalStructure.getStrings()));
    } else {
      applyNoCollisionRule(packet.getStrings());
    }
  }

  private void applyNoCollisionRule(StructureModifier<String> strings) {
    strings.write(COLLISION_RULE_FIELD, "never");
  }
}