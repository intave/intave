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

package de.jpx3.intave.packet.plib.reader;

import de.jpx3.intave.adapter.MinecraftVersions;
import org.jetbrains.annotations.NotNull;

public final class AttachEntityReader extends AbstractPacketReader implements EntityIterable {
  public int entityId() {
    return packet().getIntegers().read(entityIdIndex());
  }

  public int vehicleId() {
    return packet().getIntegers().read(entityIdIndex() + 1);
  }

  public boolean isMount() {
    return MinecraftVersions.VER1_9_0.below() && packet().getIntegers().read(0) == 0;
  }

  private int entityIdIndex() {
    return MinecraftVersions.VER1_9_0.below() ? 1 : 0;
  }

  private int slot = 0;
  private final SubstitutionIterator<Integer> STATIC_ITERATOR = new SubstitutionIterator<Integer>() {
    @Override
    public void set(Integer integer) {
      if (slot == 1) {
        packet().getIntegers().write(entityIdIndex(), integer);
      } else if (slot == 2) {
        packet().getIntegers().write(entityIdIndex() + 1, integer);
      }
    }

    @Override
    public boolean hasNext() {
      return slot < 2;
    }

    @Override
    public Integer next() {
      return packet().getIntegers().read(entityIdIndex() + slot++);
    }
  };

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    slot = 0;
    return STATIC_ITERATOR;
  }
}
