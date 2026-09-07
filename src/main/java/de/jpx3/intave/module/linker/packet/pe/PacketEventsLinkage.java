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

package de.jpx3.intave.module.linker.packet.pe;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * Registers Intave packet subscriptions on PacketEvents.
 * <p>
 * One PacketEvents listener is registered per priority instead of per subscription: PacketEvents
 * walks every registered listener for every packet, so registering hundreds of listeners would cost
 * a linear scan per packet. Subscriptions are bucketed by packet type inside the listener, which
 * turns dispatch into a single set lookup.
 */
public final class PacketEventsLinkage {

  private static final PacketEventsLinkage INSTANCE = new PacketEventsLinkage();

  private final List<Registration> registrations = new CopyOnWriteArrayList<>();
  private final Set<PacketListenerPriority> installedPriorities = new HashSet<>();

  private PacketEventsLinkage() {
  }

  public static PacketEventsLinkage linkage() {
    return INSTANCE;
  }

  /** Receives a packet that matched a subscription. */
  @FunctionalInterface
  public interface Handler {
    void handle(PacketEventWrapper event);
  }

  /**
   * Subscribes to the given Intave packets.
   *
   * @return true when at least one packet type resolved on the running PacketEvents version.
   */
  public boolean subscribe(
    PacketId.Client[] clientPackets,
    PacketId.Server[] serverPackets,
    ListenerPriority priority,
    boolean ignoreCancelled,
    String identifier,
    Handler handler
  ) {
    Set<PacketTypeCommon> types = new HashSet<>();
    if (clientPackets != null) {
      for (PacketId.Client packet : clientPackets) {
        types.addAll(PacketEventsIdMapper.typesOf(packet));
      }
    }
    if (serverPackets != null) {
      for (PacketId.Server packet : serverPackets) {
        types.addAll(PacketEventsIdMapper.typesOf(packet));
      }
    }
    if (types.isEmpty()) {
      return false;
    }
    PacketListenerPriority nativePriority = translate(priority);
    registrations.add(new Registration(types, nativePriority, ignoreCancelled, identifier, handler));
    installListenerFor(nativePriority);
    return true;
  }

  /** Drops every subscription; used when Intave reloads its modules. */
  public void unsubscribeAll() {
    registrations.clear();
  }

  public int subscriptionCount() {
    return registrations.size();
  }

  private synchronized void installListenerFor(PacketListenerPriority priority) {
    if (!installedPriorities.add(priority)) {
      return;
    }
    if (!PacketEventsBootstrap.available() || PacketEvents.getAPI() == null) {
      return;
    }
    PacketEvents.getAPI().getEventManager().registerListener(new Dispatcher(priority));
  }

  private static PacketListenerPriority translate(ListenerPriority priority) {
    if (priority == null) {
      return PacketListenerPriority.NORMAL;
    }
    switch (priority.name()) {
      case "LOWEST":
        return PacketListenerPriority.LOWEST;
      case "LOW":
        return PacketListenerPriority.LOW;
      case "HIGH":
        return PacketListenerPriority.HIGH;
      case "HIGHEST":
        return PacketListenerPriority.HIGHEST;
      case "MONITOR":
        return PacketListenerPriority.MONITOR;
      default:
        return PacketListenerPriority.NORMAL;
    }
  }

  /**
   * The event whose ignore flag this thread has already consumed.
   * <p>
   * ProtocolLib consumes the flag exactly once per packet because a single
   * {@code ForwardingPacketAdapter} is registered per packet type. PacketEvents instead walks one
   * {@link Dispatcher} per installed priority over the same event, so the flag has to be consumed by
   * the first dispatcher and remembered for the rest. A connection's packets are decoded
   * sequentially on their own netty thread and every dispatcher for one packet runs inside that one
   * call, which makes a thread local identity marker sufficient. Held as a weak reference so a
   * parked event never keeps a buffer alive after the connection is gone.
   */
  private static final ThreadLocal<WeakReference<Object>> CONSUMED_INBOUND = new ThreadLocal<>();

  /**
   * Mirrors {@code ForwardingPacketAdapter#onPacketReceiving}: a packet that Intave itself replayed
   * into the server skips every Intave subscriber exactly once.
   * <p>
   * There is deliberately no login exemption like the outbound path in that adapter has.
   * {@link UserRepository#userOf(Player)} is a pure lookup that answers with a no-op fallback user
   * when no user is registered yet, so a pre-login packet reports "do not ignore" without the
   * special case.
   *
   * @return true when this packet must not reach any subscription.
   */
  private static boolean skipInbound(PacketReceiveEvent event) {
    WeakReference<Object> consumed = CONSUMED_INBOUND.get();
    if (consumed != null) {
      if (consumed.get() == event) {
        return true;
      }
      // A different packet: drop the stale marker rather than let it linger on this thread.
      CONSUMED_INBOUND.remove();
    }
    Object player = event.getPlayer();
    if (!(player instanceof Player)) {
      return false;
    }
    User user = UserRepository.userOf((Player) player);
    if (!user.shouldIgnoreNextInboundPacket()) {
      return false;
    }
    user.receiveNextInboundPacketAgain();
    CONSUMED_INBOUND.set(new WeakReference<>(event));
    return true;
  }

  /**
   * Mirrors {@code ForwardingPacketAdapter#onPacketSending}. The flag is only read, never cleared:
   * clearing it is commented out on the ProtocolLib path, and an outbound gate that consumed itself
   * would open again halfway through a burst the caller meant to hide in full.
   */
  private static boolean skipOutbound(PacketSendEvent event) {
    Object player = event.getPlayer();
    if (!(player instanceof Player)) {
      return false;
    }
    return UserRepository.userOf((Player) player).shouldIgnoreNextOutboundPacket();
  }

  private final class Dispatcher extends PacketListenerAbstract {

    private Dispatcher(PacketListenerPriority priority) {
      super(priority);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
      if (skipInbound(event)) {
        return;
      }
      dispatch(new PacketEventWrapper(event), event.getPacketType(), event.isCancelled());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
      if (skipOutbound(event)) {
        return;
      }
      dispatch(new PacketEventWrapper(event), event.getPacketType(), event.isCancelled());
    }

    private void dispatch(PacketEventWrapper wrapper, PacketTypeCommon type, boolean cancelled) {
      for (Registration registration : registrations) {
        if (registration.priority != getPriority()) {
          continue;
        }
        if (cancelled && registration.ignoreCancelled) {
          continue;
        }
        if (!registration.types.contains(type)) {
          continue;
        }
        registration.hits.increment();
        registration.handler.handle(wrapper);
      }
    }
  }

  private static final class Registration {
    /**
     * Dispatch count. The point of the whole port is that a twin behaves like its ProtocolLib
     * original, and the way a twin fails quietly is by never running at all - a packet id that
     * bound the wrong constant raises nothing and logs nothing. A hit of zero after a session with
     * real traffic is that failure, made visible.
     */
    private final LongAdder hits = new LongAdder();
    private final Set<PacketTypeCommon> types;
    private final PacketListenerPriority priority;
    private final boolean ignoreCancelled;
    private final String identifier;
    private final Handler handler;

    private Registration(
      Set<PacketTypeCommon> types,
      PacketListenerPriority priority,
      boolean ignoreCancelled,
      String identifier,
      Handler handler
    ) {
      this.types = types;
      this.priority = priority;
      this.ignoreCancelled = ignoreCancelled;
      this.identifier = identifier;
      this.handler = handler;
    }
  }

  /** One registered subscription, for diagnostics. */
  public static final class SubscriptionStat {
    public final String identifier;
    public final String priority;
    public final int boundTypes;
    public final long hits;

    private SubscriptionStat(String identifier, String priority, int boundTypes, long hits) {
      this.identifier = identifier;
      this.priority = priority;
      this.boundTypes = boundTypes;
      this.hits = hits;
    }
  }

  /** @return every PacketEvents subscription with the packet types it bound and its dispatch count. */
  public List<SubscriptionStat> statistics() {
    List<SubscriptionStat> stats = new ArrayList<>(registrations.size());
    for (Registration registration : registrations) {
      stats.add(new SubscriptionStat(
        registration.identifier,
        registration.priority.name(),
        registration.types.size(),
        registration.hits.sum()
      ));
    }
    return stats;
  }

  /** Diagnostics: every packet name that could not be resolved on this PacketEvents version. */
  public static Collection<String> unresolvedPackets() {
    return new ArrayList<>(PacketEventsIdMapper.unresolvedPackets());
  }
}
