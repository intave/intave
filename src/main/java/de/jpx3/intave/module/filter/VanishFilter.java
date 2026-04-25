package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerInfoReader;
import de.jpx3.intave.packet.reader.PlayerInfoRemoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode.SURVIVAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class VanishFilter extends Filter {
  private final boolean disabled;
  private boolean yukiJoined = false;
  private long lastYukiJoin = 0;

  public VanishFilter(IntavePlugin plugin) {
    super("vanish");
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);

    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {

      if (yukiJoined) {
        // 25% chance that yuki will leave
        if (ThreadLocalRandom.current().nextInt(1, 100) <= 25) {
          Synchronizer.synchronizeDelayed(() -> {
            yukiJoined = false;
          }, 20 * ThreadLocalRandom.current().nextInt(60, 120));
        }
      } else {
        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean primeTime = hourOfDay >= 22 || hourOfDay <= 3;
        int chance = primeTime ? 10 : 2;
        if (ThreadLocalRandom.current().nextInt(1, 100) <= chance) {
          Synchronizer.synchronizeDelayed(() -> {
            yukiJoined = true;
            FAKE_DATA = new PlayerInfoData(
              new WrappedGameProfile(
                UUID.fromString("3ad99947-352f-4719-be96-9bfccc36ae71"),
                "funkeln"
              ),
              ThreadLocalRandom.current().nextInt(1, 100),
              SURVIVAL,
              null
            );
            lastYukiJoin = System.currentTimeMillis();
          }, 20 * ThreadLocalRandom.current().nextInt(60, 120));
        }
      }
      // every 15 minutes
    }, 20 * 60 * 15, 20 * 60 * 15);
  }

  private static PlayerInfoData FAKE_DATA = new PlayerInfoData(
    new WrappedGameProfile(
      UUID.fromString("3ad99947-352f-4719-be96-9bfccc36ae71"),
      "funkeln"
    ),
    ThreadLocalRandom.current().nextInt(1, 100),
    SURVIVAL,
    null
  );

  @PacketSubscription(
    packetsOut = {PLAYER_INFO}
  )
  public void on(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
//    System.out.println("Player info packet: " + packet);

    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    PlayerInfoReader reader = PacketReaders.readerOf(packet);
    Set<EnumWrappers.PlayerInfoAction> actions = reader.playerInfoActions();
    List<PlayerInfoData> playerInfos = reader.playerInfoData();

    for (EnumWrappers.PlayerInfoAction action : actions) {
      switch (action) {
        case ADD_PLAYER:
          playerInfos.forEach(data -> {
            UUID uuid = data.getProfile().getUUID();
            if (shownPlayers.contains(uuid)) {
              return;
            }
//            Synchronizer.synchronize(() -> {
//              player.sendMessage("Showing " + data.getProfile().getName() + " to you.");
//            });
//            System.out.println("Showing " + data.getProfile().getName() + " to you.");
            shownPlayers.add(uuid);
          });
          break;
        case UPDATE_GAME_MODE:
        case UPDATE_LATENCY:
          playerInfos.removeIf(playerInfo -> {
            UUID infoId = playerInfo.getProfile().getUUID();
            boolean toBeRemoved = !shownPlayers.contains(infoId);
            if (toBeRemoved) {
//              System.out.println("Hiding " + playerInfo.getProfile().getName() + " from " + player.getName());
            }
            return toBeRemoved;
          });
          break;
        case REMOVE_PLAYER:
          playerInfos.removeIf(playerInfoData -> {
            UUID uuid = playerInfoData.getProfile().getUUID();
            boolean wasVisible = shownPlayers.remove(uuid);
//            System.out.println("Hiding " + playerInfoData.getProfile().getName() + " from you (was visible: "+wasVisible +")");
//            Synchronizer.synchronize(() -> {
//              player.sendMessage("Hiding " + playerInfoData.getProfile().getName() + " from you (was visible: "+wasVisible +")");
//            });
            return !wasVisible;
          });
          break;
      }
    }

    if (playerInfos.isEmpty()) {
      event.setCancelled(true);
//      System.out.println("Cancelled empty player info packet");
    }

    Collections.shuffle(playerInfos);
//    lists.write(0, playerInfos);
    reader.writePlayerInfoData(playerInfos);
    reader.release();
  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    prioritySlot = PrioritySlot.EXTERNAL,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    PacketContainer packet = event.getPacket();
    String[] stuff = packet.getStringArrays().readSafely(0);
    if (stuff != null) {
      List<String> playerNames = Bukkit.getOnlinePlayers().stream()
        .map(Player::getName).collect(Collectors.toList());
      List<String> hiddenPlayers = Lists.newArrayList();
      for (String name : playerNames) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
          continue;
        }
        if (!shownPlayers.contains(target.getUniqueId())) {
          hiddenPlayers.add(name);
        }
      }
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff).filter(string -> !hiddenPlayers.contains(string)).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        packet.getStringArrays().writeSafely(0, newTabCompletions.toArray(new String[0]));
//        Synchronizer.synchronize(() -> {
//          player.sendMessage("Removed " + (stuff.length - newTabCompletions.size()) + " hidden players from tab complete");
//        });
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage("Tab: " + Arrays.toString(stuff) + " -> " + newTabCompletions);
//      });
    }
  }

//  @PacketSubscription(
//    packetsOut = {
//      SCOREBOARD_TEAM
//    }
//  )
//  public void onTeam(ProtocolPacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    User user = UserRepository.userOf(player);
//    ProtocolMetadata protocol = user.meta().protocol();
//    Set<UUID> shownPlayers = protocol.shownPlayers;
//    String teamName = packet.getStrings().readSafely(0);
//    shownPlayers.removeIf(uuid -> teamName.contains(Bukkit.getPlayer(uuid).getName()));
//  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO_REMOVE
    }
  )
  public void onRemoval(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;
    PlayerInfoRemoveReader reader = PacketReaders.readerOf(packet);
    List<UUID> uuids = reader.playersToRemove();
    uuids.removeIf(uuid -> !shownPlayers.contains(uuid));
    reader.release();
  }

  @Override
  protected boolean enabled() {
    return !disabled && super.enabled();
  }
}
