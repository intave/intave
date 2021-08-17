package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;

import java.util.Optional;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityNoCollisionService implements PacketEventSubscriber {
  private final static String SCOREBOARD_NAME = "INTAVE";
  private final static int COLLISION_RULE_FIELD = (MinecraftVersions.VER1_13_0.atOrAbove() ? (MinecraftVersions.VER1_17_0.atOrAbove() ? 1 : 2) : 5);

  public EntityNoCollisionService(IntavePlugin plugin) {
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

//  @PacketSubscription(
//    priority = ListenerPriority.HIGHEST,
//    packetsOut = {
//      PLAYER_INFO
//    }
//  )
//  public void receiveTabListName(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    EnumWrappers.PlayerInfoAction action = packet.getPlayerInfoAction().read(0);
//    if (action != EnumWrappers.PlayerInfoAction.ADD_PLAYER) {
//      return;
//    }
//    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
//    for (PlayerInfoData playerInfoData : playerInformationList) {
//      WrappedGameProfile profile = playerInfoData.getProfile();
//      String name = profile.getName();
//      disableCollisions(player, name);
//    }
//  }
//
//  @PacketSubscription(
//    priority = ListenerPriority.HIGHEST,
//    packetsOut = {
//      SPAWN_ENTITY_LIVING
//    }
//  )
//  public void receiveEntitySpawn(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    disableCollisions(player, packet.getUUIDs().read(0).toString());
//  }

  private static final boolean INDIRECT_SCOREBOARD_ACCESS = MinecraftVersions.VER1_17_0.atOrAbove();

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(PacketEvent event) {
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