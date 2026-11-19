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

package de.jpx3.intave.check;

import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.nayoro.NayoroEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.ProtocolLibSubscriptionLinker;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link CheckLinker} links checks to packet and event bindings, used by the {@link CheckService}.
 * The methods {@link CheckLinker#linkBukkitEventSubscriptions(Collection)} and {@link CheckLinker#linkPacketEventSubscriptions(Collection)}
 * take {@link Collection}s of {@link Check}s and forward them to the packet linkers and the {@link BukkitEventSubscriptionLinker}.
 * The {@link CheckLinker#removeBukkitEventSubscriptions(Collection)} and {@link CheckLinker#removePacketEventSubscriptions(Collection)} undo this procedure again.
 * <br>
 * <br>
 * Note: {@link CheckPart}s of a {@link Check} are not necessarily affected by {@link Check#performLinkage()}.
 * Although they *do* just forward their {@link CheckPart#enabled()} method to {@link Check#enabled()},
 * they could override it, meaning that a {@link CheckPart} can be active while its parent {@link Check} is not.
 *
 * @see Check
 * @see CheckPart
 * @see MetaCheck
 * @see MetaCheckPart
 * @see CheckService
 * @see ProtocolLibSubscriptionLinker
 * @see PacketPerfectSubscriptionLinker
 * @see BukkitEventSubscriptionLinker
 */
final class CheckLinker {
  private final Set<EventProcessor> linkedNayoroProcessors = identitySet();
  private final Set<EventProcessor> linkedPacketProcessors = identitySet();
  private final Set<EventProcessor> linkedPacketPerfectProcessors = identitySet();
  private final Set<EventProcessor> linkedBukkitProcessors = identitySet();

  public void linkNayoroEventSubscriptions(Collection<? extends Check> checks) {
    NayoroEventSubscriptionLinker nayoro = Modules.linker().nayoroEvents();
    iterativeLink(checks, linkedNayoroProcessors, nayoro::registerEventsIn);
  }

  public void removeNayoroEventSubscriptions(Collection<? extends Check> checks) {
    NayoroEventSubscriptionLinker packetSubscriptionLinker = Modules.linker().nayoroEvents();
    iterativeRemove(checks, linkedNayoroProcessors, packetSubscriptionLinker::unregisterEventsIn);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to link their proposed packet subscription methods.
   *
   * @param checks the collection of checks to perform linkage on
   */
  public void linkPacketEventSubscriptions(Collection<? extends Check> checks) {
    ProtocolLibSubscriptionLinker protocolLibSubscriptionLinker = Modules.linker().protocolLib();
    iterativeLink(checks, linkedPacketProcessors, protocolLibSubscriptionLinker::linkSubscriptionsIn);
    PacketPerfectSubscriptionLinker packetPerfectSubscriptionLinker = Modules.linker().packetPerfect();
    iterativeLink(checks, linkedPacketPerfectProcessors, packetPerfectSubscriptionLinker::linkSubscriptionsIn);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to remove the linkage of their proposed packet subscription methods.
   *
   * @param checks the collection of checks to remove linkage
   */
  public void removePacketEventSubscriptions(Collection<? extends Check> checks) {
    ProtocolLibSubscriptionLinker protocolLibSubscriptionLinker = Modules.linker().protocolLib();
    iterativeRemove(checks, linkedPacketProcessors, protocolLibSubscriptionLinker::removeSubscriptionsOf);
    PacketPerfectSubscriptionLinker packetPerfectSubscriptionLinker = Modules.linker().packetPerfect();
    iterativeRemove(checks, linkedPacketPerfectProcessors, packetPerfectSubscriptionLinker::removeSubscriptionsOf);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to link their proposed event subscription methods.
   *
   * @param checks the collection of checks to perform linkage on
   */
  public void linkBukkitEventSubscriptions(Collection<? extends Check> checks) {
    BukkitEventSubscriptionLinker bukkitEventLinker = Modules.linker().bukkitEvents();
    iterativeLink(checks, linkedBukkitProcessors, bukkitEventLinker::registerEventsIn);
  }

  /**
   * Iterate through the {@link Collection} of {@link Check}s -
   * constraint by {@link Check#performLinkage()} - and their check parts - constraint by {@link CheckPart#enabled()}
   * to remove the linkage of their proposed event subscription methods.
   *
   * @param checks the collection of checks to remove linkage
   */
  public void removeBukkitEventSubscriptions(Collection<? extends Check> checks) {
    BukkitEventSubscriptionLinker bukkitEventLinker = Modules.linker().bukkitEvents();
    iterativeRemove(checks, linkedBukkitProcessors, bukkitEventLinker::unregisterEventsIn);
  }

  private void iterativeLink(
    Collection<? extends Check> checks,
    Set<EventProcessor> linkedProcessors,
    Consumer<? super EventProcessor> applier
  ) {
    iterativeApply(checks, processor -> {
      if (!linkedProcessors.contains(processor)) {
        applier.accept(processor);
        linkedProcessors.add(processor);
      }
    });
  }

  private void iterativeRemove(
    Collection<? extends Check> checks,
    Set<EventProcessor> linkedProcessors,
    Consumer<? super EventProcessor> applier
  ) {
    for (Check check : checks) {
      removeIfLinked(check, linkedProcessors, applier);
      for (CheckPart<?> checkPart : check.checkParts()) {
        removeIfLinked(checkPart, linkedProcessors, applier);
      }
    }
  }

  private void removeIfLinked(
    EventProcessor processor,
    Set<EventProcessor> linkedProcessors,
    Consumer<? super EventProcessor> applier
  ) {
    if (linkedProcessors.contains(processor)) {
      applier.accept(processor);
      linkedProcessors.remove(processor);
    }
  }

  private static Set<EventProcessor> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private void iterativeApply(
    Collection<? extends Check> checks,
    Consumer<? super EventProcessor> applier
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
