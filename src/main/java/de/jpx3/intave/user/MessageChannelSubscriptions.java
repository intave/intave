package de.jpx3.intave.user;

import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MessageChannelSubscriptions {
  private static final Set<Player> sibylRepo = GarbageCollector.watch(new HashSet<>());

  public static Collection<? extends Player> sibylReceiver() {
    return sibylRepo;
  }

  public static void setSibyl(Player player, boolean sibyl) {
    if (sibyl) {
      sibylRepo.add(player);
    } else {
      sibylRepo.remove(player);
    }
  }

  private static final Map<MessageChannel, Set<Player>> messageChannelSubscriptions = new ConcurrentHashMap<>();

  public static Set<Player> receiverOf(MessageChannel channel) {
    return messageChannelSubscriptions.computeIfAbsent(channel, theChannel -> new HashSet<>());
  }

  public static void setChannelActivation(Player player, MessageChannel channel, boolean status) {
    Set<Player> players = receiverOf(channel);
    if (status) {
      players.add(player);
    } else {
      players.remove(player);
    }
  }
}
