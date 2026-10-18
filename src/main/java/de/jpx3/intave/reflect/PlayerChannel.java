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

package de.jpx3.intave.reflect;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftFields;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class PlayerChannel {
  private static volatile FieldAccessor channelAccessor;

  private PlayerChannel() {
  }

  public static Channel channelOf(Player player) {
    Object networkManager = MinecraftFields.getNetworkManager(Objects.requireNonNull(player, "player"));
    if (networkManager == null) {
      throw new IllegalStateException("Could not find the network manager of " + player.getName());
    }

    FieldAccessor accessor = channelAccessor;
    if (accessor == null || !accessor.getField().getDeclaringClass().isInstance(networkManager)) {
      accessor = Accessors.getFieldAccessor(networkManager.getClass(), Channel.class, true);
      channelAccessor = accessor;
    }

    Object channel = accessor.get(networkManager);
    if (!(channel instanceof Channel)) {
      throw new IllegalStateException("Could not find the Netty channel of " + player.getName());
    }
    return (Channel) channel;
  }
}
