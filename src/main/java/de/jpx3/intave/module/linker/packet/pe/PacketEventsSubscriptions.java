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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Binds an annotated subscriber method to the PacketEvents engine.
 * <p>
 * The ProtocolLib path generates a call site with ASM because it runs for every packet of every
 * player and reflection overhead there is measurable. This path deliberately uses plain reflection
 * with a pre-computed argument plan instead: the argument layout is resolved once at registration,
 * so per packet cost is an array fill plus one {@link Method#invoke}, and the class stays
 * verifiable without the bytecode toolchain.
 * <p>
 * Supported parameter types, in any order:
 * {@link PacketEventWrapper}, {@link PacketReceiveEvent}, {@link PacketSendEvent},
 * {@link Player}, {@link User}, {@link PacketTypeCommon}.
 */
public final class PacketEventsSubscriptions {

  private PacketEventsSubscriptions() {
  }

  /**
   * @param instanceLookup resolves the subscriber instance for a player; returning null skips the
   *                       packet, which is what per-player subscribers need before their user
   *                       object exists.
   * @return true when the subscription was registered on at least one packet type.
   */
  public static boolean register(
    Method method,
    Function<Player, Object> instanceLookup,
    PacketId.Client[] packetsIn,
    PacketId.Server[] packetsOut,
    ListenerPriority priority,
    boolean ignoreCancelled,
    String identifier
  ) {
    if (method == null || instanceLookup == null) {
      return false;
    }
    ArgumentPlan plan = ArgumentPlan.of(method);
    if (plan == null) {
      return false;
    }
    method.setAccessible(true);
    return PacketEventsLinkage.linkage().subscribe(
      packetsIn,
      packetsOut,
      priority,
      ignoreCancelled,
      identifier,
      event -> invoke(method, instanceLookup, plan, event)
    );
  }

  private static void invoke(
    Method method,
    Function<Player, Object> instanceLookup,
    ArgumentPlan plan,
    PacketEventWrapper event
  ) {
    Player player = event.player();
    Object instance = instanceLookup.apply(player);
    if (instance == null) {
      return;
    }
    Object[] arguments = plan.build(event, player);
    try {
      method.invoke(instance, arguments);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("subscriber method not accessible: " + method, exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IllegalStateException("subscriber method failed: " + method, cause);
    }
  }

  /** Pre-resolved parameter layout of a subscriber method. */
  static final class ArgumentPlan {
    private static final int NONE = -1;

    private final int length;
    private final int wrapperIndex;
    private final int receiveIndex;
    private final int sendIndex;
    private final int playerIndex;
    private final int userIndex;
    private final int typeIndex;

    private ArgumentPlan(int length, int wrapperIndex, int receiveIndex, int sendIndex,
                         int playerIndex, int userIndex, int typeIndex) {
      this.length = length;
      this.wrapperIndex = wrapperIndex;
      this.receiveIndex = receiveIndex;
      this.sendIndex = sendIndex;
      this.playerIndex = playerIndex;
      this.userIndex = userIndex;
      this.typeIndex = typeIndex;
    }

    /** @return the plan, or null when the method takes a parameter this engine cannot supply. */
    static ArgumentPlan of(Method method) {
      Class<?>[] types = method.getParameterTypes();
      int wrapper = NONE, receive = NONE, send = NONE, player = NONE, user = NONE, type = NONE;
      for (int i = 0; i < types.length; i++) {
        Class<?> parameter = types[i];
        if (parameter == PacketEventWrapper.class) {
          wrapper = i;
        } else if (parameter == PacketReceiveEvent.class) {
          receive = i;
        } else if (parameter == PacketSendEvent.class) {
          send = i;
        } else if (parameter.isAssignableFrom(Player.class)) {
          player = i;
        } else if (parameter == User.class) {
          user = i;
        } else if (parameter.isAssignableFrom(PacketTypeCommon.class)) {
          type = i;
        } else {
          return null;
        }
      }
      return new ArgumentPlan(types.length, wrapper, receive, send, player, user, type);
    }

    Object[] build(PacketEventWrapper event, Player player) {
      Object[] arguments = new Object[length];
      if (wrapperIndex != NONE) {
        arguments[wrapperIndex] = event;
      }
      if (receiveIndex != NONE) {
        arguments[receiveIndex] = event.receiveEvent();
      }
      if (sendIndex != NONE) {
        arguments[sendIndex] = event.sendEvent();
      }
      if (playerIndex != NONE) {
        arguments[playerIndex] = player;
      }
      if (userIndex != NONE) {
        arguments[userIndex] = event.user();
      }
      if (typeIndex != NONE) {
        arguments[typeIndex] = event.packetType();
      }
      return arguments;
    }
  }
}
