package de.jpx3.intave.event.entity;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketEventSubscriber;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.reflect.ReflectiveScoreboardAccess;
import de.jpx3.intave.tools.sync.Synchronizer;
import org.bukkit.entity.Player;

import static de.jpx3.intave.event.packet.PacketId.Server.SCOREBOARD_TEAM;

public final class EntityNoCollisionService implements PacketEventSubscriber {
  private final static String SCOREBOARD_NAME = "INTAVE";
  private final static int COLLISION_RULE_FIELD = MinecraftVersions.VER1_13_0.atOrAbove() ? 2 : 5;

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

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      SCOREBOARD_TEAM
    }
  )
  public void receiveScoreboardUpdate(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    packet.getStrings().write(COLLISION_RULE_FIELD, "never");
  }

  private void disableCollisions(Player player, String entityScoreboardName) {
    Synchronizer.synchronize(() -> ReflectiveScoreboardAccess.applyNoCollisionRule(player, SCOREBOARD_NAME, entityScoreboardName));
  }
}