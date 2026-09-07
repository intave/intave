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

package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;

public final class SkinBlinker extends CheckPart<ProtocolScanner> {
  private static final boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();
  private static Class<?> enumMainHandClass;

  public SkinBlinker(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      return;
    }

    PacketContainer packet = event.getPacket();
    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      if (enumMainHandClass == null) {
        enumMainHandClass = Lookup.serverClass("EnumMainHand");
      }
      HandSlot sentHand = packet.getEnumModifier(HandSlot.class, enumMainHandClass).read(0);
      if (!equalHand(player.getMainHand(), sentHand)) {
        return;
      }
    }
    if (shouldCancel(user)) {
      event.setCancelled(true);
    }
  }

  /**
   * PacketEvents entry point. No engine neutral view exists for the client settings packet; the
   * only field this check reads is the main hand, which PacketEvents already normalises into
   * {@link HumanoidArm}, so it is translated to {@link HandSlot} here and the shared body decides.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLIENT_SETTINGS) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player)) {
      return;
    }
    Player player = (Player) rawPlayer;
    User user = userOf(player);

    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      return;
    }

    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      HandSlot sentHand = translateHand(new WrapperPlayClientSettings(event).getMainHand());
      if (!equalHand(player.getMainHand(), sentHand)) {
        return;
      }
    }
    if (shouldCancel(user)) {
      event.setCancelled(true);
    }
  }

  /** Engine independent body: the movement state test that decides whether to drop the packet. */
  private boolean shouldCancel(User user) {
    MovementMetadata movementData = user.meta().movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    double distanceMoved = Hypot.fast(movementData.offsetMotionX(), movementData.offsetMotionZ());
    if (movementData.inWeb || movementData.receivedFlyingPacketIn(2)) {
      return false;
    }
    return (keyForward != 0 || keyStrafe != 0) && distanceMoved > 0.1;
  }

  private static HandSlot translateHand(HumanoidArm arm) {
    if (arm == HumanoidArm.LEFT) {
      return HandSlot.LEFT;
    }
    if (arm == HumanoidArm.RIGHT) {
      return HandSlot.RIGHT;
    }
    return null;
  }

  private boolean equalHand(Object bukkitHand, HandSlot hand) {
    return bukkitHand == MainHand.LEFT && hand == HandSlot.LEFT
      || bukkitHand == MainHand.RIGHT && hand == HandSlot.RIGHT;
  }

  public enum HandSlot {
    LEFT,
    RIGHT
  }
}