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

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenHorseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.entity.Player;

/**
 * {@link WindowOpenView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction because every PacketEvents getter re-reads the packet
 * buffer, so {@link #release()} has nothing to hand back. Two factories mirror the two packet
 * shapes the ProtocolLib reader folds together: the open window packet and the open horse window
 * packet.
 * <h2>How faithfully the menu type reproduces the ProtocolLib reader</h2>
 * {@code WindowOpenReader#menuType()} resolves the type in three ways, and which one it takes is
 * decided by the shape of the NMS packet rather than by the wire:
 * <ul>
 *   <li>the horse packet is answered with {@code minecraft:horse} without touching the packet;</li>
 *   <li>below 1.14 the packet carries the type as a plain string, which is returned verbatim;</li>
 *   <li>from 1.14 the type is a numeric registry id, looked up in a fixed table.</li>
 * </ul>
 * The numeric field survived in NMS up to and including 1.18.2 - verified against the shipped
 * server jars, where {@code PacketPlayOutOpenWindow} holds {@code int b} through 1.18.2 and a
 * {@code Containers<?>} from 1.19 on. Up to 1.18.2 this view therefore reads the same number off
 * the wire that the reader reads off the NMS object and runs it through the same table, so the two
 * backends return the identical string.
 * <p>
 * From 1.19 the reader resolves the registry object into its real key instead, and PacketEvents
 * 2.4.0 ships no menu type registry to resolve the wire id against - the wrapper hands out the raw
 * number and nothing more. Feeding that number into the 1.16 era table would silently mislabel
 * every menu the registry has gained since, so this view returns
 * {@link WindowOpenView#UNKNOWN_MENU_TYPE} there: the reader's own fallback value, which it already
 * returns whenever ProtocolLib cannot expose the registry. The container id, which is what the
 * assumed window state machine runs on, is exact on every version.
 */
public final class PacketEventsWindowOpenView implements WindowOpenView {

  /**
   * The menu registry order as of 1.14, kept identical to {@code WindowOpenReader}'s own table so
   * both backends map a given wire id to the same name. Applies to 1.14 and 1.15.
   */
  private static final String[] MENU_TYPES_1_14 = {
    "minecraft:generic_9x1",
    "minecraft:generic_9x2",
    "minecraft:generic_9x3",
    "minecraft:generic_9x4",
    "minecraft:generic_9x5",
    "minecraft:generic_9x6",
    "minecraft:generic_3x3",
    "minecraft:anvil",
    "minecraft:beacon",
    "minecraft:blast_furnace",
    "minecraft:brewing_stand",
    "minecraft:crafting",
    "minecraft:enchantment",
    "minecraft:furnace",
    "minecraft:grindstone",
    "minecraft:hopper",
    "minecraft:lectern",
    "minecraft:loom",
    "minecraft:merchant",
    "minecraft:shulker_box",
    "minecraft:smoker",
    "minecraft:cartography_table",
    "minecraft:stonecutter"
  };

  /**
   * The same table after 1.16 inserted {@code smithing}. Kept identical to
   * {@code WindowOpenReader}'s own table; used from 1.16 up to the last version whose packet still
   * carries a numeric type, 1.18.2.
   */
  private static final String[] MENU_TYPES_1_16 = {
    "minecraft:generic_9x1",
    "minecraft:generic_9x2",
    "minecraft:generic_9x3",
    "minecraft:generic_9x4",
    "minecraft:generic_9x5",
    "minecraft:generic_9x6",
    "minecraft:generic_3x3",
    "minecraft:anvil",
    "minecraft:beacon",
    "minecraft:blast_furnace",
    "minecraft:brewing_stand",
    "minecraft:crafting",
    "minecraft:enchantment",
    "minecraft:furnace",
    "minecraft:grindstone",
    "minecraft:hopper",
    "minecraft:lectern",
    "minecraft:loom",
    "minecraft:merchant",
    "minecraft:shulker_box",
    "minecraft:smithing",
    "minecraft:smoker",
    "minecraft:cartography_table",
    "minecraft:stonecutter"
  };

  private final PacketSendEvent event;
  private final int containerId;
  private final String menuType;

  private PacketEventsWindowOpenView(PacketSendEvent event, int containerId, String menuType) {
    this.event = event;
    this.containerId = containerId;
    this.menuType = menuType;
  }

  /** @return a view over the event, or null when the packet opens no container. */
  public static PacketEventsWindowOpenView of(PacketSendEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Server.OPEN_WINDOW) {
      WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow(event);
      return new PacketEventsWindowOpenView(
        event, wrapper.getContainerId(), menuTypeOf(wrapper)
      );
    }
    if (type == PacketType.Play.Server.OPEN_HORSE_WINDOW) {
      WrapperPlayServerOpenHorseWindow wrapper = new WrapperPlayServerOpenHorseWindow(event);
      // The reader answers the horse packet from its type alone, without reading a type field.
      return new PacketEventsWindowOpenView(event, wrapper.getWindowId(), "minecraft:horse");
    }
    return null;
  }

  private static String menuTypeOf(WrapperPlayServerOpenWindow wrapper) {
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      String legacyType = wrapper.getLegacyType();
      return legacyType == null || legacyType.isEmpty() ? UNKNOWN_MENU_TYPE : legacyType;
    }
    if (MinecraftVersions.VER1_19.atOrAbove()) {
      // See the class comment: the reader names the type out of the server registry from 1.19 on,
      // and PacketEvents exposes only the raw wire id, which no table here can name safely.
      return UNKNOWN_MENU_TYPE;
    }
    int numericType = wrapper.getType();
    String[] menuTypes = MinecraftVersions.VER1_16_0.atOrAbove()
      ? MENU_TYPES_1_16
      : MENU_TYPES_1_14;
    return numericType >= 0 && numericType < menuTypes.length
      ? menuTypes[numericType]
      : UNKNOWN_MENU_TYPE;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public int containerId() {
    return containerId;
  }

  @Override
  public String menuType() {
    return menuType;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}
