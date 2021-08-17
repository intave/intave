package de.jpx3.intave.detect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * A {@link CheckLinker} is a utility class for the {@link CheckService}.
 * The methods {@link CheckLinker#linkBukkitEventSubscriptions(Collection)} and {@link CheckLinker#linkPacketEventSubscriptions(Collection)}
 * take {@link Collection}s of {@link Check}s and forward them to the {@link PacketSubscriptionLinker} and the {@link BukkitEventSubscriptionLinker}.
 * The {@link CheckLinker#removeBukkitEventSubscriptions(Collection)} and {@link CheckLinker#removePacketEventSubscriptions(Collection)} undo this procedure again.
 * <br />
 * <br />
 * Note: {@link CheckPart}s of a {@link Check} are not necessarily affected by {@link Check#performLinkage()}.
 * Although they *do* just forward their {@link CheckPart#enabled()} method to {@link Check#enabled()},
 * they could override it, meaning that a {@link CheckPart} can be active while its parent {@link Check} is not.
 *
 * @see Check
 * @see CheckPart
 * @see MetaCheck
 * @see MetaCheckPart
 * @see CheckService
 * @see PacketSubscriptionLinker
 * @see BukkitEventSubscriptionLinker
 */
public final class CheckLinker {
  private final IntavePlugin plugin;

  public CheckLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to link their proposed packet subscription methods.
   * @param checks the collection of checks to perform linkage on
   */
  public void linkPacketEventSubscriptions(Collection<Check> checks) {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    iterativeApply(checks, packetSubscriptionLinker::linkSubscriptionsIn);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to remove the linkage of their proposed packet subscription methods.
   * @param checks the collection of checks to remove linkage
   */
  public void removePacketEventSubscriptions(Collection<Check> checks) {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    iterativeApply(checks, packetSubscriptionLinker::removeSubscriptionsOf);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to link their proposed event subscription methods.
   * @param checks the collection of checks to perform linkage on
   */
  public void linkBukkitEventSubscriptions(Collection<Check> checks) {
    BukkitEventSubscriptionLinker bukkitEventLinker = plugin.eventLinker();
    iterativeApply(checks, bukkitEventLinker::registerEventsIn);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to remove the linkage of their proposed event subscription methods.
   * @param checks the collection of checks to remove linkage
   */
  public void removeBukkitEventSubscriptions(Collection<Check> checks) {
    BukkitEventSubscriptionLinker bukkitEventLinker = plugin.eventLinker();
    iterativeApply(checks, bukkitEventLinker::unregisterEventsIn);
  }

  private void iterativeApply(
    Collection<Check> checks,
    Consumer<EventProcessor> applier
  ) {
    for (Check check : checks) {
      if (check.performLinkage()) {
        applier.accept(check);
      }
      for (CheckPart<?> checkPart : check.checkParts()) {
        if (checkPart.enabled()) {
          applier.accept(checkPart);
        }
      }
    }
  }
}
