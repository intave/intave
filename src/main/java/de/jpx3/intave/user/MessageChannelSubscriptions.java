package de.jpx3.intave.user;

import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MessageChannelSubscriptions {
  /*
   * We are mainly iterating over players, so a List makes more sense than a Set or Map.
   */

  private static final Collection<Player> sibylRepo = GarbageCollector.watch(new CopyOnWriteArrayList<>());

  public static Collection<Player> sibylReceivers() {
    return sibylRepo;
  }

  public static void setSibyl(Player player, boolean sibyl) {
    Collection<Player> sibylReceiver = sibylReceivers();
    if (sibyl) {
      if (!sibylReceiver.contains(player)) {
        sibylReceiver.add(player);
      }
    } else {
      sibylReceiver.remove(player);
    }
  }

  private static final Map<MessageChannel, Collection<Player>> messageChannelSubscriptions = new ConcurrentHashMap<>();

  public static Collection<Player> receiverOf(MessageChannel channel) {
    return messageChannelSubscriptions.computeIfAbsent(channel, theChannel -> GarbageCollector.watch(new CopyOnWriteArrayList<>()));
  }

  public static void setChannelActivation(Player player, MessageChannel channel, boolean status) {
    Collection<Player> players = receiverOf(channel);
    if (status) {
      if (!players.contains(player)) {
        players.add(player);
      }
    } else {
      players.remove(player);
    }
  }
}
