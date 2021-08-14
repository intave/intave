package de.jpx3.intave.detect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;

import java.util.Collection;

public final class CheckLinker {
  private final IntavePlugin plugin;

  public CheckLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void linkPacketEventSubscriptions(Collection<Check> checks) {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    for (Check check : checks) {
      if (check.performLinkage()) {
        packetSubscriptionLinker.linkSubscriptionsIn(check);
      }
      for (CheckPart<?> checkPart : check.checkParts()) {
        if (checkPart.enabled()) {
          packetSubscriptionLinker.linkSubscriptionsIn(checkPart);
        }
      }
    }
  }

  public void removePacketEventSubscriptions(Collection<Check> checks) {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    for (Check check : checks) {
      if (check.performLinkage()) {
        packetSubscriptionLinker.removeSubscriptionsOf(check);
      }
      for (CheckPart<?> checkPart : check.checkParts()) {
        if (checkPart.enabled()) {
          packetSubscriptionLinker.removeSubscriptionsOf(checkPart);
        }
      }
    }
  }

  public void linkBukkitEventSubscriptions(Collection<Check> checks) {
    BukkitEventLinker bukkitEventLinker = plugin.eventLinker();
    for (Check check : checks) {
      if (check.performLinkage()) {
        bukkitEventLinker.registerEventsIn(check);
      }
      for (CheckPart<?> checkPart : check.checkParts()) {
        if (checkPart.enabled()) {
          bukkitEventLinker.registerEventsIn(checkPart);
        }
      }
    }
  }

  public void removeBukkitEventSubscriptions(Collection<Check> checks) {
    BukkitEventLinker bukkitEventLinker = plugin.eventLinker();
    for (Check check : checks) {
      if (check.performLinkage()) {
        bukkitEventLinker.unregisterEventsIn(check);
      }
      for (CheckPart<?> checkPart : check.checkParts()) {
        if (checkPart.enabled()) {
          bukkitEventLinker.unregisterEventsIn(checkPart);
        }
      }
    }
  }
}
