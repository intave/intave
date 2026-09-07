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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EntityRelativeMoveView} backed by ProtocolLib.
 * <p>
 * Reads the same three fields the entity tracker read inline before the view existed, from the same
 * structure modifiers and in the same version order: shorts from 1.14, ints from 1.9, bytes below.
 * The field layout differs per version because the server changed the delta field type twice while
 * keeping the entity id in integer slot zero, which is why the 1.9 branch reads integer slots one
 * to three and the others read slot zero to two of their own modifier.
 * <p>
 * The deltas are read lazily and then cached. Lazily, because the tracker drops most of these
 * packets on the entity id alone and the original code read the deltas only after that check, so
 * reading them at construction would move work into the hot path and would surface a decode failure
 * on packets that used to be discarded untouched. Cached, because once the tracker asks for one
 * delta it asks for all three.
 */
public final class ProtocolLibEntityRelativeMoveView implements EntityRelativeMoveView {

  private static final boolean NEW_POSITION_PROCESSING_1_9 = MinecraftVersions.VER1_9_0.atOrAbove();
  private static final boolean NEW_POSITION_PROCESSING_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();

  private final PacketEvent event;

  private boolean deltasRead;
  private long deltaX;
  private long deltaY;
  private long deltaZ;
  private double divisor;

  public ProtocolLibEntityRelativeMoveView(PacketEvent event) {
    this.event = event;
  }

  /** @return the wrapped event, for movement code that still needs ProtocolLib specifics. */
  public PacketEvent event() {
    return event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public @Nullable Integer entityId() {
    return event.getPacket().getIntegers().read(0);
  }

  @Override
  public long deltaX() {
    readDeltas();
    return deltaX;
  }

  @Override
  public long deltaY() {
    readDeltas();
    return deltaY;
  }

  @Override
  public long deltaZ() {
    readDeltas();
    return deltaZ;
  }

  @Override
  public double divisor() {
    readDeltas();
    return divisor;
  }

  private void readDeltas() {
    if (deltasRead) {
      return;
    }
    PacketContainer packet = event.getPacket();
    if (NEW_POSITION_PROCESSING_1_14) {
      StructureModifier<Short> shorts = packet.getShorts();
      deltaX = shorts.readSafely(0);
      deltaY = shorts.readSafely(1);
      deltaZ = shorts.readSafely(2);
      divisor = 4096d;
    } else if (NEW_POSITION_PROCESSING_1_9) {
      StructureModifier<Integer> integers = packet.getIntegers();
      deltaX = integers.readSafely(1);
      deltaY = integers.readSafely(2);
      deltaZ = integers.readSafely(3);
      divisor = 4096d;
    } else {
      StructureModifier<Byte> bytes = packet.getBytes();
      deltaX = bytes.readSafely(0);
      deltaY = bytes.readSafely(1);
      deltaZ = bytes.readSafely(2);
      divisor = 32d;
    }
    deltasRead = true;
  }

  @Override
  public void release() {
    // Nothing held: this view reads structure modifiers straight off the event's packet and never
    // borrows a pooled reader.
  }
}
