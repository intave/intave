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

package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.PlayerDigAction;

public final class BlockDigReader extends BlockPositionReader {
  public boolean isStab() {
    return action() == PlayerDigAction.STAB;
  }

  public @Nullable PlayerDigAction action() {
    StructureModifier<Object> modifier = actionModifier();
    if (modifier == null) {
      return null;
    }
    Object value = modifier.readSafely(0);
    if (!(value instanceof Enum)) {
      return null;
    }
    String name = ((Enum<?>) value).name();
    if ("SWAP_ITEM_WITH_OFFHAND".equals(name)) {
      return PlayerDigAction.SWAP_HELD_ITEMS;
    }
    try {
      return PlayerDigAction.valueOf(name);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public void setAction(PlayerDigAction action) {
    setAction(packet(), action);
  }

  public static void setAction(PacketContainer packet, PlayerDigAction action) {
    StructureModifier<Object> modifier = actionModifier(packet);
    if (modifier == null) {
      throw new IllegalStateException("Missing player dig action field");
    }
    Class<?> actionType = modifier.getField(0).getType();
    Object value = enumValue(actionType, action);
    modifier.write(0, value);
  }

  private @Nullable StructureModifier<Object> actionModifier() {
    return actionModifier(packet());
  }

  private static @Nullable StructureModifier<Object> actionModifier(PacketContainer packet) {
    StructureModifier<Object> packetModifier = packet.getModifier();
    for (int index = 0; index < packetModifier.size(); index++) {
      Class<?> type = packetModifier.getField(index).getType();
      if (isActionType(type)) {
        return packetModifier.withType(type);
      }
    }
    return null;
  }

  private static boolean isActionType(Class<?> type) {
    if (!type.isEnum()) {
      return false;
    }
    boolean start = false;
    boolean stop = false;
    boolean release = false;
    for (Object value : type.getEnumConstants()) {
      String name = ((Enum<?>) value).name();
      start |= "START_DESTROY_BLOCK".equals(name);
      stop |= "STOP_DESTROY_BLOCK".equals(name);
      release |= "RELEASE_USE_ITEM".equals(name);
    }
    return start && stop && release;
  }

  private static Object enumValue(Class<?> type, PlayerDigAction action) {
    String name = action == PlayerDigAction.SWAP_HELD_ITEMS
      ? "SWAP_ITEM_WITH_OFFHAND"
      : action.name();
    for (Object value : type.getEnumConstants()) {
      String valueName = ((Enum<?>) value).name();
      if (name.equals(valueName)
        || (action == PlayerDigAction.SWAP_HELD_ITEMS && action.name().equals(valueName))) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported player dig action " + action);
  }
}
